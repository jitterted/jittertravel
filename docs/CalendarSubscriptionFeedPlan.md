# Calendar Subscription Feed — Plan

**Status: `planned` (2026-08-17) — nothing built.** Phase 1 is cancel-deadline reminders;
the feed is designed to grow into a full travel-calendar subscription later.

## Goal

Publish a private **iCalendar (`.ics`) feed** that Ted subscribes to once on his iPhone/iPad
(built-in Calendar app — no App Store install). Each booking with a free-cancellation deadline
becomes a calendar event carrying a `VALARM` set to **24 hours before** the deadline. **iOS
schedules and fires that alarm locally on the device**, so the reminder works even with no
connectivity — WiFi, roaming in Europe, or airplane mode — which is exactly where carrier SMS is
unreliable.

The decisive property: because the phone fires the alarm, **the server never "sends" anything at a
moment in time.** The feed is a pure projection of current bookings. That erases the three hard
parts of the SMS/email design — **no scheduler, no SMS/push provider, and no exactly-once /
replay-safety hazard** (the "email-sender hazard" that reverted live-rebuild does not arise,
because nothing is emitted during event replay).

- **Phase 1 (this plan builds):** cancel-deadline reminders only.
- **Left open (not built now):** a full travel calendar (flights, trains, hotel stays, gatherings,
  conferences) served from the same feed. See "Extension seam" below.

## Why a subscription feed (vs SMS / email / push)

| | Reliable abroad/offline | No app install | No server scheduler | No 3rd-party cost | In-house |
|---|---|---|---|---|---|
| **iCal subscription** | ✅ (device-local alarm) | ✅ (built-in Calendar) | ✅ | ✅ | ✅ |
| SMS (Twilio) | ❌ (carrier roaming) | ✅ | ❌ | ❌ | ❌ |
| Email | ✅ | ✅ | ❌ | ~ | ~ |
| Web Push (PWA) | ✅ | ~ (add-to-home-screen) | ❌ | ✅ | ✅ |

## Security model — READ THIS FIRST

The feed is **unredacted OWNER data** — hotel names, addresses, cancel deadlines (and, later, the
whole travel calendar). It is deliberately **NOT** run through `CalendarEntryRedactor`; it is the
private, full-detail feed. Therefore:

**The token in the URL is the only credential. Treat it exactly like a password.**

1. Token is a configured secret: `jittertravel.calendar-feed.token` ← `CALENDAR_FEED_TOKEN` env var
   (Railway). **Absent ⇒ the feed is disabled and returns 404.** Safe, opt-in default.
2. **Constant-time comparison.** Never log the token. Rotatable (change the env var ⇒ old feed dies,
   re-subscribe).
3. An unknown / missing / disabled token returns **404 with an empty body** — do not confirm the
   endpoint exists, and never emit a single VEVENT without a valid token.
4. `SecurityConfig`: add a `/calendar/feed/**` matcher as `permitAll` (the **token**, not the login
   session, authenticates — the Calendar app cannot do a login form), and add the route to the
   `policy()` matrix in `AuthorizationMatrixTest` in the same change, per the deny-by-default route
   rule in `CLAUDE.md`. The matrix test asserts the route is reachable without a session; a separate
   `CalendarFeedSecurityTest` covers the token logic the matrix can't.
5. The **subscribe URL contains the token**, so any page that displays it is **OWNER-only**.
6. If a *public* travel feed is ever wanted, it is a **separate endpoint** that runs through the
   redactor. Never blend redacted and full data on one URL.

## Phase 1 — cancel-deadline reminders

### Data source (reuse, don't add)

Reuse `BookedHotelsProjector` / `BookedHotelView`. It already carries `hotelName`, `address`,
`bookingIntent`, `mapsUrl`, and `cancelBy` (nullable `ZonedTimestamp`), and already computes
`cancelDeadlinePassed` from an injected `now`. **No new projector.**

The feed emits one event per **live** (non-cancelled) booking whose `cancelBy` is **present and in
the future**. `now` is captured at the controller boundary and passed inward (external-inputs rule).
`ZonedTimestamp.utc()` gives the deadline instant directly.

### iCalendar output

A small **in-house iCal writer** — no new dependency, consistent with the hand-rolled j2html
renderers. Keep it a pure text builder that takes a `List<ICalEvent>` and emits RFC 5545.

`ICalEvent` value type: `uid`, `start` (Instant), `end` (Instant), `summary`, `description`,
optional `alarmTrigger` (e.g. `-PT24H`).

