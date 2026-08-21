# Review: UTC datetime plan & codebase (2026-08-05)

> **Historical record — fully folded into `docs/UtcDatetimeStoragePlan.md` on 2026-08-05.**
> Track progress there (bugs appear as R1–R3, plus the "Test backfill" and "Smaller design
> improvements" sections); the checkboxes below are no longer maintained.

Scope agreed with Ted: prioritize what affects the **upcoming conference slice**
(`docs/archived/GatheringConferenceUtcRolloutPlan.md`), then bugs/risks, plan-status corrections, missing
tests, and smaller design improvements. Context confirmed during review: production server runs in
**UTC**; existing backup files contain **both** `MigrateConferenceToGathering` and
`ClearDifferentCityConflict` entries; phase 4 (browser-zone display, FAMILY/ANON roles) is still
wanted **as planned**.

Checklist legend: `[ ]` open · `[x]` addressed (update as items land).

## 1. Bugs found

### 1.1 `[ ]` Internal commands return a random `commandId()` — import resumability is broken for them

`MigrateConferenceToGathering.commandId()` (`MigrateConferenceToGathering.java:41`) and
`ClearDifferentCityConflict.commandId()` (`ClearDifferentCityConflict.java:24`) both return
`UUID.randomUUID()` — a *new id on every call*. Consequences:

- **Resume duplicates events.** `CommandImporter.apply` skips entries whose id is already in
  `command_log`. These two types get a fresh id each run, so re-running a backup after a mid-apply
  failure (the advertised resume path, `CommandImporter.java:27-29`) re-appends their events:
  duplicate `ConferenceCancelled` + `GatheringPlanned`, duplicate `DifferentCityConflictCleared`.
  A duplicated migration re-cancels a conference and plans a *second* gathering with the same
  `gatheringId` — projectors keyed by id will overwrite, but the event log is polluted and any
  future exhaustive replay logic sees a history that never happened.
- **In-file duplicate detection is defeated.** `CommandImporter.validate`'s
  `firstEntryUsingId` check can never flag a duplicated migrate/clear entry in a backup file.
- The record javadoc claims it "captures everything needed to deterministically re-emit its
  events" — the command id is the one thing it does not.

**Recommended fix** (must work for existing backups, which carry no id field): derive a
**deterministic** id in `commandId()` from the payload's stable identity, e.g.
`UUID.nameUUIDFromBytes(("MigrateConferenceToGathering:" + conferenceId + ":" + gatheringId).getBytes(UTF_8))`
and `("ClearDifferentCityConflict:" + gatheringId + ":" + conferenceId + ":" + reason)`. Old and
new backups then import and *resume* identically with no wire-format change. (Adding a persisted
`commandId` record field would orphan every existing backup, so don't.)

Caveat to document with the fix: the live execution path stores the command under the id the
service passed to `CommandExecutor`; on import the derived id differs from the original
`command_log` id. That is already true today (worse — random), and ids are opaque, so the
deterministic id only needs to be stable *per backup file content*, which the derivation gives.

**Test to add:** an import-the-same-file-twice case (natural home: `CommandImportSafetyTest` or
`CommandExportImportRoundTripTest`) asserting the second run reports all entries **skipped** and
appends zero new events — with a migrate and a clear-conflict entry in the file, since those are
exactly the types that would have caught this.

### 1.2 `[ ]` `ScheduleGapProjector` still compares wall-clock across zones in missing-travel/missing-hotel

Gathering-vs-gathering conflicts now compare instants (good), but the rest of the projector
compares entry-zone `LocalDateTime`s *from different cities* against each other:

- `allLegs()` (`ScheduleGapProjector.java:274-278`) sorts legs by wall-clock departure — ordering
  two legs in different zones by numbers that denote different moments.
- `deduplicateMissingTravel`'s `next.arrivedAt().isBefore(earliestNextDep)`
  (`ScheduleGapProjector.java:116`) and `nextDepartureFromCity`'s `departure().isAfter(afterTime)`
  (`ScheduleGapProjector.java:269`) mix arrival wall-clock in one city with departure wall-clock
  in another.
- Conference gap detection (`detectMissingTravelToFromConferences`) compares leg wall-clocks with
  conference wall-clocks — currently unavoidable (conferences haven't migrated), but it should
  flip to instants in the conference slice, same as `detectDifferentCityConflicts` is documented
  to (`ScheduleGapProjector.java:292-293`).

This is the same class of bug decision 6 fixed for gathering conflicts. At day granularity the
practical impact is small (an SFO→FRA overnight flight still lands "later"), but a short
eastbound trans-Pacific hop can invert wall-clock order, and the fix is mechanical: **sequence and
compare by `utc()`; keep entry-zone locals only for night bucketing and messages** (master plan
decision 7 covers bucketing already). Recommendation: fold the leg-ordering/comparison part into
the conference slice's `ScheduleGapProjector` step 5 rather than leaving `TravelLeg` on
`LocalDateTime` — the events already carry `ZonedTimestamp`, so the data is there.

### 1.3 `[ ]` Cross-zone `SchedulingConflict` reports one gathering's date with the other's times

`detectGatheringConflicts` builds the problem from `a.startTime()/a.endTime()`,
`b.startTime()/b.endTime()` and **`a.date()`** (`ScheduleGapProjector.java:317-320`). For the
midnight-straddling case the instant-based detection was built for (SF Oct 3 evening vs Tokyo
Oct 4 morning), the message shows B's times under A's date — times that occur on a different
local day. Minor/cosmetic, but it will read as wrong exactly when the new detection does its job.
Consider carrying each gathering's own date (or full `ZonedTimestamp`s) in
`ScheduleProblem.SchedulingConflict` and letting the renderer say
"Oct 3 18:00–21:00 (San Francisco) overlaps Oct 4 09:00–12:00 (Tokyo)". Ties into phase 4's
renderer work on `ScheduleProblemsRenderer`.

## 2. Conference slice — review of the planned work

The plan itself (`docs/archived/GatheringConferenceUtcRolloutPlan.md`, conference steps 1–7) is sound and
the gathering slice proved the pattern. Points to fold in:

1. `[ ]` **Urgency note:** the server runs UTC, so `ConferenceView.relevantUntil()`'s
   stopgap (`ConferenceView.java:30`) reads a venue wall-clock as UTC. A US conference
   ending 17:00 local is treated as ending 17:00 UTC — it drops off the FUTURE list ~7–8 hours
   early. This is the *original bug of the whole plan* still live for conferences, in the
   opposite direction of "past items linger". Worth doing the slice soon.
2. `[ ]` **Reuse `ZonedTimestamp.isOnDayAfter` for the "start at least one day out" rule.** The
   existing rule is `startDate.isBefore(now.plusDays(1))` on wall-clock
   (`PlanConferenceCommand.java:11`). The gathering slice already built the
   day-granularity, entry-zone, `Instant.MIN`-sentinel-safe version (`ZonedTimestamp.java:68`).
   Re-deriving it with `now.plus(Duration.ofDays(1))` would silently change semantics ("24h out"
   vs "a later calendar day") *and* re-open the `Instant.MIN` overflow trap the javadoc warns
   about. Decide explicitly which rule conferences want; if it's "later day", it's one method call.
3. `[ ]` **Add the application-layer architecture guard in the same commit that rewrites
   `ConferencePlanning`.** `ConferencePlanning` is the last `EventStore`-injecting service
   (CLAUDE.md rule + TODO). The moment step 3 lands, add the enforcement test so it can never
   regress. A plain reflection test over `application` package constructors (in
   `src/test/java/.../architecture/`, styled like `NoFullyQualifiedClassReferencesTest`) avoids
   adding an ArchUnit dependency. Also delete the CLAUDE.md TODO when done.
4. `[ ]` **Golden conference sample must switch to the legacy path.** The current
   `conferenceTentativelyPlannedLegacyPayloadWithStateFieldDeserializes` sample
   (`GoldenEventDeserializationTest.java:97`) binds scalar `startDate`/`endDate` directly. Once
   the event holds `ZonedTimestamp`s, that sample must go through `deserializeLegacy` (path
   already exists) — and a *new-shape* conference sample should be added beside it, matching the
   other types.
5. `[ ]` **Sentinel cleanup ride-along:** after the conference command modernizes to
   `Instant now`, check whether `IMPORT_BYPASS_NOW` (`LocalDateTime.MIN`) and
   `IMPORT_BYPASS_DATE` still have any users (`ImportableCommand.java:24-25`) and delete the dead
   ones — they're exactly the kind of trap that gets grabbed by the next new command.
6. `[ ]` **Read-only parity check (plan step 3 already flags it):** `ConferencePlanning` throws
   `ReadOnlyModeException` *before* saving the command; confirm `CommandExecutor` gives the same
   guarantee (no command row written in read-only mode), since `PlanConferenceController` also
   calls `isReadOnly()` directly and will need a replacement source for that flag.
7. `[ ]` **`detectMissingTravelToFromConferences` and `CityOccupancy` move to instants** in the
   same pass as `detectDifferentCityConflicts` (see finding 1.2) — the plan's step 5 mentions
   `CityOccupancy.startDate()/endDate()` but not the leg-vs-conference comparisons around
   `ScheduleGapProjector.java:150-188`.

## 3. Plan-status corrections (the master plan is stale in places)

`docs/UtcDatetimeStoragePlan.md` should be updated — its phase 5 description no longer matches
the design that actually shipped:

- `[ ]` **"`command_log` / import path is not upcast" (line ~212) is obsolete.** The import wire
  type is the *request* (`ImportableCommandTypes` registers `BookHotelRequest` & co., not the
  domain commands). Requests deliberately kept scalar wall-clock fields plus an optional `zone`
  (gathering plan decision 5), and `events()` re-resolves zones during import validation. Old
  scalar backups import **by design, without any command-path upcaster**. The
  `CommandImporter`-needs-the-upcaster bullet and the phase-5 `[~]` rationale should be rewritten
  to describe the requests-stay-wall-clock design instead.
- `[ ]` **"Golden legacy-shape samples … missing" is done.** `GoldenEventDeserializationTest` has
  the `deserializeLegacy` upcast path and legacy samples for hotel, train, flight and gathering.
- `[ ]` **"Flight case has no upcaster unit test" is half-stale:** covered end-to-end by
  `legacyFlightBookedUpcastsEachEndpointFromItsAirportCode` in the golden test, though
  `EventPayloadUpcasterTest` itself still has no flight case (see 4.2).
- `[ ]` The status table's phase 5 row ("command_log/import upcasting; legacy-shape golden +
  round-trip tests") should shrink to what's genuinely left: legacy round-trip import cases and
  the conference upcaster (section 4).

## 4. Missing tests (beyond what the slice plan already lists)

1. `[ ]` **The four cross-zone `ScheduleGapProjector` scenarios were never written**, although
   gathering step 6 is marked `[x]` and "Resolved questions" item 1 lists them as "test cases to
   write". `ScheduleGapProjectorTest` has no cross-zone case at all — every conflict test uses
   same-zone fixtures, so the instant-based `overlapsWith` and the removal of the same-date
   precondition (the whole point of decision 6) are unpinned. Write: Amsterdam-vs-London locals
   overlap/instants don't ⇒ no conflict; SF-Oct-3-evening vs Tokyo-Oct-4-morning instants overlap
   ⇒ conflict; Tokyo gathering vs Chicago conference `DifferentCityConflict` (after the
   conference slice); same-zone pair unaffected.
2. `[ ]` **`EventPayloadUpcasterTest` flight cases** (`FlightBooked`/`FlightChanged` via
   `AirportZoneResolver`) — currently only hotel/train/gathering; the golden test covers
   `FlightBooked` but nothing covers `FlightChanged` through the upcaster anywhere.
3. `[ ]` **Golden legacy sample for `GatheringChanged`** — the upcaster handles it, but only
   `GatheringPlanned` has a legacy golden sample; `GatheringChanged` carries the same trio and
   would fail identically if a key were missed.
4. `[ ]` **Legacy zone-less command import round-trip** (slice test 4, still open): a gathering
   and a conference command JSON *without* the `zone` field must import and produce the right
   instants. Inline JSON text blocks per the under-30-lines convention.
5. `[ ]` **`MigrateConferenceToGathering.events()` direct test** (slice test 5, still open) —
   zone resolved from its own location; plus the new determinism assertion once 1.1 is fixed:
   `commandId()` returns the same value on every call.
6. `[ ]` **Import-twice/resume test** for the internal commands (pins bug 1.1; see there).
7. `[ ]` **Handler zone-rule backfill for hotel/train/flight** (slice test 1 ride-along, still
   open): explicit `CommonZone` wins / derive from address / unresolvable+no-pick throws — the
   pattern now exists in `PlanGatheringHandlerTest`.
8. `[ ]` **`@WebMvcTest` assertions on the zone `<select>` and the `ZoneResolutionException`
   re-render** (slice test 7, `[~]`) for the gathering forms now, conference form when it gains
   the selector.
9. `[ ]` **Phase 4 `js`-tier tests** stay as specced (two pinned `timezoneId` contexts; toggle
   interaction) — confirmed still wanted as planned.

## 5. Design & code improvements (smaller, non-blocking)

1. `[ ]` **`events()` implementations construct their own `LocationZoneResolver`**
   (`BookHotelRequest.java:81`, `MigrateConferenceToGathering.java:46`, and siblings). Fine while
   the resolver is a stateless, dependency-free table, but it means import validation cannot be
   exercised with a stub resolver and every site goes stale if the resolver ever gains
   configuration (e.g. the ISO-code aliases below). If that happens, thread the resolver through
   `events(...)` (or an import context parameter) in one sweep — noting the interface change
   touches all eleven implementations. Not worth doing preemptively; worth writing down.
2. `[ ]` **ISO alpha-2 country aliases for single-zone countries.** The gathering slice's
   watch-out ("a manually-typed `GB` does not resolve") already bit test fixtures; hotel golden
   samples store `"country": "GB"` — evidence real data can carry codes. Adding aliases for
   single-zone countries only (never multi-zone) is cheap insurance and keeps the strict-no-default
   promise. Re-run `/admin/zone-audit` after (it must be re-run before the conference deploy
   anyway, per the slice plan's backward-compat section).
3. `[ ]` **`ConferenceProjectorTest` lives in `web/`** while the projector is in
   `application/` — move it beside its peers when the conference slice touches it.
4. `[ ]` **`EventSourcingConfig` projector wiring** repeats the subscribe-then-replay triple
   fifteen times; a small private helper (`wire(projector)`) would collapse it without any Spring
   cleverness. Cosmetic; do it opportunistically.
5. `[ ]` **`CommonZone` coverage** remains USA/Canada/UK/CET while itineraries include Japan —
   the existing "promote the picker" follow-up in the master plan stands; no action now, but the
   conference form should reuse whatever list exists rather than fork it.

## 6. Suggested sequencing

1. Fix bug 1.1 (deterministic `commandId`) + its resume test — small, standalone, protects every
   backup you make between now and the conference deploy.
2. Conference slice per its plan, folding in section 2 items (instant comparisons in
   `ScheduleGapProjector` conference paths, architecture guard, golden legacy sample, sentinel
   cleanup) and the cross-zone scenario tests from 4.1.
3. Plan-status corrections (section 3) — five minutes, prevents the next session from
   re-investigating phase 5.
4. Test backfill batch (4.2–4.8) — mechanical, pattern exists.
5. Phase 4 display work as planned, picking up 1.3's renderer tweak.
