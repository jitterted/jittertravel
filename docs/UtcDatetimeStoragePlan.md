# Plan: Store datetimes as UTC + zone, evaluate by instant, display per viewer role

## Status at a glance (updated 2026-07-26)

Legend: `[x]` done · `[~]` partially done · `[ ]` not started.

| Phase | State | What's left |
|-------|-------|-------------|
| 1. Value type + resolver | `[x]` | — |
| 2. Events & commands → `ZonedTimestamp` | `[~]` | conference + gathering (hotel/train/flight migrated) |
| 3. Evaluation by instant | `[~]` | gathering/conference views still convert via `ZoneId.systemDefault()` |
| 4. Display | `[~]` | browser-zone JS upgrade, role switch, ANON toggle; `<time>` in gathering/conference/itinerary/calendar renderers |
| 5. Backward compatibility | `[~]` | `command_log`/import upcasting; legacy-shape golden + round-trip tests |
| 6. Conventions & other consumers | `[~]` | test-JVM UTC-pin audit (no iCal/GCal export exists → n/a) |
| 7. Full test pass | `[ ]` | run at the end |

Next up: **phase 2 for gathering + conference** (they're the only event types still storing
wall-clock) — detailed in `docs/GatheringConferenceUtcRolloutPlan.md`, which also carries the
conference modernization to handler+context and the test-coverage gaps found reviewing the earlier
slices. Then **command-path upcasting** (phase 5), then the **browser-zone display upgrade**
(phase 4).

## Context

The "upcoming" filter on booked lists shows items already in the past. Root cause: event
datetimes are stored as zoneless `LocalDateTime` (wall-clock), but the FUTURE/past boundary
compares them against `LocalDateTime.now()` resolved in the *server's* default zone
(`BookedHotelsController.java:30`, `TimeView.java:9`). When the server zone differs from where the
entry happens, the boundary is off by the offset and past items linger — the normal case for a
travel app.

Fix: make time unambiguous end to end.
- **Store** every event datetime as a UTC instant **plus** the IANA zone id of that endpoint.
- **Resolve** the zone from the endpoint's location (airport / city+country), else a manual
  common-zone pick. Strict — no default; if neither yields a zone, the command is rejected.
- **Evaluate** past/future by comparing instants (zone-independent, always correct).
- **Display** entry-local by default (server-side); for FAMILY (and an anonymous toggle), JS
  upgrades to the viewer's browser zone.

## Decisions (confirmed with the user)

1. Entry zone is **derived from location** — **strictly, with no default zone** (see Zone
   resolution). This is a travel app; most entries are away from any "home" zone, so a silent
   default would be wrong more often than right.
2. Events persist **UTC instant + zone id**.
3. **Read-time upcaster** for legacy data: derive the zone from the event's own location. With no
   default, an unresolvable legacy location must **fail loudly** rather than be assumed — so the
   existing-data audit (decision below) must pass *before* the upcaster ships, or replay breaks.
   No rewrite of `event_log`/`command_log`; old export files stay importable.
   *Assumption to validate:* legacy bare datetimes were entered as local-at-the-location wall-clock
   (so reinterpreting them in the location zone preserves intent). Confirm old backups still import.
4. **Display zone is role-dependent** (evaluation is unaffected — always instant-based):
   - **OWNER (traveler):** entry-local (each endpoint in its own zone).
   - **FAMILY:** the viewer's **browser zone**.
   - **ANONYMOUS:** entry-local by default, with a toggle to browser zone.
   Roles from `SecurityConfig` (OWNER/FAMILY/ANONYMOUS); `GeneralController.java:42` already reads
   `request.isUserInRole("FAMILY")`. Booking lists (`/booked-*`) are OWNER-only → always entry-local.
5. **No zone label** on rendered times. (Caveat: a mixed-zone itinerary row can be momentarily
   ambiguous; accepted.)
6. **Per-endpoint zones.** Departure and arrival of a flight/train resolve **independently** (a
   Frankfurt→Paris train spans two zones). Hotel check-in/out share the hotel's zone.
