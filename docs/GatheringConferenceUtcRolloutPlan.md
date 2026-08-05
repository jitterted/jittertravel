# Plan: Roll UTC + zone storage out to Gatherings and Conferences

Third and final slice of `docs/UtcDatetimeStoragePlan.md` phase 2. Hotels, trains and flights already
ship the pattern (`ZonedTimestamp` events, boundary zone resolution, read-time upcaster, `<time>`
rendering); see `docs/TrainFlightUtcRolloutPlan.md` for the previous slice. Gatherings and
conferences are the only event types still storing bare wall-clock times.

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done.

**Gathering is done** (2026-07-27): events, commands, contexts, handlers, views, projectors, web,
upcaster and their tests. Left for later: its renderer moves to `<time>` in phase 4.

**Conference is done** (2026-08-05): all eight steps and every ride-along below — domain event and
command on `ZonedTimestamp`, the handler+context modernization, `ConferencePlanning` on
`CommandExecutor` with the read-only guard moved into the executor and an architecture test to hold
it, view/projectors/web/upcaster, and the `ScheduleGapProjector` instant sweep (master-plan bug R2).
Full suite green (596) plus `-Pjs-tests` (5). **Still open in this slice:** tests 4 and 8 below, and
`PlannedGatheringsRenderer`'s deferred `<time>` treatment (gathering step 9).

**2026-08-05:** the review (`docs/UtcDatetimePlanReview.md`, folded into the master plan) added
ride-along items to the conference steps below — they're mirrored in here at their anchor points,
marked *(review ride-along)*. The master plan's "Bugs" section (R1–R3) also matters to this slice:
fix **R1** (deterministic `commandId()` for `MigrateConferenceToGathering` /
`ClearDifferentCityConflict`) *before* the slice — it's standalone and protects every backup made
in the meantime. **Urgency note:** the server runs UTC, so `TentativeConferenceView`'s
`relevantUntil()` stopgap reads the venue wall-clock as UTC — a US conference ending 17:00 local
drops off the FUTURE list ~7–8 hours *early*. The plan's original bug is still live for
conferences, in the opposite direction.

## Decisions (confirmed 2026-07-26)

1. **Conferences stay a separate concept from gatherings.** A conference is a multi-day or
   single-full-day event; a gathering is a few hours on a single day (often an evening). The
   conference→gathering migration path (`ConferenceMigrationService`,
   `MigrateConferenceToGathering`, `/admin/migrate-conferences`) is a correction tool for entries
   filed under the wrong concept, **not** a deprecation of conferences. So conferences get the full
   `ZonedTimestamp` treatment, same as everything else.
2. **`PlanTentativeConferenceCommand` gets modernized to the handler + context pattern** as part of
   this slice. It is the oldest code in the app (conferences were the first concept implemented) and
   the only command that still takes a web request DTO directly. Details in "Conference
   modernization" below.
