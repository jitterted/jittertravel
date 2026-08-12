# Schengen Day Counter Plan — 90/180 tracking on the calendar and in the planning forms

> **Status: OPEN (planned 2026-08-12). Nothing built.** Design agreed with Ted; implementation
> not started. See `docs/Backlog.md` for the current status of everything else.

## Problem

The Schengen area allows a non-resident 90 days of presence in any rolling 180-day window. Ted
travels there often enough to get close to the limit, and today the app cannot answer either
question that matters:

1. **Where am I right now?** Nothing in the app models "which country was I in on a given day".
   `ScheduleGapProjector` derives something adjacent (nights in a city, for hotel gaps) but never
   exposes it, and works in cities rather than countries.
2. **Would this booking push me over?** Conferences are usually booked *first*, before any flight
   exists. So the check has to work from a conference's own dates and venue country alone, and it
   has to fire **before submit** — a warning after the fact is a warning about something already
   committed.

There is also a plain memory problem: Schengen membership is not obvious (Croatia and Bulgaria
are in; Ireland and Cyprus are not), so the form should determine it rather than expecting Ted to.

## What the app already knows

| Source | Country available? | Where |
|---|---|---|
| Conference | yes | `ConferenceTentativelyPlanned.venueAddress().country()` |
| Gathering | yes | `GatheringPlanned.location().country()` |
| Hotel | yes | `HotelBooked.address().country()` |
| Train leg | yes | `TrainStationAddress.country()` |
| **Flight leg** | **no** | `StaticAirportCityResolver` maps airport → **city** only |

The flight gap is the only missing datum, and it is deliberately sequenced last (step 7) — see
"Presence is a union" below for why it is not a blocker.

## Design

### Presence is a union of dated country intervals, not a chain of legs

The first sketch made travel legs the spine: arrival in country X holds until the next departure.
That is wrong for this feature, because a conference booked before any flight would contribute
nothing at all — exactly the case the warning exists for.

Instead, **every source contributes dates independently** and the results are unioned:

| Source | Dates contributed |
|---|---|
| Conference | start local date … end local date, inclusive |
| Gathering | start local date … end local date, inclusive |
| Hotel | check-in date … check-out date, inclusive (you are there that morning) |
| Train leg | arrival date … next departure date |
| Flight leg (step 7) | arrival date … next departure date |

Unioning a `Set<LocalDate>` makes three things fall out for free:

- overlapping sources collapse (a conference plus its hotel plus its flights is one set of days);
- the day of entry and the day of exit each count as a full day, which is the rule;
- **a conference with no flights still counts**, which is the requirement.

Local dates throughout, per decision 7 elsewhere in this codebase: the day you are in a country is
the local day at that location.

### No padding — accurate information only

**Decision (Ted, 2026-08-12): do not pad.** An earlier proposal was to inflate a conference's
range by ±1 day in the preview, on the theory that you arrive early and leave late, and that
under-counting is the dangerous direction for a warning. Rejected: arrival and departure padding
are never symmetric, and a number that is quietly wrong is worse than a number that is
conservative-but-honest.

**Consequence to expect, not to fix:** the count *rises* as flights and hotels are added around a
conference. That is correct — the conference contributes its own days, the travel contributes the
travel days, and each is counted when it is actually known.

### `application/SchengenArea`

Plain Java, no Spring. A normalized country-name set, matching the `trim().toLowerCase(Locale.ROOT)`
style `LocationZoneResolver` already uses, since stored country strings are free text.

Members (29): Austria, Belgium, Bulgaria, Croatia, Czechia, Denmark, Estonia, Finland, France,
Germany, Greece, Hungary, Iceland, Italy, Latvia, Liechtenstein, Lithuania, Luxembourg, Malta,
Netherlands, Norway, Poland, Portugal, Romania, Slovakia, Slovenia, Spain, Sweden, Switzerland.

Explicitly **not** members: United Kingdom, Ireland, Cyprus. Alias the same spellings the rest of
the codebase already accepts (`czech republic`/`czechia`, `united kingdom`/`uk`, and so on).

### `application/SchengenDayCounter`

Plain Java. No Spring, no event knowledge, no clock — `today` comes in from the boundary, per the
external-inputs rule.

```java
public class SchengenDayCounter {
    public SchengenUsage usage(Set<LocalDate> schengenDates, LocalDate today);
}

public record SchengenUsage(int daysUsed, int daysRemaining, LocalDate windowStart,
                            int peakDays, LocalDate peakDate) {}
```

- **Window** for a reference date `D` is `[D-179, D]` — 180 days inclusive.
- **`daysUsed`** — how many dates in the set fall in today's window.
- **`peakDays` / `peakDate`** — the maximum of that same count over every `D` from `today` through
  the last date in the set, and the date where it occurs. One sliding-window pass, O(n).

`peakDays` is the number that protects against an overstay: `daysUsed` alone cannot see a trip
already on the calendar three months out.

**There is no separate "what-if" mode.** The conference preview unions the hypothetical dates into
the set and calls the same method. One code path means the number on the form and the number on the
calendar cannot disagree.

### `application/SchengenPresenceProjector`

An `EventStreamConsumer` producing `Set<LocalDate> schengenDates()` — the union described above,
filtered through `SchengenArea`.

Handles the same lifecycle events the other projectors do, so a cancelled or changed item moves the
number: `ConferenceTentativelyPlanned` / `ConferenceCancelled`, `GatheringPlanned` /
`GatheringChanged`, `HotelBooked` / `HotelChanged` / `HotelBookingCancelled`, `TrainBooked` /
`TrainChanged`, and (step 7) `FlightBooked` / `FlightChanged`.

## The pre-submit warning

### Endpoint