7. **Day bucketing is always by entry zone.** Calendar/itinerary place an event on its
   local-at-location day regardless of viewer; only the displayed time may be browser-localized.
8. **No-JS fallback = entry-local** (server-rendered baseline; browser-zone is progressive
   enhancement).

## Core representation

```java
public record ZonedTimestamp(Instant utc, ZoneId zone) {
    public ZonedDateTime atEntryZone()      { return utc.atZone(zone); }
    public ZonedDateTime at(ZoneId display)  { return utc.atZone(display); }
}
```

`utc` is the durable, comparable moment; `zone` retains the endpoint zone for re-edit and
entry-local display. Jackson (`findAndAddModules`, `EventJsonMapperFactory.java`) round-trips
`Instant`/`ZoneId` natively → `{"utc":"2026-06-21T09:00:00Z","zone":"Europe/Paris"}`. **New on-wire
shape** (see Backward compat).

Field changes (each `LocalDateTime` → `ZonedTimestamp`, independent zone per field):
- `HotelBooked`/`HotelChanged`: checkIn, checkOut (same zone).
- `FlightBooked`/`FlightChanged`: departure, arrival (independent zones).
- `TrainBooked`/`TrainChanged`: departure, arrival (independent zones).
- `ConferenceTentativelyPlanned`: startDate, endDate.
- `GatheringPlanned`: collapse `date`+`startTime`/`endTime` into `startsAt`/`endsAt`
  `ZonedTimestamp`s.

**DST policy:** building a `ZonedTimestamp` from a typed wall-clock uses `ZonedDateTime.of(local,
zone)` (lenient: non-existent spring-forward times shift forward, fall-back ambiguity picks the
earlier offset). Documented and accepted; no custom rejection.

## Zone resolution (`LocationZoneResolver`) — strict, no default

Plain-Java instance service injected at the boundary (controllers/importers). **`resolve(Address)`
returns a `ZoneId` or throws `ZoneResolutionException` — there is no default fallback.** Per
endpoint the boundary's contract is:
1. **Explicit `CommonZone` wins.** If the form carries a user-chosen zone, use it and never call
   location resolution. The list is small: USA (Eastern/Central/Mountain/Pacific), Canada, UK,
   Western Europe (CET). Flights, which carry only airport codes, always offer this picker per
   endpoint as the manual entry path.
2. **Flights via API:** `AeroDataBoxClient.parseLocal()` (`AeroDataBoxClient.java`) already receives
   the airport offset (`"2026-06-28 11:45+02:00"`) and discards it via `.toLocalDateTime()`. Keep
   the `OffsetDateTime`; obtain the IANA zone from the API (airport timezone) when looked up.
3. **Location-based resolve** from city/country (curated, dependency-free single-tz-country table +
   notable cities for multi-tz countries).
4. **Otherwise: command validation fails.** When no explicit `CommonZone` was chosen and
   location-based resolve throws, the command is rejected and the form re-prompts, *requiring* a
   `CommonZone` selection. A record is therefore stored either with a verified-resolved zone or a
   human-chosen zone — never a silent guess.

**Correctness is proven by an audit** over every distinct location already in `event_log` /
`command_log` (plus airport codes via the flight path): each must resolve, and the
`location → zone` output is eyeballed once. After that, any unrecognized future location fails fast
at the boundary (extend the curated table, or the user picks a `CommonZone`).

Editing recomputes the `ZonedTimestamp` at the boundary from current inputs, so changing a
location/zone naturally re-resolves. Wrong guesses are correctable via the common-zone picker.

## Implementation phases

### 1. Value type + resolver — `[x]` done
Add `ZonedTimestamp` (domain), `LocationZoneResolver` (+ city/country table, throws
`ZoneResolutionException` on a miss — no default), `CommonZone` enum. Unit-test the resolver
directly (per renderer/services testing convention).

Shipped: `ZonedTimestamp`, `CommonZone`, `LocationZoneResolver`, `AirportZoneResolver`,
`StationZone`, `FlightEndpointZone`, `ZoneResolutionException`, each with unit tests.