3. **Gathering collapses `date` + `startTime` + `endTime` into `startsAt`/`endsAt`** (per the master
   plan's field list). This is a *shape* change, not just a type change — see "Why gathering is
   harder" below.
4. **Both endpoints of an item share one zone.** A gathering happens at one venue; a conference has
   one `venueAddress`. Unlike trains and flights, there is no two-independent-zones wrinkle here —
   one `resolve` call per command, one `CommonZone` picker per form.
5. **The command/request wire shape does not change.** Only an optional `zone` field is added.
   See "Backward compatibility" — this is what keeps existing backups importable.
6. **Schedule conflicts: detect by instant, report in entry-zone local.** Comparing wall-clock
   date+time across two zones compares numbers that denote different moments, and the "same
   calendar date" precondition is the bigger liability — it hides any conflict that straddles
   midnight in one of the zones (a San Francisco evening gathering and a Tokyo morning gathering
   two hours apart in real time are never even compared). Detection therefore moves to `utc()`
   comparisons; the problem records keep their `LocalDate`/`LocalTime` fields, derived from
   `atEntryZone()`, so displayed messages are unchanged.

## Why gathering is harder than the previous three

Hotel/train/flight changed a field's *type* in place: `LocalDateTime departure` →
`ZonedTimestamp departure`. The upcaster rewrote a scalar into an object under the same key, and the
golden samples were simply updated to the new shape.

Gathering changes the field *set*: three fields (`date`, `startTime`, `endTime`) become two
(`startsAt`, `endsAt`). Consequences that have no precedent in the earlier slices:

- The upcaster must **merge and delete keys**, not rewrite one in place, and must stay idempotent.
- `GoldenEventDeserializationTest` runs with `FAIL_ON_UNKNOWN_PROPERTIES` and **does not route
  through the upcaster**. A legacy gathering sample therefore cannot simply be kept — it fails on the
  removed keys. The test needs an upcast-then-deserialize path (the master plan's phase-5 bullet
  "keep old-format golden files passing *through the upcaster*", which was never actually built —
  hotel and train rewrote their samples instead, so the repo currently has **zero** golden coverage
  of any legacy event shape).
- Validation moves from a `LocalDate today` context to an `Instant now` context, which changes
  `ImportableCommand.IMPORT_BYPASS_DATE` → `IMPORT_BYPASS_INSTANT` for these commands.

## Gathering rollout steps

1. `[x]` **Domain** — `GatheringPlanned`, `GatheringChanged`: replace
   `LocalDate date, LocalTime startTime, LocalTime endTime` with
   `ZonedTimestamp startsAt, ZonedTimestamp endsAt`. Same field change on `PlanGatheringCommand` and
   `ChangeGatheringCommand`. Move validation onto instants/entry-zone per `BookHotelCommand`:
   - future check: **kept at date granularity**, not switched to an instant comparison. The old rule
     was `date.isAfter(today)` — a gathering must be on a *later day*, and a test pinned that one
     later today is rejected. Shipped as `ZonedTimestamp.isOnDayAfter(Instant)`, which reads both
     sides in the gathering's own zone (`GatheringDateNotInFuture`). Relaxing this to "any later
     moment" would be a deliberate behavior change, not a side effect of the storage migration.
   - end-after-start → `endsAt.utc().isAfter(startsAt.utc())` (`InvalidGatheringTimeRange`);
   - `isOnDayAfter` is expressed as "reference falls before midnight of this day" rather than by
     converting the reference to a local date — equivalent, and it survives the `Instant.MIN`
     import-bypass sentinel, which overflows when given a zone.
2. `[x]` **Contexts** — `GatheringPlanningContext(LocalDate today)` →
   `GatheringPlanningContext(Instant now)`; same for `ChangeGatheringContext`. `GatheringPlanning`
   and `ChangeGathering` take `Instant now` instead of `LocalDate today`; controllers pass
   `Instant.now(clock)`.
3. `[x]` **Handlers** — `PlanGatheringHandler` and `ChangeGatheringHandler` take a
   `LocationZoneResolver` (they currently take no dependencies), resolve the venue zone once
   (explicit `CommonZone` wins, else `resolve(location)`, else `ZoneResolutionException`), and build
   both `ZonedTimestamp`s from `date.atTime(startTime)` / `date.atTime(endTime)` in that zone.
4. `[x]` **Views** — `PlannedGatheringView` holds `ZonedTimestamp startsAt/endsAt`;
   `relevantUntil()` returns `endsAt.utc()`, removing the documented server-zone STOPGAP.
   `GatheringDetailsView` likewise, so the edit form prefills from `atEntryZone()`.
5. `[x]` **Projectors** — `PlannedGatheringsProjector`, `GatheringDetailsViewProjector`,
   `GatheringCalendarProjector` (`date.atTime(startTime)` → `startsAt.localDateTime()`),
   `GatheringItineraryEntry` + `ItineraryProjector`.
6. `[x]` **`ScheduleGapProjector` — instants for detection, entry-zone locals for reporting**
   (decided 2026-07-26, decision 6 below). `GatheringOccupancy.overlapsWith` becomes
   `this.startsAt.utc().isBefore(other.endsAt.utc()) && other.startsAt.utc().isBefore(this.endsAt.utc())`
   — the `date.equals(other.date)` precondition **goes away entirely**, which is the point: it is
   what currently hides any conflict straddling midnight in one of the two zones.
   `detectDifferentCityConflicts` likewise compares the gathering's instants against the
   conference's `startsAt`/`endsAt` instants instead of `LocalDate`s. `ScheduleProblem`
   (`SchedulingConflict`, `DifferentCityConflict`) keeps carrying `LocalDate`/`LocalTime` for its
   messages, now derived from `atEntryZone()` — displayed text is unchanged. Day *bucketing* for
   display stays entry-zone (decision 7 of the master plan).
7. `[x]` **Web** — `PlanGatheringRequest`/`ChangeGatheringRequest` keep `date`/`startTime`/`endTime`
   and gain an optional `zone` (a `CommonZone` name); `events()` passes a real
   `LocationZoneResolver`. Controllers add `@ModelAttribute("commonZones")` and a
   `ZoneResolutionException` catch that rejects the `zone` field. `plan-gathering.html` and
   `change-gathering.html` gain the selector block (copy `book-hotel.html:256-270`). The edit form
   prefills date/times from the venue-zone wall-clock and leaves the picker on "derive from
   location" — matching what the hotel edit form does, rather than the "preselect the stored zone"
   this plan originally called for.
8. `[x]` **`MigrateConferenceToGathering`** — this `ImportableCommand` record builds
   `GatheringPlanned` **directly**, bypassing the handler, and appears in existing backups. Resolve
   the zone from its own `location` inside `events()` rather than adding a record field: old backups
   stay importable, and the work stays in `events()` where it runs during import validation (per
   `CLAUDE.md`). `ConferenceMigrationService` correspondingly reads
   `conference.startsAt().atEntryZone()` when destructuring into date/start/end.
9. `[ ]` **Renderer** (deferred to phase 4 with the other list views) — `PlannedGatheringsRenderer` renders each time via
   `ZonedTimeTag.render(view.startsAt(), "…")`, matching the booked-list renderers.
10. `[x]` **Upcaster** — add `"GatheringPlanned"`/`"GatheringChanged"` cases: resolve the zone from
    `location.{city,country}`, merge `date`+`startTime` → `startsAt` and `date`+`endTime` → `endsAt`,
    and **remove** the three legacy keys. Idempotent when `startsAt` is already present.

## Conference rollout steps

1. `[x]` **Domain** — `ConferenceTentativelyPlanned`: `startDate`/`endDate`
   `LocalDateTime → ZonedTimestamp` (both in the venue's single zone).
2. `[x]` **Modernization (decision 2)** — turn `PlanTentativeConferenceCommand` into a
   `record … implements DomainCommand<PlanTentativeConferenceContext>`:
   - new `PlanTentativeConferenceContext(Instant now)` (mirrors `BookHotelContext`);
   - new `PlanTentativeConferenceHandler(LocationZoneResolver)` mapping request → command, resolving
     the venue zone (explicit `CommonZone` wins, else `resolve(venueAddress)`);
   - `execute(context)` keeps the existing rules on instants: start at least one day out
     (`DateRangeNotInFuture`), end on/after start (`InvalidDateRange`);
   - *(review ride-along)* `[x]` **decided 2026-08-05: "a later calendar day at the venue"**,
     via `ZonedTimestamp.isOnDayAfter` — matching gatherings. This is a deliberate behavior
     change: a conference starting tomorrow morning is now accepted even when that is under 24
     hours away, where the old wall-clock `now.plusDays(1)` rule rejected it. Reuses
     `ZonedTimestamp.isOnDayAfter`, if the answer is "a later calendar day". The old rule is
     `startDate.isBefore(now.plusDays(1))` on wall-clock
     (`PlanTentativeConferenceCommand.java:11`); re-deriving it as
     `now.plus(Duration.ofDays(1))` would silently change semantics ("24h out" vs "later day")
     *and* re-open the `Instant.MIN` overflow trap `isOnDayAfter`'s javadoc warns about
     (`ZonedTimestamp.java:68`) — the gathering slice already built the sentinel-safe version;
   - the command no longer imports `PlanTentativeConferenceRequest` — the domain stops depending on
     the web package.
3. `[x]` **`ConferencePlanning` must go through `CommandExecutor`.** It currently injects
   `EventStore` + `PostgresPersister` and calls `persister.saveCommand(...)` then
   `eventStore.append(...)` by hand — a direct violation of the architecture rule in `CLAUDE.md`
   ("Application services must never receive `EventStore`"). Rewrite it to mirror `HotelBooking`:
   `commandExecutor.execute(conferenceId, request, context, command)`, with `Instant now` passed in
   from the controller. It also drops its own `isReadOnly()` clock/read-only handling in favor of
   whatever `CommandExecutor` already enforces.
   - *(review ride-along)* `[x]` **Read-only guard moved into `CommandExecutor`** (2026-08-05):
     parity did *not* hold, so `execute`/`appendEvents` now throw `ReadOnlyModeException` before
     `saveCommand`, covered by two `CommandExecutorTest` cases. `PlanConferenceController` reads
     the flag via `ConferencePlanning.isReadOnly()`, delegating to the executor. Original note: `ConferencePlanning` throws
     `ReadOnlyModeException` *before* saving the command — confirm `CommandExecutor` gives the
     same guarantee (no command row written in read-only mode). `PlanConferenceController` also
     calls `applicationService.isReadOnly()` directly in two places and needs a replacement
     source for that flag.
   - *(review ride-along)* `[x]` **Application-layer architecture guard added** (2026-08-05):
     `ApplicationServicesUseCommandExecutorTest`, plain reflection over `application` constructors,
     excluding `CommandExecutor` itself. CLAUDE.md TODO deleted. Original note: `ConferencePlanning` is the last `EventStore`-injecting service (CLAUDE.md rule +
     TODO). The moment the rewrite lands, add the enforcement test so it can never regress: a
     plain reflection test over `application`-package constructors (in
     `src/test/java/.../architecture/`, styled like `NoFullyQualifiedClassReferencesTest`) — no
     ArchUnit dependency. Delete the CLAUDE.md TODO when done.
4. `[x]` **View** — `TentativeConferenceView` holds `ZonedTimestamp startDate/endDate`;
   `relevantUntil()` returns `endDate.utc()`, removing its STOPGAP.
5. `[x]` **Projectors** — `TentativeConferenceProjector` (the single-day filter at line 48 becomes an
   entry-zone `toLocalDate()` comparison), `ConferenceCalendarProjector`, `ConferenceItineraryEntry`
   + `ItineraryProjector`, and `ScheduleGapProjector`'s `CityOccupancy.startDate()/endDate()`.
   - *(review ride-along — master plan bug R2)* `[x]` **done 2026-08-05: `ScheduleGapProjector`
     comparisons and sequencing moved to instants across the board.** `TravelLeg` and
     `CityOccupancy` now hold `ZonedTimestamp`s; `allLegs()` sorts by `utc()`,
     `deduplicateMissingTravel`/`nextDepartureFromCity`/`detectMissingTravelToFromConferences`
     compare `utc()`, and `detectDifferentCityConflicts` became a true instant overlap instead of
     a local-date range test. `ScheduleProblem.MissingTravel` carries `ZonedTimestamp`s so the
     dedup has instants to compare; night bucketing and rendered text stay entry-zone local.
     Original note:, not just `CityOccupancy`:
     `detectMissingTravelToFromConferences` compares leg wall-clocks with conference wall-clocks
     (`ScheduleGapProjector.java:150-188`); `allLegs()` sorts legs by wall-clock departure
     (`:274-278`); `deduplicateMissingTravel` (`:116`) and `nextDepartureFromCity` (`:269`) mix
     arrival wall-clock in one city with departure wall-clock in another. Same class of bug
     decision 6 fixed for gathering conflicts, same fix: **sequence and compare by `utc()`; keep
     entry-zone locals only for night bucketing and messages.** The events already carry
     `ZonedTimestamp`, so don't leave `TravelLeg` on `LocalDateTime`.
   - *(review ride-along)* `[x]` moved 2026-08-05 — `TentativeConferenceProjectorTest` lives in
     `application/` beside its projector. Original note: while the
     projector is in `application/` — move it beside its peers while touching it.
6. `[x]` **Web** — `PlanTentativeConferenceRequest` keeps `startDate`/`endDate` as `LocalDateTime`
   and gains an optional `zone`; `events()` calls the new handler with a real `LocationZoneResolver`.
   `PlanConferenceController` gains `@ModelAttribute("commonZones")` and a `ZoneResolutionException`
   catch; `plan-conference.html` gains the selector block. `TentativeConferencesRenderer` moves to
   `ZonedTimeTag`.
7. `[x]` **Upcaster** — add a `"ConferenceTentativelyPlanned"` case: resolve from
   `venueAddress.{city,country}`, rewrite both scalars in place (the simple hotel-style rewrite).
8. `[x]` **Sentinel cleanup** — done 2026-08-05: `IMPORT_BYPASS_NOW` and `IMPORT_BYPASS_DATE` had no users left once the conference command moved to `Instant`, and are deleted; only `IMPORT_BYPASS_INSTANT` remains. Original note: — after the conference command modernizes to
   `Instant now`, check whether `IMPORT_BYPASS_NOW` (`LocalDateTime.MIN`) and
   `IMPORT_BYPASS_DATE` still have any users (`ImportableCommand.java:24-25`) and delete the dead
   ones — they're exactly the kind of trap the next new command grabs.

## Backward compatibility

- **Event shape changes; command shape does not.** The request/command JSON in backups keeps
  `date`/`startTime`/`endTime` (gathering) and `startDate`/`endDate` (conference) exactly as they are
  today, gaining only an optional `zone` that older files simply lack (absent ⇒ derive from
  location). This is the same trick hotels used and is what keeps every pre-migration backup
  importable. **Do not** collapse the request fields to match the event fields.
- The `/admin/zone-audit` sweep passed 2026-06-21 over all stored locations, so the no-default
  upcaster cannot throw on real data. Re-run it before shipping, since gathering/conference venues
  are in scope of that audit.
- No rewrite of stored `event_log`/`command_log` rows.
- **Found while implementing:** `LocationAuditProjector` swept `GatheringPlanned` but not
  `GatheringChanged` — the only `*Changed` event it missed. A gathering edited to an unresolvable
  venue would therefore pass the audit and then throw on replay. Fixed; re-run `/admin/zone-audit`
  before deploying, since it now covers strictly more events than the 2026-06-21 green run did.
- **Watch out for country codes:** the curated table keys on country *names* (what the Nominatim
  address parser returns), so a manually-typed `"GB"` does not resolve and forces a `CommonZone`
  pick. Test fixtures using `"GB"` had to change. Consider adding ISO alpha-2 codes for the
  single-zone countries (never for the multi-zone ones) if this bites in practice.

## Tests

Existing tests to update (all currently assert wall-clock values):
`PlanGatheringCommandTest`, `ChangeGatheringCommandTest`, `PlanTentativeConferenceCommandTest`,
`PlannedGatheringsProjectorTest`, `GatheringDetailsViewProjectorTest`,
`GatheringCalendarProjectorTest`, `ConferenceCalendarProjectorTest`,
`TentativeConferenceProjectorTest`, `PlannedGatheringsRendererTest`,
`TentativeConferencesRendererTest`, `ItineraryProjectorTest`, `ScheduleGapProjectorTest`,
`CalendarViewBuilderTest`, `CalendarAggregatorTest`, `CalendarEntryRedactorTest`,
`ItineraryRendererTest`, `ConfirmedCalendarRendererTest`, `CalendarControllerTest`,
`ConferenceMigrationServiceTest`, `EventJsonMapperEquivalenceTest`,
`GoldenEventDeserializationTest`, `PlanGatheringWebIntegrationTest`,
`ChangeGatheringWebIntegrationTest`, `ConferenceWebIntegrationTest`,
`PlanConferenceControllerValidationTest`.

New tests this slice must add — the gaps found reviewing the earlier slices:

1. `[x]` **Handler zone tests** (gathering done; hotel/train/flight backfill still open) (`PlanGatheringHandlerTest`, `ChangeGatheringHandlerTest`,
   `PlanTentativeConferenceHandlerTest`): an explicit `CommonZone` wins over the address; an absent
   pick derives from the address; an unresolvable address with no pick throws
   `ZoneResolutionException`. **These rules are currently untested for every migrated type** — there
   is no `BookHotelHandlerTest`/`BookTrainHandlerTest`, and `BookHotelControllerValidationTest` only
   pins a zone to sidestep ambiguity. Backfilling hotel/train/flight is cheap once the pattern
   exists and should ride along.
2. `[x]` **`EventPayloadUpcasterTest` cases** for `GatheringPlanned`/`GatheringChanged` (three keys →
   two, legacy keys removed, idempotent on new-shape input) and `ConferenceTentativelyPlanned`.
   While here: the flight cases added in `d2884fb` have **no upcaster test at all** — add them.
3. `[x]` **Golden legacy coverage — decided 2026-07-26: build this now, in this slice.** Give
   `GoldenEventDeserializationTest` an upcast-then-deserialize path and add legacy-shape samples for
   gathering and conference **and**, retroactively, for hotel/train/flight, which lost theirs when
   their samples were rewritten to the new shape. Keep the new-shape samples too. Writing the path
   once covers all five types, and it is the master plan's phase-5 "old golden files pass through
   the upcaster" bullet finally landing.
   - *(review ride-along)* `[x]` done 2026-08-05 — the conference golden sample now goes through
     `deserializeLegacy` and asserts the venue-zone instants, with a new-shape sample
     (`conferenceTentativelyPlannedNewShapePayloadDeserializes`) beside it. Original note: `conferenceTentativelyPlannedLegacyPayloadWithStateFieldDeserializes`
     (`GoldenEventDeserializationTest.java:97`) binds scalar `startDate`/`endDate` directly today;
     once the event holds `ZonedTimestamp`s it must go through `deserializeLegacy`, and a
     *new-shape* conference sample gets added beside it, matching the other types.
   - *(review, 2026-08-05 — contradicts the `[x]` above)* `[ ]` the flight cases in
     `EventPayloadUpcasterTest` were **not** actually added: the golden test covers `FlightBooked`
     end-to-end, but nothing anywhere covers `FlightChanged` through the upcaster. Add both
     (via `AirportZoneResolver`). Also `[ ]` a legacy golden sample for `GatheringChanged` — only
     `GatheringPlanned` has one, and `GatheringChanged` carries the same field trio.
4. `[ ]` **`CommandExportImportRoundTripTest`** — a zone-less legacy gathering command and a
   zone-less legacy conference command must both import and produce the right instants.
5. `[ ]` **`MigrateConferenceToGathering.events()`** — direct test that it resolves a zone from its
   location (today it is only exercised indirectly via `ConferenceMigrationServiceTest`); once
   master-plan bug R1 is fixed, also assert `commandId()` returns the same value on every call.
6. `[x]` **Zone-skew FUTURE filter** — a gathering and a conference whose end has passed in the entry
   zone but not in UTC (and vice versa) drop off / stay on the FUTURE list correctly. `pom.xml:183`
   pins the test JVM to UTC, so the test must set the entry zone explicitly for this to prove
   anything.
7. `[~]` **`@WebMvcTest` render coverage** (templates render green in the existing tests; no explicit assertion on the `<select>` or the rejection path yet) for `plan-gathering.html`, `change-gathering.html` and
   `plan-conference.html` after they gain the `<select>` (Thymeleaf errors surface only at render
   time), plus a POST that rejects on `ZoneResolutionException` and re-renders with the field error.
8. `[x]` **The four cross-zone `ScheduleGapProjector` scenarios from resolved question 1** —
   written 2026-08-05 as the `CrossZoneConflictDetection` nested class, covering all four cases
   listed there (the `DifferentCityConflict` one became writable once conferences carried zones).
   Each fixture names its own zone rather than the file's single `ZONE`, and the offset arithmetic
   is spelled out in comments. Verified by temporarily reverting the projector to wall-clock
   comparisons: three of the four fail, the same-zone control still passes.

`[ ]` Finish with the "All Tests" IDEA run configuration + `./mvnw test -Pjs-tests`, and stage all
new files for review.

## Resolved questions

1. ~~`ScheduleGapProjector` overlap semantics~~ — **resolved 2026-07-26:** instants for detection,
   entry-zone locals for reporting (decision 6, gathering step 6). Test cases to write from the
   scenarios that motivated it:
   - two gatherings whose entry-zone locals overlap but whose instants do not (Amsterdam
     19:00–20:30 CEST vs London 19:30–21:00 BST) ⇒ **no** `SchedulingConflict`;
   - two gatherings on different entry-zone dates whose instants overlap (San Francisco Oct 3
     18:00–21:00 PDT vs Tokyo Oct 4 09:00–12:00 JST) ⇒ `SchedulingConflict` reported;
   - a gathering starting before a conference ends in another zone, on a later entry-zone date
     (Tokyo Oct 8 06:00 JST vs a Chicago conference ending Oct 7 17:00 CDT) ⇒
     `DifferentCityConflict` reported;
   - a same-zone pair, to pin that the common case is unaffected.
2. ~~Backfill scope~~ — **resolved 2026-07-26:** the golden upcast path and its legacy samples for
   all five event types are built in this slice (test 3). The handler zone tests (test 1) are
   written for gathering/conference here; backfilling hotel/train/flight with them is a ride-along
   if convenient, otherwise a follow-up.