Per cancel-deadline event:
- `UID`: **stable**, derived from the booking id — `{hotelBookingId}-cancelby@jittertravel`. iOS
  reconciles updates and removals across refetches by UID.
- `DTSTART` / `DTEND`: `cancelBy.utc()` in UTC (`…Z`), a short (~15-minute) timed event **at** the
  deadline instant. (UTC keeps it unambiguous; iOS displays it in the device's current zone. `TZID`
  in the hotel's own zone is a possible later refinement.)
- `SUMMARY`: `Free-cancel deadline: {hotelName}`.
- `DESCRIPTION`: city + check-in/out dates for context.
- **Two `VALARM` blocks:** `ACTION:DISPLAY` with `TRIGGER;RELATED=START:-PT24H` **and**
  `TRIGGER;RELATED=START:-PT4H` — the device fires a reminder 24h **and** 4h before the deadline.
  (A VEVENT may carry multiple VALARMs; iOS honors each. This also settles the "booked <24h before
  the deadline" case for free: the `-PT24H` alarm is in the past and iOS silently skips it, while
  the `-PT4H` alarm still fires. Booked <4h before the deadline ⇒ neither fires; the event still
  shows on the calendar — an accepted corner.)
- `DTSTAMP` / `LAST-MODIFIED` / `SEQUENCE` for update reconciliation.

RFC 5545 details the writer must get right: **CRLF** line endings, **75-octet line folding**,
escaping of `,` `;` `\` and newlines, plus the `VCALENDAR` wrapper (`VERSION:2.0`, `PRODID`,
`CALSCALE:GREGORIAN`).

### Endpoint

`GET /calendar/feed/{token}.ics` → `Content-Type: text/calendar; charset=utf-8`. A plain controller
that writes the iCal string (read-only projection output — the iCal analogue of the j2html views).
Flow: validate token (constant-time) → 404 on bad/missing/disabled → capture `now` → assemble
`List<ICalEvent>` → write. `Cache-Control` can be short or omitted for v1 (iOS controls its own
refresh cadence regardless).

### Surfacing the subscribe URL (OWNER-only, on `admin-home`)

An OWNER-only **card on `admin-home`** shows two links (both carry the token, so the card is
OWNER-only — it is already under the `/admin/**` matcher):
- **Subscribe:** `webcal://{host}/calendar/feed/{token}.ics` (the `webcal://` scheme opens the iOS
  subscribe sheet) + a copy button.
- **Test the alarm now:** `https://{host}/calendar/feed/{token}/probe.ics` with the one-line
  instruction (open on the phone, tap "Add All", an alert pops in ~5 min — see the testing section).

Covered by a `@WebMvcTest` (Thymeleaf render) and an `AuthorizationMatrixTest` assertion that the
token never reaches a non-owner. `admin-home` is already linked from the nav.

## Extension seam — full travel calendar (later, NOT now)

The assembler produces a `List<ICalEvent>`. Phase 1 wires exactly one contributor (cancel
deadlines). Later phases add contributors for flights, trains, hotel stays, gatherings, and
conferences, building VEVENTs from the existing `CalendarEntry` / itinerary read models with the
same UTC-instant approach (all-day or timed as appropriate).

Per "no abstraction before the second user," v1 does **not** introduce an `ICalEventSource`
interface — the seam today is simply "the assembler returns a `List<ICalEvent>`." Introduce the
collection/interface when the second contributor actually arrives. Feed shape (one feed with
everything vs scoped feeds like `…/deadlines.ics` vs `…/all.ics`) is deferred to that work.

## Config & deployment

- `jittertravel.calendar-feed.token` ← `CALENDAR_FEED_TOKEN` (Railway secret). Absent ⇒ 404.
- Generate a long random token (≥128 bits of entropy). Rotation = change the env var, then
  re-subscribe on the devices.
- Subscribe link base URL: derive from the request host, or a configured `jittertravel.base-url`.

## Testing

- **`ICalWriter` unit tests:** VCALENDAR/VEVENT structure, CRLF, 75-octet folding, escaping, UTC
  `DTSTART`, `VALARM` trigger, stable UID. One **inline golden sample** (<30 lines).
- **Assembler tests:** future `cancelBy` ⇒ one VEVENT carrying **both** the `-PT24H` and `-PT4H`
  alarms; no `cancelBy` ⇒ none; cancelled booking ⇒ none; past `cancelBy` ⇒ excluded (all via
  injected `now`).
- **Probe-feed test:** the probe endpoint emits exactly one VEVENT a few minutes out with a
  near-future alarm, and is token-gated the same way as the real feed.
- **Controller `@WebMvcTest`:** correct token ⇒ 200 `text/calendar` with the VEVENT; wrong / missing
  / disabled token ⇒ 404 empty body; content-type asserted.
- **`CalendarFeedSecurityTest` (real security chain):** wrong/missing token reveals nothing (full
  hotel name **absent**, 404); correct token returns the private data (full hotel name **present**)
  — proving the feed is intentionally unredacted but token-gated. Plus the `SecurityConfig` matcher
  and `AuthorizationMatrixTest` row for `/calendar/feed/**`.
- **Subscribe-URL page:** `@WebMvcTest` + auth-matrix row (OWNER-only; the token never reaches a
  non-owner).
- Every new/changed test **mutation-verified**.

## Testing it on your device — no hotel booking, no waiting

Three layers, cheapest first:

1. **Correctness, no device.** `curl` the feed URL (or open the `.ics` on a Mac) and eyeball the
   `VCALENDAR` / `VEVENT` / two `VALARM`s. The automated tests above already assert this shape, so
   most iteration never touches a phone.
2. **On-device alarm firing, in ~5 minutes — the answer to "without booking a hotel".** The feed
   ships a **probe endpoint**: `GET /calendar/feed/{token}/probe.ics` returns a single synthetic
   VEVENT ~10 minutes out, carrying a near-future alarm (`TRIGGER:-PT5M`, so it fires ~5 min after
   the device fetches it). It runs through the **exact same iCal writer and VALARM path** as a real
   cancel-deadline event, so a green probe proves the whole mechanism end to end — server generates
   → device parses → **local alarm actually pops** — with no hotel and no multi-hour wait.
   - **How you use it:** on the phone, open the probe link over **`https://`** (not `webcal://`) —
     Safari/Mail offers **"Add All"**, which imports it as a **one-off** into your calendar (not a
     live subscription), so it never pollutes your real subscribed feed. Wait ~5 minutes; the alert
     pops. Delete the test event afterward. The probe event self-describes ("JitterTravel test
     reminder — safe to delete").
   - Token-gated exactly like the real feed (same `/calendar/feed/**` matcher; wrong/missing token ⇒
     404). It exposes no booking data — it is a fixed synthetic event — so it is safe to hand out
     for a quick check, though the token is still required.
3. **Realistic dry run (optional).** Book a throwaway hotel with a `cancelBy` a few hours out and
   confirm the deadline event appears in the subscribed feed. Only needed to sanity-check the real
   data path; the probe already covers the alarm-firing path.

The admin-home card (below) shows both the **Subscribe** link (`webcal://…/feed/{token}.ics`) and a
**"Test the alarm now"** link (`https://…/feed/{token}/probe.ics`) with that one-line instruction.

## Decisions (confirmed 2026-08-17)

1. **Lead time:** **two fixed alarms, 24h and 4h** before the deadline (two `VALARM`s per VEVENT).
2. **Event shape:** a short **timed event at the deadline instant** (UTC `DTSTART`).
3. **Booked with the 24h alarm already past:** no special logic — the **4h alarm covers it** (the
   past 24h alarm is skipped by iOS). Booked <4h out ⇒ event shows, no alarm fires (accepted corner).
4. **Subscribe link surfaced on `admin-home`** (an OWNER-only card), alongside the "test the alarm"
   probe link below.
5. **Token:** a single shared secret via env var for v1 (`CALENDAR_FEED_TOKEN`); a stored,
   rotatable-in-UI token is a later refinement.

## Edge cases

- **`HotelChanged` moves `cancelBy`** ⇒ next fetch reflects the new instant; the stable UID lets iOS
  update the event and its alarm in place.
- **`HotelBookingCancelled`** ⇒ the booking leaves `BookedHotelView` ⇒ the VEVENT disappears next
  fetch ⇒ iOS removes it by UID.
- **iOS refresh cadence is device-controlled and can lag** — fine, because the event is published
  far ahead of time and the **local** alarm fires precisely regardless. This design is unsuitable
  for near-term reminders (shorter than the refresh interval); it is ideal for a 24h-ahead deadline.
- Multiple bookings with deadlines ⇒ multiple VEVENTs.

## Non-goals (Phase 1)

No server scheduler, no SMS/email/push provider, no public or redacted feed, and no full travel
calendar — only the private, token-gated cancel-deadline `.ics` feed (plus its probe endpoint for
on-device testing), with the assembler seam left open for more event types.