### 2. Events & commands → `ZonedTimestamp` — `[~]` hotel/train/flight done, conference + gathering left
Remaining work is planned in detail in `docs/GatheringConferenceUtcRolloutPlan.md` (previous slice:
`docs/TrainFlightUtcRolloutPlan.md`).

- `[x]` Hotel (`f35b7d6`), train (`daa7107`), flight (`d2884fb`): events, commands, contexts,
  requests and controllers all carry `ZonedTimestamp`.
- `[ ]` `ConferenceTentativelyPlanned` (startDate/endDate) + `PlanTentativeConferenceCommand` /
  `PlanTentativeConferenceRequest`.
- `[ ]` `GatheringPlanned`/`GatheringChanged` — still `LocalDate date` + `LocalTime start/endTime`;
  collapse to `startsAt`/`endsAt` per the field-changes list above, plus `PlanGatheringCommand`,
  `ChangeGatheringCommand`, their contexts, requests and controllers.
- `[ ]` Entry-zone validation for those two (`InvalidGatheringTimeRange`,
  `GatheringDateNotInFuture`, `ConferenceSpansMultipleDays`, `DateRangeNotInFuture`).

Original detail, still the spec for the two remaining types:
- Change events + commands/contexts (hotel/flight/train/conference/gathering). Flight/train resolve
  departure and arrival zones independently; gathering collapses date+times to start/end.
- Web requests keep binding `datetime-local` wall-clock as `LocalDateTime` (`@DateTimeFormat`
  stays) and gain an optional `CommonZone` selector. At the boundary, per endpoint: if a
  `CommonZone` was chosen, use it; otherwise call `resolve(address)`. If that throws
  `ZoneResolutionException` and no `CommonZone` was supplied, **command validation fails** and the
  form re-renders *requiring* a `CommonZone` selection. Then build `ZonedTimestamp`(s), capture
  `Instant.now()`.
- **Validations run in the entry zone**, not UTC: checkout-after-checkin (`BookHotelCommand.java:21`),
  conference start-before-end, and not-in-the-past comparisons use `utc.atZone(zone).toLocalDate()`
  / instant comparison — never UTC dates (that would reintroduce a midnight-boundary bug).
- **Edit forms prefill** `datetime-local` from `utc.atZone(entryZone)` (and preselect the zone), so
  re-editing never silently shifts the time.

### 3. Evaluation — `[~]` mechanism done, two views on the stopgap
`TemporalView.relevantUntil()` returns `Instant`; views derive it from their end `ZonedTimestamp`.
`TimeView.includes(...)` compares `Instant`s; the five list controllers pass `Instant.now()`.
Update `TimeViewTest` + projector tests to instants.

- `[x]` `TemporalView`/`TimeView` on `Instant`, controllers pass `Instant.now()`, `TimeViewTest`
  updated (`5dab535`).
- `[ ]` `PlannedGatheringView.relevantUntil()` and `TentativeConferenceView.relevantUntil()` still
  do `.atZone(ZoneId.systemDefault())` — the documented stopgap; it disappears with phase 2.

### 4. Display — `[~]` server-side baseline started, browser-zone upgrade not started
- `[x]` `ZonedTimeTag` helper emitting `<time datetime="…Z" data-fmt="…">`, used by
  `BookedHotelsRenderer`, `BookedTrainsRenderer`, `BookedFlightsRenderer` (`a97e96e`, `7b31d6d`).
- `[ ]` Same `<time>` treatment in `PlannedGatheringsRenderer`, `TentativeConferencesRenderer`,
  `ItineraryRenderer`, `CalendarViewBuilder`, `ScheduleProblemsRenderer`.
- `[ ]` Browser-zone upgrade script, role switch (OWNER/FAMILY/ANON), `?tz=` toggle.
- `[ ]` The two `JsBehaviorTest`s below (rendering in two pinned `timezoneId` contexts; toggle
  interaction).
- `[x]` Day bucketing by entry zone for migrated types: calendar/itinerary/gap projectors read
  `ZonedTimestamp.localDateTime()` (entry-zone wall-clock), so grouping is already entry-local;
  gathering/conference follow when phase 2 lands.

