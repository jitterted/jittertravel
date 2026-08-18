# Calendar Subscription Feed — Plan

**Status: `DONE (2026-08-18)` — built, tested, and validated on-device in prod.** Phase 1
(cancel-deadline reminders) is fully implemented, covered by both test tiers, deployed to prod
(Railway env vars `CALENDAR_FEED_TOKEN` + `JITTERTRAVEL_BASE_URL` set), and the money-critical
Critical Validation Gate **passed on Ted's iPhone**: an alarm fired from the real `webcal://`
subscription. Ted is subscribed to the live feed; the weekly heartbeat is now the standing liveness
signal.

**Key operational finding (2026-08-18): iOS defaults "Remove Alerts" ON for a subscribed calendar,
and buries the toggle** — it did **not** appear on the subscribe sheet. The event shows up and
everything looks right, but alarms are silently suppressed. The fix is in the **subscribed
calendar's *details*** (Calendar app → Calendars → ⓘ next to the subscription → turn "Remove Alerts"
OFF), **not** at subscribe time. This defaults ON on every subscription (test *and* real feed), so
it must be turned off per-subscription. This is the exact silent-failure mode that costs money;
the admin card's gate instructions were rewritten to point at the details screen.

The feed is designed to grow into a full travel-calendar subscription later (see "Extension seam").

Shipped: `ICalWriter` (RFC 5545 — CRLF, 75-octet folding, escaping) + `ICalEvent`;
`CalendarFeedAssembler` (three deadline VALARMs `-PT48H/-PT24H/-PT4H`, weekly liveness heartbeat,
dual-mode probe event); token-gated `CalendarFeedController` at `GET /calendar/feed/{token}.ics`
and `…/{token}/probe.ics` (constant-time compare, 404 when disabled/wrong); the OWNER-only
`admin-calendar-feed` card (subscribe + both probe links, "Remove Alarms OFF" gate instruction) and
its `admin-home` nav card; `SecurityConfig` `/calendar/feed/**` permitAll + `AuthorizationMatrixTest`
rows + `CalendarFeedSecurityTest`; config `CALENDAR_FEED_TOKEN` / `JITTERTRAVEL_BASE_URL`. Full suite
green (927 unit + 36 js).

## Rollout — DONE (2026-08-18)

All three steps completed; kept here as the record of what "turning it on" took.

1. ✅ **`CALENDAR_FEED_TOKEN` set on Railway** — a long random secret (`openssl rand -hex 24`). Until
   it is set every feed URL 404s — the safe default, not a bug (see "How the token works" below).
   Rotate it there to revoke a leaked URL.
2. ✅ **`JITTERTRAVEL_BASE_URL` set on Railway** — the public origin (`https://jittertravel.com`,
   scheme + host; a trailing slash is tolerated). Without it the admin card derives the host from the
   request, which is unreliable behind Railway's proxy and can print a `webcal://` link that silently
   won't subscribe.
3. ✅ **Critical Validation Gate passed against prod** — subscribed via the real-feed `webcal://`
   link, turned **"Remove Alerts" OFF in the subscribed calendar's details** (it defaulted ON and the
   toggle was *not* on the subscribe sheet — see the gate section), pulled to refresh, and the alert
   fired *from the subscription*. Ted is now on the live feed with the weekly heartbeat as the
   standing liveness signal.

## How the token works (read this when you come back to it)

**The token is the entire security model.** The feed is deliberately *unredacted* OWNER data — full
hotel names, addresses, cancel deadlines. There is no login on it (the iOS Calendar app can't submit
a login form), so `SecurityConfig` lets `/calendar/feed/**` through as `permitAll` and the token in
the **URL path** is the *only* thing standing between an anonymous request and Ted's travel details.
Treat the token exactly like a password.

- **Where it lives:** one configured secret, `jittertravel.calendar-feed.token`, fed from the
  `CALENDAR_FEED_TOKEN` env var (`application.properties`:
  `jittertravel.calendar-feed.token=${CALENDAR_FEED_TOKEN:}`). It is **not** stored in the database
  and **not** per-user — a single shared secret for v1.