Follows the `AddressParseController` precedent exactly: a `@RestController` GET under `/api/`,
deliberately a GET so it stays outside Spring Security's CSRF scope and the form need not thread a
token through `fetch` (`AddressParseController.java:19-22`).

```
GET /api/schengen-preview?country=Netherlands&start=2026-11-02T09:00&end=2026-11-05T17:00

→ { "inSchengen": true, "daysUsed": 87, "remaining": 3, "peakDate": "2026-11-14" }
→ { "inSchengen": false }
```

`remaining` is `90 − peakDays`, computed **including the range in the query** — not
`90 − daysUsed`. Today's count does not know about the trip about to be committed, which is the
entire point of the check.

`inSchengen: false` covers both "the country is not a member" and "the country field is blank",
so the client needs no special-casing for a half-filled form.

**Security:** add `/api/schengen-preview` to the OWNER block at `SecurityConfig.java:43-50` **and**
a row to the `policy()` matrix in `AuthorizationMatrixTest`, in the same change. The endpoint
discloses presence and forward travel planning; it is owner-only. `/api/**` already receives a real
403 rather than a 302 redirect (`SecurityConfig.java:79-86`), so the `fetch` will see auth failures
honestly.

### Form behaviour

Both triggers call the same endpoint with all three params; the response decides visibility and
content. Country blur is simply the moment `inSchengen` typically flips.

- **`country` field blur** → fires the call. This answers "is Croatia Schengen?" without Ted having
  to know.
- **`endDate` field blur** → fires the same call with the new range and repaints the number.

**Reserved space.** The panel is always present in the DOM with a fixed `min-height`, empty until
populated, so revealing it never shifts the fields under the cursor. It sits immediately above the
submit button: it reads inputs both above it (the dates) and below it (the country), so it must not
sit above a field it depends on.

**States:**

| Condition | Panel |
|---|---|
| `inSchengen: false`, or country blank | empty (space still held) |
| used < 85 | quiet: `Schengen: 24 days remaining (peak 14 Nov)` |
| used ≥ 85 | **warning** styling, same line |
| used > 90 | warning styling, plus `— 6 days over` |

**Non-blocking.** The submit button is never disabled. These are *tentative* conferences, and Ted
may know something the app does not; a warning is the right amount of pressure.

### Scope of the form work

First pass: `plan-conference.html` (field `venueCountry`) and `plan-gathering.html` (field
`country`). Both are things booked before flights exist. `book-hotel.html` also uses `country` and
picks the panel up nearly free.

The `change-*` forms need an `&excluding=<id>` parameter, so that editing an event does not count
its old dates *and* its new dates. Out of scope for the first pass, but the endpoint signature
should leave room for it.

## The calendar strip

A strip above the grid in `ConfirmedCalendarRenderer`, showing both numbers:

> **Schengen: 34 / 90 days used** · window from 2026-02-14 · peak **71** on 2026-11-03

**OWNER-only.** `/calendar` is the one page anonymous visitors can see. The strip reveals
country-level presence *and* forward planning, so it is gated on `isOwner`, which
`CalendarController` already derives (`CalendarController.java:42`). Pass the usage inward as a
nullable value and render nothing when absent — renderers must never re-derive viewer identity
(CLAUDE.md redaction rule 4).

## Build order

1. `SchengenArea`.
2. `SchengenDayCounter` + tests. Pure and dependency-free, so the boundary semantics get nailed
   before anything depends on them.
3. `SchengenPresenceProjector` — conferences, gatherings, hotels, trains.
4. `/api/schengen-preview` + `SecurityConfig` matcher + `AuthorizationMatrixTest` row.
5. Form panel and JS on both templates.
6. Calendar strip, owner-only.
7. `StaticAirportCountryResolver`, folding flight legs into the projector.

## Testing

Per the project's usual tiers:

- **`SchengenDayCounterTest`** — day 180 exactly ageing out of the window; a single-day trip;
  back-to-back trips; a peak that falls in the future rather than today; the 84/85 warning
  threshold boundary.
- **`SchengenPresenceProjectorTest`** — lifecycle-propagation scenarios, per the preferred guard
  against a projector silently missing an event: `ConferenceCancelled`, `HotelBookingCancelled`
  and `GatheringChanged` must each move the number.
- **`SchengenPreviewControllerTest`** — `@WebMvcTest` slice with `@WithMockUser`: URL mapping,
  param type conversion, and a 403 for an anonymous caller.
- **`AuthorizationMatrixTest`** — a `policy()` row for `/api/schengen-preview`.
- **`CalendarRedactionSecurityTest`** — an anonymous body that `doesNotContain("Schengen")` **and**
  does not contain the day number. Assert on absence of the private value, per redaction rule 5.
- **`JsBehaviorTest`** (`@Tag("js")`, `./mvnw test -Pjs-tests`) — the blur triggers, a non-Schengen
  country leaving the panel empty, the warning class appearing at 85, and no layout shift when the
  panel populates. The `fetch` is stubbed with Playwright's `page.route()`; still no server, no
  Spring context, no DB.

Every new or changed test to be proven by mutating production code so it fails for the right
reason, then reverted.

## Known limitations, to state rather than fix

- **Layovers are invisible.** A three-hour Schengen connection inside a booked LAX→AMS→NRT
  itinerary will not count unless it is entered as a separate leg.
- **Days with no data are gaps, not zeros.** The projector knows only what has been booked.
  Unbooked intent does not exist in the event stream, and the count will understate until it is
  entered. This follows directly from the no-padding decision.
- **Unknown airports (step 7)** resolve to an unknown country, never a guess — consistent with
  `LocationZoneResolver`'s strictness. Under-counting is the dangerous direction here, so unknown
  days should be surfaced rather than silently dropped.