Spec for the remaining items:
- **Baseline (all roles, no JS needed):** the renderer always emits entry-local, server-side, as
  `<time datetime="2026-06-21T09:00:00Z" data-fmt="EEE, MMM d, h:mm a">Sun, Jun 21, 11:00 AM</time>`
  — the element text is the entry-local rendering; the `datetime` attribute carries the UTC instant.
- **Browser-zone upgrade:** for FAMILY (default) and ANONYMOUS-with-toggle, a tiny shared inline
  script rewrites each `<time>` to the browser zone via `Intl.DateTimeFormat`. OWNER ships without
  the script. No JS ⇒ entry-local automatically (decision 8).
- A shared zone-aware formatting helper produces the `<time>` element from a `ZonedTimestamp`. The
  anonymous toggle mirrors the existing `TimeFilterToggle` pattern; a `?tz=` param (read like
  `TimeView.fromParam`) selects the mode where a toggle is offered.
- **Day bucketing by entry zone** (decision 7): `CalendarViewBuilder`/`ItineraryProjector` group by
  `utc.atZone(entryZone).toLocalDate()`; `ScheduleGapProjector`'s `.toLocalDate()` overlap/
  "missing hotel" logic likewise uses entry-zone local dates.
- Tests: server-side entry-local formatting = renderer unit tests; the browser-zone behavior =
  `JsBehaviorTest` (`@Tag("js")`, `./mvnw test -Pjs-tests`, per `docs/JS-Behavior-Tests.md`):
  - **Rendering test:** render HTML with a known `<time datetime="…Z">`, load via `setContent` in a
    Playwright context pinned to a fixed zone (`browser.newContext({ timezoneId })`), assert the
    upgraded text equals the expected browser-zone time; a second context with a different
    `timezoneId` proves the same instant localizes differently.
  - **Toggle-interaction test:** start from the entry-local baseline text, click the browser-zone
    toggle, assert each `<time>` switches to the browser-zone rendering; toggle back and assert it
    returns to the entry-local baseline. If the toggle persists via `?tz=`/`localStorage`, assert
    the choice survives a reload. No server/Spring/DB/auth — JS only.

### 5. Backward compatibility — `[~]` event path covered, command/import path is the gap
- `[x]` Zone audit shipped (`/admin/zone-audit`, `LocationZoneAudit`) and **passed 2026-06-21**
  (17 locations, all resolved).
- `[x]` `EventPayloadUpcaster` wired into the `event_log` read path via `PostgresPersister`;
  handles `HotelBooked/Changed`, `TrainBooked/Changed`, `FlightBooked/Changed`; idempotent on
  new-shape payloads. Unit tests cover hotel + train.
- `[ ]` Flight case has no upcaster unit test.
- `[ ]` **`command_log` / import path is not upcast.** `BookHotelCommand` & co. now bind
  `ZonedTimestamp`, so a pre-migration backup file (scalar `checkIn`) fails to import.
  `CommandImporter` needs to run payloads through the upcaster.
- `[ ]` `CommandExportImportRoundTripTest` case proving an old (scalar) backup still imports.
- `[ ]` Golden legacy-shape samples run *through the upcaster*
  (`GoldenEventDeserializationTest` currently has new-shape samples only for the migrated types).
- `[ ]` Upcaster cases for conference + gathering, added with phase 2.

Spec:
- **Existing-data audit (do first):** sweep every distinct location in `event_log` / `command_log`
  (plus airport codes via the flight path) through `resolve`; assert each succeeds and review the
  `location → zone` output once. This must pass *before* the upcaster ships — with no default, an
  unresolvable legacy location has nowhere to go and replay would throw.