- **How a request is checked:** `CalendarFeedController` compares the `{token}` path segment against
  the configured secret with a **constant-time** compare (`MessageDigest.isEqual`, so a wrong token
  can't be guessed by timing). Match ⇒ 200 with the iCal; **no match, missing, or no token
  configured ⇒ 404 with an empty body** — it never even confirms the endpoint exists, and never
  emits a single VEVENT without a valid token.
- **Disabled by default:** if `CALENDAR_FEED_TOKEN` is unset/blank the feed is *off* and everything
  404s. This is why prod shows nothing until you set it (step 1 above), and why the admin card shows
  a "disabled" state with no links when there's no token.
- **The URL contains the token,** so any page that shows a feed URL is OWNER-only — the
  `admin-calendar-feed` card lives under `/admin/**`. Don't paste a real feed URL anywhere public.
- **Rotation = the recovery path.** To revoke access (e.g. a URL leaked into a proxy access log,
  browser history, or a `Referer`), **change `CALENDAR_FEED_TOKEN` on Railway and redeploy**: the
  old URL instantly 404s and you re-subscribe on the device with the new one. Accepted risk: because
  the token is in the path, it *is* logged by upstream infrastructure we don't control — rotation is
  how we live with that.

> ⚠️ **This is money-critical.** A missed free-cancellation deadline costs real money, so the
> reminder had to be *proven* to fire, not merely *believed* to. The whole design rested on one
> assumption — **that iOS fires `VALARM`s from a read-only *subscribed* calendar** — which the probe
> test could not exercise. **That assumption is now confirmed on-device (2026-08-18); see the gate
> section.** The standing risk is no longer "does it fire" but "does it *keep* firing": if "Remove
> Alerts" gets re-enabled, iOS refresh is disabled, or the token is rotated without re-subscribing,
> the feed goes silent — which is what the weekly heartbeat is there to catch.

## Critical validation gate — prove it on the SUBSCRIPTION before relying on it

> ✅ **GATE PASSED on-device 2026-08-18.** An alarm fired from the real `webcal://` subscription on
> Ted's iPhone. The one gotcha, recorded for posterity: **"Remove Alerts" defaults ON and iOS did
> not show the toggle at subscribe time** — the alarm only fired after opening the *subscribed
> calendar's details* and turning "Remove Alerts" OFF (then pull-to-refresh). Applies to every
> subscription, real feed included. The section below is kept as the original reasoning.

The single point of failure is: **does iOS actually fire a local `VALARM` from a calendar Ted
*subscribed* to (read-only `.ics` over `webcal://`/`https://`)?** This is different from firing an
alarm on an event Ted *owns*. iOS treats the two differently:

- **Owned events** (created locally, or imported one-off via "Add All") reliably fire their alarms.
- **Subscribed calendars** are read-only and, at subscribe time, iOS offers a **"Remove Alarms"**
  toggle; alerts from subscriptions can be suppressed by that toggle and, historically, iOS/macOS
  have been inconsistent about honouring subscription `VALARM`s at all.

**The probe's "Add All" path imports events Ted owns — so a green probe proves the writer and that
iOS *can* fire the alarm, but it does NOT prove the subscription path that Ted will actually use.**
Treating a green probe as proof of the whole mechanism (as an earlier draft of this plan did) is the
exact trap that could cost money.

**Gate — all must pass on Ted's real iPhone before the feed is trusted:**

1. Subscribe to the **real feed** via `webcal://…/feed/{token}.ics` (not "Add All"). During the
   subscribe sheet, confirm **"Remove Alarms" is OFF / alerts are kept**; the admin-home card must
   spell this out as a subscribe-time instruction.
2. Put a booking (or the built-in **liveness heartbeat event**, below) into the subscribed feed with
   an alarm a few minutes out, force a calendar refresh, and **confirm the alert actually pops from
   the subscription** — not from a one-off import.
3. Only after step 2 fires does the feed count as validated.

If step 2 does **not** fire, the subscription approach is invalid for money-critical use and we fall
back (see "If the subscription path fails" below) — **do not ship a feed that looks right in `curl`
but stays silent on the phone.**

### Liveness heartbeat (built into the real feed) — ongoing proof, not one-time

Because a subscription that works today can silently stop working (iOS refresh disabled, "Remove
Alarms" flipped, calendar toggled off, token rotated and not re-subscribed), the **real feed carries
a recurring synthetic "heartbeat" VEVENT** — e.g. one every 7 days with a normal `VALARM` — that
Ted expects to see fire on a known cadence. A heartbeat that goes quiet is an early warning that the
pipeline is dead *before* a real deadline is missed. This turns "did I set this up right months ago?"
into a standing, self-checking signal. (Distinct from the probe: the probe is a one-off manual test;
the heartbeat lives in the subscribed feed permanently. Make it clearly self-describing and
harmless.) Confirm this is wanted before building — it is the cheapest insurance against silent
failure.

### Redundant lead times

Because a single missed device-refresh window could swallow a single alarm, do not rely on one lead
time. Phase 1 ships **at least 24h and 4h** (per the decisions below); consider adding **48h** as
well so that even a multi-day refresh lag still leaves a warning. Each is a separate `VALARM`; iOS
honours all, and past ones are silently skipped.

### If the subscription path fails (contingency)

If the gate proves iOS won't fire subscription alarms reliably, the local-alarm design is unsuitable
for money-critical reminders on its own. Options, in order of least new machinery:
- Import the deadline events as **owned** events (share/AirDrop the `.ics`, or "Add All") on a
  cadence — loses the "subscribe once" convenience but keeps alarms reliable.
- Reintroduce a server-side reminder channel (email/push) despite its scheduler cost — the thing this
  plan set out to avoid. Flag to Ted and decide together; **do not silently downgrade to a
  best-effort feed** for a money-critical reminder.

## Goal

Publish a private **iCalendar (`.ics`) feed** that Ted subscribes to once on his iPhone/iPad
(built-in Calendar app — no App Store install). Each booking with a free-cancellation deadline
becomes a calendar event carrying a `VALARM` set to **24 hours before** the deadline. The intent is
that **iOS schedules and fires that alarm locally on the device**, so the reminder works even with no
connectivity — WiFi, roaming in Europe, or airplane mode — which is exactly where carrier SMS is
unreliable. **This "device fires the alarm" property is a design goal, not a proven fact for
*subscribed* calendars — it must pass the Critical Validation Gate above before being relied on.**

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
   - **Caveat — the token is in the URL path**, so it lands in places application code doesn't
     control: Railway/reverse-proxy **access logs**, browser history, and any `Referer`. "Never log
     the token" only binds *our* code; the request line is logged by default upstream. For a
     single-user, rotatable, personal feed this is an **accepted risk** — but it must be a *stated*
     accepted risk, not an implied guarantee. Mitigations if wanted: redact the path in access logs,
     or move the token to a header/query param and scrub it. At minimum, treat token rotation as the
     recovery path if a log is ever exposed.
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
- `DTSTAMP` / `LAST-MODIFIED` for the writer's generation time. **`SEQUENCE` is *not* required here**
  and an earlier draft over-specified it: a subscribed calendar refetches the whole document and
  *replaces* its copy each cycle (unlike an iTIP invite, which needs a monotonic `SEQUENCE` to
  supersede a prior send). A pure stateless projection has no natural source for a monotonic
  `SEQUENCE` anyway. Emit a constant `SEQUENCE:0` (or omit it); rely on the stable `UID` +
  full-document replace for reconciliation.

RFC 5545 details the writer must get right: **CRLF** line endings, **75-octet line folding**,
escaping of `,` `;` `\` and newlines, plus the `VCALENDAR` wrapper (`VERSION:2.0`, `PRODID`,
`CALSCALE:GREGORIAN`). Also emit **`X-WR-CALNAME`** (e.g. `JitterTravel deadlines`) so iOS shows a
sensible calendar name instead of the raw URL.

**Empty feed is valid and expected.** Token valid but zero live bookings with a future `cancelBy` ⇒
a well-formed `VCALENDAR` with **no `VEVENT`s** (plus the heartbeat, if built). This is a normal 200,
not an error — assert it in the tests so it isn't mistaken for a failure.

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
- Subscribe link base URL: prefer a configured **`jittertravel.base-url`**. Deriving from the request
  host is unreliable behind Railway's proxy — `request.getServerName()` sees the internal host unless
  `X-Forwarded-Host` is honoured (`ForwardedHeaderFilter` / `server.forward-headers-strategy`). A
  wrong base URL produces a `webcal://` link that silently won't subscribe, so pin it via config.

## Testing

- **`ICalWriter` unit tests:** VCALENDAR/VEVENT structure, CRLF, 75-octet folding, escaping, UTC
  `DTSTART`, `VALARM` trigger, stable UID. One **inline golden sample** (<30 lines).
- **Assembler tests:** future `cancelBy` ⇒ one VEVENT carrying the configured alarms (`-PT24H` and
  `-PT4H`, plus `-PT48H` if adopted); no `cancelBy` ⇒ none; cancelled booking ⇒ none; past `cancelBy`
  ⇒ excluded; **no live bookings ⇒ well-formed empty `VCALENDAR`, 200** (all via injected `now`).
- **Heartbeat test (if built):** the real feed always contains the recurring heartbeat VEVENT with
  its alarm, independent of bookings.
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

**What the automated suite can and cannot prove.** The tests above prove the *server* emits correct
iCal. **No automated test in this project can prove iOS fires a subscription alarm** — that is a
device behaviour, not our code. The money-critical link (subscription → local alarm actually pops) is
covered *only* by the manual Critical Validation Gate. Keep that distinction explicit so a green CI
run is never mistaken for "the reminder works."

## Testing it on your device — no hotel booking, no waiting

Three layers, cheapest first:

1. **Correctness, no device.** `curl` the feed URL (or open the `.ics` on a Mac) and eyeball the
   `VCALENDAR` / `VEVENT` / two `VALARM`s. The automated tests above already assert this shape, so
   most iteration never touches a phone.
2. **On-device alarm firing (OWNED-event path), in ~5 minutes.** The feed ships a **probe endpoint**:
   `GET /calendar/feed/{token}/probe.ics` returns a single synthetic VEVENT ~10 minutes out, carrying
   a near-future alarm (`TRIGGER:-PT5M`, so it fires ~5 min after the device fetches it). It runs
   through the **exact same iCal writer and VALARM path** as a real cancel-deadline event.
   - ⚠️ **What this does and does NOT prove.** Opening the probe over **`https://`** and tapping
     **"Add All"** imports it as a **one-off event you now OWN** — so a green probe proves the writer
     is correct and that iOS *can* fire the alarm, but it does **NOT** prove the *subscription* path
     (see the Critical Validation Gate). Owned-event alarms are reliable; subscription alarms are the
     open question. **A green probe is necessary but not sufficient** to trust the feed.
   - **How you use it:** on the phone, open the probe link over `https://`, tap "Add All" (a one-off,
     so it never pollutes the real subscribed feed), wait ~5 minutes, confirm the alert pops, then
     delete the test event. The probe event self-describes ("JitterTravel test reminder — safe to
     delete").
   - Token-gated exactly like the real feed (same `/calendar/feed/**` matcher; wrong/missing token ⇒
     404). It exposes no booking data — a fixed synthetic event — so it is safe to hand out for a
     quick check, though the token is still required.
3. **Subscription alarm firing (the money path — REQUIRED, not optional).** This is the Critical
   Validation Gate: **subscribe** to the real feed via `webcal://` (keeping "Remove Alarms" OFF), put
   a near-future alarm into the *subscribed* feed (a throwaway booking with a `cancelBy` a few hours
   out, or the liveness heartbeat), refresh, and confirm the alert fires **from the subscription**.
   Only this step proves what Ted will actually rely on. Do not skip it before trusting the feed with
   a real deadline.

The admin-home card (below) shows both the **Subscribe** link (`webcal://…/feed/{token}.ics`) and a
**"Test the alarm now"** link (`https://…/feed/{token}/probe.ics`) with that one-line instruction.

## Decisions (confirmed 2026-08-17)

1. **Lead time:** **two fixed alarms, 24h and 4h** before the deadline (two `VALARM`s per VEVENT).
   *Open (money-driven):* consider adding **48h** so a multi-day refresh lag still leaves a warning —
   decide with Ted.
2. **Event shape:** a short **timed event at the deadline instant** (UTC `DTSTART`).
3. **Booked with the 24h alarm already past:** no special logic — the **4h alarm covers it** (the
   past 24h alarm is skipped by iOS). Booked <4h out ⇒ event shows, no alarm fires (accepted corner).
4. **Subscribe link surfaced on `admin-home`** (an OWNER-only card), alongside the "test the alarm"
   probe link below.
5. **Token:** a single shared secret via env var for v1 (`CALENDAR_FEED_TOKEN`); a stored,
   rotatable-in-UI token is a later refinement.

**Confirmed 2026-08-17 (round 2):**
6. **Liveness heartbeat: BUILD IT.** Recurring self-test VEVENT in the real feed (see the gate
   section). Cheap insurance against silent failure.
7. **48h alarm: BUILD IT.** Third `VALARM` at `-PT48H` alongside `-PT24H` and `-PT4H`, so even a
   multi-day refresh lag still leaves a warning.
8. **No-hotel manual test: dual-mode probe.** The **one** probe endpoint is consumed **two ways**,
   so Ted can test on-demand with no hotel booking:
   - **`https://…/probe.ics` + "Add All"** — imports as an **owned** one-off event; tests the writer
     and that iOS *can* fire an alarm (~5 min). Owned-event path.
   - **`webcal://…/probe.ics` (subscribe)** — tests the **real subscription path** (the money path).
     The probe recomputes `DTSTART = now + ~10 min` on every fetch with a **stable UID** and a
     `TRIGGER:-PT5M` alarm, so each **pull-to-refresh** in the Calendar app reschedules the alarm
     ~5 min out and it fires **from a subscription**. Ted unsubscribes when done. No persisted state,
     no hotel. **This replaces the "book a throwaway hotel" dry-run** — that step is removed.

   The admin-home card therefore shows **three** links: *Subscribe (real feed)*, *Test — Add All*
   (`https` probe), and *Test — Subscribe* (`webcal` probe), each with one-line instructions.

**Still gating release:** the manual Critical Validation Gate must pass on Ted's device (now testable
via the `webcal` probe above, no hotel) — **the feed is not "done" until it has.**

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
