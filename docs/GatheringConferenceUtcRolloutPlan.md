# Plan: Roll UTC + zone storage out to Gatherings and Conferences

Third and final slice of `docs/UtcDatetimeStoragePlan.md` phase 2. Hotels, trains and flights already
ship the pattern (`ZonedTimestamp` events, boundary zone resolution, read-time upcaster, `<time>`
rendering); see `docs/TrainFlightUtcRolloutPlan.md` for the previous slice. Gatherings and
conferences are the only event types still storing bare wall-clock times.

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done. Nothing here is started yet.

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

1. `[ ]` **Domain** — `GatheringPlanned`, `GatheringChanged`: replace
   `LocalDate date, LocalTime startTime, LocalTime endTime` with
   `ZonedTimestamp startsAt, ZonedTimestamp endsAt`. Same field change on `PlanGatheringCommand` and
   `ChangeGatheringCommand`. Move validation onto instants/entry-zone per `BookHotelCommand`:
   - future check → `startsAt.utc().isAfter(context.now())` (`GatheringDateNotInFuture`);
   - end-after-start → `endsAt.utc().isAfter(startsAt.utc())` (`InvalidGatheringTimeRange`);
   - "same day" is an *entry-zone* check (`startsAt.localDateTime().toLocalDate()`), never UTC.
2. `[ ]` **Contexts** — `GatheringPlanningContext(LocalDate today)` →
   `GatheringPlanningContext(Instant now)`; same for `ChangeGatheringContext`. `GatheringPlanning`
   and `ChangeGathering` take `Instant now` instead of `LocalDate today`; controllers pass
   `Instant.now(clock)`.
3. `[ ]` **Handlers** — `PlanGatheringHandler` and `ChangeGatheringHandler` take a
   `LocationZoneResolver` (they currently take no dependencies), resolve the venue zone once
   (explicit `CommonZone` wins, else `resolve(location)`, else `ZoneResolutionException`), and build
   both `ZonedTimestamp`s from `date.atTime(startTime)` / `date.atTime(endTime)` in that zone.
4. `[ ]` **Views** — `PlannedGatheringView` holds `ZonedTimestamp startsAt/endsAt`;
   `relevantUntil()` returns `endsAt.utc()`, removing the documented server-zone STOPGAP.
   `GatheringDetailsView` likewise, so the edit form prefills from `atEntryZone()`.
5. `[ ]` **Projectors** — `PlannedGatheringsProjector`, `GatheringDetailsViewProjector`,
   `GatheringCalendarProjector` (`date.atTime(startTime)` → `startsAt.localDateTime()`),
   `GatheringItineraryEntry` + `ItineraryProjector`.
6. `[ ]` **`ScheduleGapProjector`** — `GatheringOccupancy` currently overlaps by comparing
   `LocalTime`s on an equal `LocalDate`, and `detectDifferentCityConflicts` compares a gathering's
   `LocalDate` against a conference's date range. **Recommendation: compare instants**
   (`a.startsAt().utc().isBefore(b.endsAt().utc()) && ...`), which is correct across zones and needs
   no same-day precondition; keep *day bucketing* for display in the entry zone (decision 7 of the
   master plan). Open for review — see "Open questions".
7. `[ ]` **Web** — `PlanGatheringRequest`/`ChangeGatheringRequest` keep `date`/`startTime`/`endTime`
   and gain an optional `zone` (a `CommonZone` name); `events()` passes a real
   `LocationZoneResolver`. Controllers add `@ModelAttribute("commonZones")` and a
   `ZoneResolutionException` catch that rejects the `zone` field. `plan-gathering.html` and
   `change-gathering.html` gain the selector block (copy `book-hotel.html:256-270`); the edit form
   preselects the stored zone.
8. `[ ]` **`MigrateConferenceToGathering`** — this `ImportableCommand` record builds
   `GatheringPlanned` **directly**, bypassing the handler, and appears in existing backups. Resolve
   the zone from its own `location` inside `events()` rather than adding a record field: old backups
   stay importable, and the work stays in `events()` where it runs during import validation (per
   `CLAUDE.md`). `ConferenceMigrationService` correspondingly reads
   `conference.startsAt().atEntryZone()` when destructuring into date/start/end.
9. `[ ]` **Renderer** — `PlannedGatheringsRenderer` renders each time via
   `ZonedTimeTag.render(view.startsAt(), "…")`, matching the booked-list renderers.
10. `[ ]` **Upcaster** — add `"GatheringPlanned"`/`"GatheringChanged"` cases: resolve the zone from
    `location.{city,country}`, merge `date`+`startTime` → `startsAt` and `date`+`endTime` → `endsAt`,
    and **remove** the three legacy keys. Idempotent when `startsAt` is already present.

## Conference rollout steps