- **Read-time JSON upcaster** keyed by type (beside `EventTypes`/`ImportableCommandTypes`): a bare
  scalar datetime → resolve zone from the same payload's location → rewrite to a `ZonedTimestamp`
  object before record binding. **No default:** an unresolvable location fails loudly (the audit
  guarantees this can't happen for known data). New rows pass through untouched. Applies to **both**
  the `event_log` read/replay path **and** the `command_log`/import path — not only backups.
- **Golden/contract tests:** keep old-format golden files passing *through the upcaster*
  (`GoldenEventDeserializationTest`); add new-shape golden files. Keep
  `EventJsonMapperEquivalenceTest` green with the nested `ZonedTimestamp`.
- **Export/import round-trip:** `CommandImporter` reuses the upcaster so old (scalar) and new
  backups both import; verify in `CommandExportImportRoundTripTest`. No backfill/rewrite of stored
  rows (preserves old-backup compatibility — per the export/import-compat rule).

### 6. Conventions & other consumers — `[~]`
- `[ ]` `TimeFilterToggleConventionTest` discovers `render(List, TimeView)`; if renderer signatures
  gain a display-mode/role param (phase 4), update the convention test accordingly.
- `[ ]` `pom.xml:183` pins the test JVM to UTC — audit tests that implicitly assume server==UTC; new
  display tests must set explicit zones.
- `[x]` iCal / Google Calendar export / notification paths: none exist in the codebase — nothing to
  route. Revisit if one is added.

### 7. Full test pass — `[ ]`
"All Tests" IDEA run configuration + `./mvnw test -Pjs-tests`. Stage all new files for review.

## Critical files
- New: `domain/ZonedTimestamp.java`, `application/LocationZoneResolver.java` (+ city/country table),
  `CommonZone` enum, the JSON upcaster, a shared time-formatting helper + browser-zone toggle.
- Events: `HotelBooked/Changed`, `FlightBooked/Changed`, `TrainBooked/Changed`,
  `ConferenceTentativelyPlanned`, `GatheringPlanned`.
- Commands/contexts + web requests: `BookHotelCommand`/`ChangeHotelCommand` (+ flight/train/
  conference/gathering), `BookHotelRequest`/`ChangeHotelRequest` (+ siblings).
- Eval: `TemporalView`, `TimeView`, the five list controllers.
- Display: renderers (`BookedHotelsRenderer` + siblings), `ItineraryProjector`/`CalendarViewBuilder`/
  `ScheduleGapProjector`; role read as in `GeneralController.java:42`.
- Flight zone source: `AeroDataBoxClient.parseLocal` (preserve offset, capture airport zone).
- Compat: `EventJsonMapperFactory`/`EventTypes`, `GoldenEventDeserializationTest`,
  `CommandImporter`, `CommandExportImportRoundTripTest`, `EventJsonMapperEquivalenceTest`.

## Verification

Status: 1 `[x]` (hotel filtering fixed in `5dab535`) · 2 `[x]` · 3 `[x]` (train) · 4 `[~]`
(hotel/train/flight done, gathering/conference pending) · 5 `[~]` (new-shape goldens exist;
legacy-through-upcaster + old-backup import missing) · 6 `[ ]` · 7 `[ ]`.

1. **Bug repro:** a hotel with checkout earlier today shows under `/booked-hotels` (FUTURE) pre-fix;
   post-fix it drops off once `Instant.now()` passes the checkout instant.
2. `LocationZoneResolver` unit tests: flight API zone, single-tz country, multi-tz city, common-zone
   manual pick, and `ZoneResolutionException` on an unresolvable location; plus a DST
   spring-forward/fall-back case.
3. Per-endpoint zones: a Frankfurt→Paris train stores two different zones; durations correct.
4. Entry-zone validation: checkout same calendar day as checkin still rejected across UTC midnight.
5. `GoldenEventDeserializationTest`: old scalar golden files upcast; new-shape files round-trip.
   `CommandExportImportRoundTripTest`: old + new backups both import.
6. Display by role: OWNER entry-local; FAMILY browser zone; ANON entry-local + working toggle;
   no-JS falls back to entry-local; calendar buckets by entry-zone day. `js` tier: a UTC instant
   renders correctly in two browser zones, **and a Playwright toggle test confirms clicking the
   browser-zone toggle switches the displayed times and toggling back restores entry-local**.
7. "All Tests" IDEA config + `./mvnw test -Pjs-tests` green.

## Open follow-ups (not in this plan)
- Promote the common-zone picker to a richer per-entry zone override/search if the curated list and
  city table prove too coarse in practice.