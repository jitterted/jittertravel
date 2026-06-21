# Plan: Store datetimes as UTC + zone, evaluate by instant, display per viewer role

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
  common-zone pick, else a configured default.
- **Evaluate** past/future by comparing instants (zone-independent, always correct).
- **Display** entry-local by default (server-side); for FAMILY (and an anonymous toggle), JS
  upgrades to the viewer's browser zone.

## Decisions (confirmed with the user)

1. Entry zone is **derived from location**, with fallbacks (see Zone resolution).
2. Events persist **UTC instant + zone id**.
3. **Read-time upcaster** for legacy data: derive the zone from the event's own location, else the
   configured default. No rewrite of `event_log`/`command_log`; old export files stay importable.
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

## Zone resolution (`LocationZoneResolver`)

Plain-Java instance service injected at the boundary (controllers/importers). Per endpoint:
1. **Flights via API:** `AeroDataBoxClient.parseLocal()` (`AeroDataBoxClient.java`) already receives
   the airport offset (`"2026-06-28 11:45+02:00"`) and discards it via `.toLocalDateTime()`. Keep
   the `OffsetDateTime`; obtain the IANA zone from the API (airport timezone) when looked up.
2. **Manual entry (no API / no key) + cities:** resolve from city/country where possible (curated,
   dependency-free single-tz-country table + notable cities); otherwise the user picks from a small
   **`CommonZone`** list — USA (Eastern/Central/Mountain/Pacific), Canada, UK, Western Europe (CET).
   Flights, which carry only airport codes, always offer this picker per endpoint as the manual
   fallback.
3. **Configured default** `jittertravel.default-zone` when nothing else resolves.

Editing recomputes the `ZonedTimestamp` at the boundary from current inputs, so changing a
location/zone naturally re-resolves. Wrong guesses are correctable via the common-zone picker.

## Implementation phases

### 1. Value type + resolver
Add `ZonedTimestamp` (domain), `LocationZoneResolver` (+ city/country table), `CommonZone` enum,
`jittertravel.default-zone` property. Unit-test the resolver directly (per renderer/services
testing convention).

### 2. Events & commands → `ZonedTimestamp`
- Change events + commands/contexts (hotel/flight/train/conference/gathering). Flight/train resolve
  departure and arrival zones independently; gathering collapses date+times to start/end.
- Web requests keep binding `datetime-local` wall-clock as `LocalDateTime` (`@DateTimeFormat`
  stays) and gain a zone selector where manual resolution is needed (flights per endpoint; others
  when derivation can fail). At the boundary, resolve zone(s), build `ZonedTimestamp`(s), capture
  `Instant.now()`.
- **Validations run in the entry zone**, not UTC: checkout-after-checkin (`BookHotelCommand.java:21`),
  conference start-before-end, and not-in-the-past comparisons use `utc.atZone(zone).toLocalDate()`
  / instant comparison — never UTC dates (that would reintroduce a midnight-boundary bug).
- **Edit forms prefill** `datetime-local` from `utc.atZone(entryZone)` (and preselect the zone), so
  re-editing never silently shifts the time.

### 3. Evaluation
`TemporalView.relevantUntil()` returns `Instant`; views derive it from their end `ZonedTimestamp`.
`TimeView.includes(...)` compares `Instant`s; the five list controllers pass `Instant.now()`.
Update `TimeViewTest` + projector tests to instants.

### 4. Display
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
- Tests: server-side entry-local formatting = renderer unit tests; the browser-zone upgrade =
  `JsBehaviorTest` (`@Tag("js")`, `./mvnw test -Pjs-tests`, per `docs/JS-Behavior-Tests.md`).

### 5. Backward compatibility (sensitive — events, commands, backups)
- **Read-time JSON upcaster** keyed by type (beside `EventTypes`/`ImportableCommandTypes`): a bare
  scalar datetime → resolve zone from the same payload's location (else default) → rewrite to a
  `ZonedTimestamp` object before record binding. New rows pass through untouched. Applies to **both**
  the `event_log` read/replay path **and** the `command_log`/import path — not only backups.
- **Golden/contract tests:** keep old-format golden files passing *through the upcaster*
  (`GoldenEventDeserializationTest`); add new-shape golden files. Keep
  `EventJsonMapperEquivalenceTest` green with the nested `ZonedTimestamp`.
- **Export/import round-trip:** `CommandImporter` reuses the upcaster so old (scalar) and new
  backups both import; verify in `CommandExportImportRoundTripTest`. No backfill/rewrite of stored
  rows (preserves old-backup compatibility — per the export/import-compat rule).

### 6. Conventions & other consumers
- `TimeFilterToggleConventionTest` discovers `render(List, TimeView)`; if renderer signatures gain a
  display-mode/role param, update the convention test accordingly.
- `pom.xml:183` pins the test JVM to UTC — audit tests that implicitly assume server==UTC; new
  display tests must set explicit zones.
- Inventory any iCal / Google Calendar export / notification path that emits these datetimes and
  route it through UTC/zone too.

### 7. Full test pass
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
1. **Bug repro:** a hotel with checkout earlier today shows under `/booked-hotels` (FUTURE) pre-fix;
   post-fix it drops off once `Instant.now()` passes the checkout instant.
2. `LocationZoneResolver` unit tests: flight API zone, single-tz country, multi-tz city, common-zone
   manual pick, default fallback; plus a DST spring-forward/fall-back case.
3. Per-endpoint zones: a Frankfurt→Paris train stores two different zones; durations correct.
4. Entry-zone validation: checkout same calendar day as checkin still rejected across UTC midnight.
5. `GoldenEventDeserializationTest`: old scalar golden files upcast; new-shape files round-trip.
   `CommandExportImportRoundTripTest`: old + new backups both import.
6. Display by role: OWNER entry-local; FAMILY browser zone; ANON entry-local + working toggle;
   no-JS falls back to entry-local; calendar buckets by entry-zone day. `js` tier asserts a UTC
   instant renders correctly in two browser zones.
7. "All Tests" IDEA config + `./mvnw test -Pjs-tests` green.

## Open follow-ups (not in this plan)
- Promote the common-zone picker to a richer per-entry zone override/search if the curated list and
  city table prove too coarse in practice.