1. `[ ]` **Domain** — `ConferenceTentativelyPlanned`: `startDate`/`endDate`
   `LocalDateTime → ZonedTimestamp` (both in the venue's single zone).
2. `[ ]` **Modernization (decision 2)** — turn `PlanTentativeConferenceCommand` into a
   `record … implements DomainCommand<PlanTentativeConferenceContext>`:
   - new `PlanTentativeConferenceContext(Instant now)` (mirrors `BookHotelContext`);
   - new `PlanTentativeConferenceHandler(LocationZoneResolver)` mapping request → command, resolving
     the venue zone (explicit `CommonZone` wins, else `resolve(venueAddress)`);
   - `execute(context)` keeps the existing rules on instants: start at least one day out
     (`DateRangeNotInFuture`), end on/after start (`InvalidDateRange`);
   - the command no longer imports `PlanTentativeConferenceRequest` — the domain stops depending on
     the web package.
3. `[ ]` **`ConferencePlanning` must go through `CommandExecutor`.** It currently injects
   `EventStore` + `PostgresPersister` and calls `persister.saveCommand(...)` then
   `eventStore.append(...)` by hand — a direct violation of the architecture rule in `CLAUDE.md`
   ("Application services must never receive `EventStore`"). Rewrite it to mirror `HotelBooking`:
   `commandExecutor.execute(conferenceId, request, context, command)`, with `Instant now` passed in
   from the controller. It also drops its own `isReadOnly()` clock/read-only handling in favor of
   whatever `CommandExecutor` already enforces (check: `PlanConferenceController` calls
   `applicationService.isReadOnly()` in two places).
4. `[ ]` **View** — `TentativeConferenceView` holds `ZonedTimestamp startDate/endDate`;
   `relevantUntil()` returns `endDate.utc()`, removing its STOPGAP.
5. `[ ]` **Projectors** — `TentativeConferenceProjector` (the single-day filter at line 48 becomes an
   entry-zone `toLocalDate()` comparison), `ConferenceCalendarProjector`, `ConferenceItineraryEntry`
   + `ItineraryProjector`, and `ScheduleGapProjector`'s `CityOccupancy.startDate()/endDate()`.
6. `[ ]` **Web** — `PlanTentativeConferenceRequest` keeps `startDate`/`endDate` as `LocalDateTime`
   and gains an optional `zone`; `events()` calls the new handler with a real `LocationZoneResolver`.
   `PlanConferenceController` gains `@ModelAttribute("commonZones")` and a `ZoneResolutionException`
   catch; `plan-conference.html` gains the selector block. `TentativeConferencesRenderer` moves to
   `ZonedTimeTag`.
7. `[ ]` **Upcaster** — add a `"ConferenceTentativelyPlanned"` case: resolve from
   `venueAddress.{city,country}`, rewrite both scalars in place (the simple hotel-style rewrite).

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

1. `[ ]` **Handler zone tests** (`PlanGatheringHandlerTest`, `ChangeGatheringHandlerTest`,
   `PlanTentativeConferenceHandlerTest`): an explicit `CommonZone` wins over the address; an absent
   pick derives from the address; an unresolvable address with no pick throws
   `ZoneResolutionException`. **These rules are currently untested for every migrated type** — there
   is no `BookHotelHandlerTest`/`BookTrainHandlerTest`, and `BookHotelControllerValidationTest` only
   pins a zone to sidestep ambiguity. Backfilling hotel/train/flight is cheap once the pattern
   exists and should ride along.
2. `[ ]` **`EventPayloadUpcasterTest` cases** for `GatheringPlanned`/`GatheringChanged` (three keys →
   two, legacy keys removed, idempotent on new-shape input) and `ConferenceTentativelyPlanned`.
   While here: the flight cases added in `d2884fb` have **no upcaster test at all** — add them.
3. `[ ]` **Golden legacy coverage** — give `GoldenEventDeserializationTest` an
   upcast-then-deserialize path and add legacy-shape samples for gathering and conference (and,
   retroactively, hotel/train/flight, which lost theirs). Keep the new-shape samples too.
4. `[ ]` **`CommandExportImportRoundTripTest`** — a zone-less legacy gathering command and a
   zone-less legacy conference command must both import and produce the right instants.
5. `[ ]` **`MigrateConferenceToGathering.events()`** — direct test that it resolves a zone from its
   location (today it is only exercised indirectly via `ConferenceMigrationServiceTest`).
6. `[ ]` **Zone-skew FUTURE filter** — a gathering and a conference whose end has passed in the entry
   zone but not in UTC (and vice versa) drop off / stay on the FUTURE list correctly. `pom.xml:183`
   pins the test JVM to UTC, so the test must set the entry zone explicitly for this to prove
   anything.
7. `[ ]` **`@WebMvcTest` render coverage** for `plan-gathering.html`, `change-gathering.html` and
   `plan-conference.html` after they gain the `<select>` (Thymeleaf errors surface only at render
   time), plus a POST that rejects on `ZoneResolutionException` and re-renders with the field error.

`[ ]` Finish with the "All Tests" IDEA run configuration + `./mvnw test -Pjs-tests`, and stage all
new files for review.

## Open questions

1. **`ScheduleGapProjector` overlap semantics** (gathering step 6): switch conflict detection to
   instant comparison, or keep entry-zone local comparison? Instants are correct across zones;
   entry-zone locals preserve exactly today's behavior. Recommendation: instants.
2. **Backfill scope** — do the hotel/train/flight handler tests and golden legacy samples (tests 1
   and 3) ride along with this slice, or land as a separate cleanup commit afterwards?
