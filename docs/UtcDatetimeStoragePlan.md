# Plan: Store datetimes as UTC + zone, evaluate by instant, display per viewer role

## Status at a glance (updated 2026-08-05; checkboxes re-verified against the tree 2026-08-06)

Legend: `[x]` done · `[~]` partially done · `[ ]` not started.

The 2026-08-05 review (`docs/UtcDatetimePlanReview.md`) has been **fully folded into this plan** —
its bugs, stale-status corrections, missing tests and improvements now live here (section
references like "review 1.1" point back to the fuller write-ups). This document is the single
tracker; the review doc is a historical record and its checkboxes are no longer maintained.

| Phase | State | What's left |
|-------|-------|-------------|
| 1. Value type + resolver | `[x]` | — |
| 2. Events & commands → `ZonedTimestamp` | `[x]` | — (all five types migrated) |
| 3. Evaluation by instant | `[x]` | — (the conference stopgap is gone) |
| 4. Display | `[x]` | — (all five renderers on `<time>`; browser-zone script, role switch, ANON toggle and the `js` tier all landed 2026-08-05) |
| 5. Backward compatibility | `[x]` | — (round-trip and golden/upcaster gaps all landed; verified 2026-08-06) |
| 6. Conventions & other consumers | `[x]` | — (no iCal/GCal export exists → n/a) |
| 7. Full test pass | `[x]` | green 2026-08-05: "All Tests" (670, 0 failures) + `./mvnw test -Pjs-tests` |
| R. Review fixes (2026-08-05) | `[x]` | R1 **dropped as not applicable**; R2 **done**; R3 **done** |

**Next up:**

Every phase is complete. The phase 5 items still shown open on 2026-08-05 were found already done
when the plan was checked against the tree on 2026-08-06 — the flight and conference upcaster
cases, the `GatheringChanged` legacy golden and the zone-less command round-trip all exist.

What is left is *not* migration work: it is the non-blocking improvements list below.

**2026-08-06, found in production:** importing the real command backup failed on three
conferences — Lone Tree CO, North Kawartha ON, North Gower ON. Fixed by adding a
**state/province step** to `LocationZoneResolver` (see improvement 2); the remaining piece of that
item is alpha-2 aliases for *single-zone* countries (`GB`).

**Before the next deploy:** re-run `/admin/zone-audit` (phase 5).

## Context

The "upcoming" filter on booked lists shows items already in the past. Root cause: event
datetimes are stored as zoneless `LocalDateTime` (wall-clock), but the FUTURE/past boundary
compares them against `LocalDateTime.now()` resolved in the *server's* default zone
(`BookedHotelsController.java:30`, `TimeView.java:9`). When the server zone differs from where the
entry happens, the boundary is off by the offset and past items linger — the normal case for a
travel app. (Production runs in **UTC**, so for US locations the conference stopgap errs in the
opposite direction: items drop off early. Both are the same disease.)

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
9. **(2026-07-26, gathering slice)** The command/request **wire shape does not change** — requests
   keep their scalar wall-clock fields and gain only an optional `zone`. This is what keeps every
   pre-migration backup importable *without any command-path upcaster* (see phase 5).

   **What this means for a backup file (written up 2026-08-06):** the `zone` field is the user's
   explicit `CommonZone` **pick**, not the zone that was resolved. So:

   - **`"zone": null` in an export is normal and correct**, not a defect. `command_log` stores the
     serialized request (`PostgresPersister.saveCommand`) and export echoes that payload verbatim
     (`CommandImporter.toExportEntry`), so a null field is written out as an explicit null. It
     means "no pick — derive from the location", which is exactly how a *missing* key behaves
     (`CommonZone.fromParam(null)` returns null). Pre-migration files have no `zone` key at all;
     both import identically. Every entry showing null just means location resolution covered
     everything and the picker was never needed.
   - **A backup does not pin the instant.** Import re-derives the zone from the location through
     the *current* resolver tables, so the same file can produce different instants after a
     resolver change. That is the deliberate trade for wire-format stability, and it is what let
     the 2026-08-06 state/province fix rescue an unimportable file — but it cuts both ways.
     (That fix was purely additive: the region step only fires for USA/Canada/Australia, none of
     which has a country-table entry, so every location that resolved before resolved by city and
     still does.)
   - **If pinning is ever wanted**, it is a real design change — store the *resolved* zone on the
     request and have import prefer it over re-deriving — and it changes the wire format, so it
     needs a backward-compatibility decision first. Not planned.

10. **(2026-08-16) "Today" is viewer-zoned, separately from display.** Decisions 4 and 7 govern
    how *entry* times are shown and bucketed; they were silent on the calendar/itinerary's notion
    of **today** — which day is highlighted, which past weeks collapse, and the default calendar
    range. That "today" was being computed in the server's JVM zone (UTC on Railway), so it flipped
    a day early each evening in the Pacific. It is now computed in the **viewer's** zone:

    - `ViewerTodayZone` resolves a `ZoneId` from a `viewerZone` cookie, else a configured fallback
      (`jittertravel.today.fallback-zone`, default `America/Los_Angeles`). Controllers compute
      `LocalDate.ofInstant(clock.instant(), resolvedZone)`.
    - The server cannot read a browser's zone on its own (the browser-zone display upgrade of
      decision 4/8 is client-side only). So a **custom login page** (replacing Spring's generated
      one) carries a hidden `browserZone` field that JS fills; on login success,
      `ZoneCapturingAuthenticationSuccessHandler` writes the `viewerZone` cookie on the very
      response that redirects to the originally-requested page — so a deep link bounced through
      login shows the correct today on first paint. The cookie is written **only** on an
      authenticated response, so anonymous visitors never receive one (no cookie-consent surface);
      they get the fallback zone, and since they are redacted to day granularity this is
      inconsequential. OWNER, who ships no browser-zone *display* script (decision 8), still gets
      this one hidden field at login — the two mechanisms are independent.
    - **Consequence to keep in mind:** today is now viewer-zoned while day *bucketing* stays
      entry-zoned (decision 7 is unchanged). Near a midnight boundary the highlighted "today" can
      therefore sit one column away from an entry's own entry-zone day. Accepted, like the
      decision-5 mixed-zone ambiguity.
    - Same change: the calendar's default `from` is now **one week before today** (was: anchored to
      the earliest entry), so the calendar opens near now rather than scrolled back into history.

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
- `HotelBooked`/`HotelChanged`: checkIn, checkOut (same zone). — done
- `FlightBooked`/`FlightChanged`: departure, arrival (independent zones). — done
- `TrainBooked`/`TrainChanged`: departure, arrival (independent zones). — done
- `GatheringPlanned`/`GatheringChanged`: `date`+`startTime`/`endTime` collapsed into
  `startsAt`/`endsAt` `ZonedTimestamp`s. — done (2026-07-27)
- `ConferencePlanned`: startDate, endDate. — done (2026-08-05)

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

## Bugs found in the 2026-08-05 review

### R1. ~~Internal commands return a random `commandId()`~~ — **dropped 2026-08-05, not applicable**

Implemented, then reverted the same day once its premise was checked against the actual operating
workflow. **Ted never re-imports a backup onto existing data — he deletes the database and imports
once.** `commandId()` is called exactly once per entry per run (`CommandImporter.prepare`), and it
is called from *nowhere else*: live paths supply their own id at the boundary
(`ConferenceMigrationService` passes a fresh `UUID.randomUUID()`; `GatheringPlanning.clearConflict`
takes one as a parameter), and `findAllCommandsForExport` never reads it — the export deliberately
drops the `command_id` column, which is the whole reason `commandId()` exists. So a single import
run is internally consistent and the duplication described below **cannot occur in practice**.

The same reasoning retires the parallel concern about the four `Change*Request` types, which return
`UUID.randomUUID()` with a comment calling it deliberate: same non-issue. (Their duplicates would
also be inert — every `*Changed` projection is a last-write-wins `put`.)

Kept from the reverted work: `MigrateConferenceToGatheringTest`, whose value is unrelated (zone
re-derived from the payload's own location; unresolvable location throws during validation) — that
is test backfill item 5.

**Revisit only if** the wipe-then-import workflow changes, or if `CommandImporter`'s advertised
"import is resumable" behavior ever needs to be true for these two types. The original analysis
follows, unchanged, for that case.

`MigrateConferenceToGathering.commandId()` (`MigrateConferenceToGathering.java:41`) and
`ClearDifferentCityConflict.commandId()` (`ClearDifferentCityConflict.java:24`) both return
`UUID.randomUUID()` — a *new id on every call*. Consequences (review 1.1 has the full analysis):

- **Resume duplicates events.** `CommandImporter.apply` skips entries whose id is already in
  `command_log`; these two types get a fresh id each run, so re-running a backup after a mid-apply
  failure re-appends their events — duplicate `ConferenceCancelled` + `GatheringPlanned` (a second
  gathering under the same `gatheringId`), duplicate `DifferentCityConflictCleared`. Existing
  backup files **do** contain both entry types.
- The in-file duplicate check (`CommandImporter.validate`'s `firstEntryUsingId`) can never flag a
  duplicated migrate/clear entry.
- The record javadoc's "deterministically re-emits its events" claim is false for the id.

**Fix** (must work for existing backups, which carry no id field): derive a **deterministic** id
in `commandId()` from the payload's stable identity, e.g.
`UUID.nameUUIDFromBytes(("MigrateConferenceToGathering:" + conferenceId + ":" + gatheringId).getBytes(UTF_8))`
and `("ClearDifferentCityConflict:" + gatheringId + ":" + conferenceId + ":" + reason)`. Old and
new backups then import and *resume* identically with no wire-format change. Do **not** add a
persisted `commandId` record field — that would orphan every existing backup. Document the caveat:
on import the derived id differs from the original `command_log` id from live execution; that is
already true today (worse — random), and ids are opaque, so per-file-content stability suffices.

**Payload-collision caveat (2026-08-05 verification):** deriving the id from payload content means
two entries with *identical payloads* derive the same id, and `CommandImporter.validate`'s
`firstEntryUsingId` check would then reject the **entire file** as a duplicate-id error — a
legitimately exported backup failing to import at all. `MigrateConferenceToGathering` is immune
(the generated `gatheringId` is in the key). `ClearDifferentCityConflict`'s key
`(gatheringId, conferenceId, reason)` is safe only because `ScheduleGapProjector`'s
`clearedConflicts` hides a cleared pair forever, so the UI can never emit a second clear for the
same pair — that invariant is load-bearing; if re-clearing ever becomes possible, revisit (e.g.
treat identical-payload duplicates as a benign skip instead of an error, which is strictly more
robust anyway).

**Test:** import-the-same-file-twice (home: `CommandImportSafetyTest` or
`CommandExportImportRoundTripTest`) asserting the second run reports every entry **skipped** and
appends zero events — with a migrate and a clear-conflict entry in the file, since those are the
types that would have caught this. Include two clear-conflict entries for *different* pairs to pin
that distinct payloads keep distinct ids (see the payload-collision caveat above). Plus a
determinism assertion (`commandId()` returns the same value on every call) in the
`MigrateConferenceToGathering.events()` test (test backfill item 5).

### R2. `[x]` `ScheduleGapProjector` still compares wall-clock across zones outside gathering conflicts

**Fixed 2026-08-05** with the conference slice. `TravelLeg` and `CityOccupancy` hold
`ZonedTimestamp`s; `allLegs()` sequences by `utc()`; `deduplicateMissingTravel`,
`nextDepartureFromCity` and `detectMissingTravelToFromConferences` compare `utc()`; and
`detectDifferentCityConflicts` is now a true instant overlap rather than a local-date range test —
so a Tokyo morning gathering overlapping the last afternoon of a Chicago conference is finally
detected. `ScheduleProblem.MissingTravel` carries `ZonedTimestamp`s (the dedup needs instants to
compare); night bucketing and rendered text stay entry-zone local, per decision 7. Renderers read
`localDateTime()`. Original analysis follows.

Gathering-vs-gathering conflicts compare instants (gathering slice, done), but the rest of the
projector compares entry-zone `LocalDateTime`s *from different cities* against each other:

- `allLegs()` (`ScheduleGapProjector.java:274-278`) sorts legs by wall-clock departure — ordering
  two legs in different zones by numbers that denote different moments.
- `deduplicateMissingTravel`'s `next.arrivedAt().isBefore(earliestNextDep)`
  (`ScheduleGapProjector.java:116`) and `nextDepartureFromCity`'s `departure().isAfter(afterTime)`
  (`ScheduleGapProjector.java:269`) mix arrival wall-clock in one city with departure wall-clock in
  another.
- `detectMissingTravelToFromConferences` compares leg wall-clocks with conference wall-clocks —
  unavoidable until conferences migrate, then it flips to instants, same as
  `detectDifferentCityConflicts` (`ScheduleGapProjector.java:292-293`).

Same class of bug decision 6 of the gathering slice fixed for conflicts. At day granularity the
practical impact is small, but a short eastbound trans-Pacific hop can invert wall-clock order, and
the fix is mechanical: **sequence and compare by `utc()`; keep entry-zone locals only for night
bucketing and messages** (decision 7 covers bucketing). **Do this inside the conference slice's
`ScheduleGapProjector` step 5** rather than leaving `TravelLeg` on `LocalDateTime` — the events
already carry `ZonedTimestamp`, so the data is there. `CityOccupancy.startDate()/endDate()` moves
to instants in the same pass.

### R3. `[x]` Cross-zone `SchedulingConflict` reports one gathering's date with the other's times

**Fixed 2026-08-05** with the phase 4 renderer work. `ScheduleProblem.SchedulingConflict` now
holds two `ConflictingGathering`s — each with its **own** name, city and start/end
`ZonedTimestamp`s — instead of two pairs of `LocalTime`s under one shared `LocalDate`. The
renderer emits each side as its own `<time>` pair plus its city:
"Sat, Oct 3, 6:00 PM–9:00 PM (San Francisco) overlaps Sun, Oct 4, 9:00 AM–12:00 PM (Tokyo)".
Pinned by `ScheduleProblemsRendererTest.crossZoneSchedulingConflictShowsEachGatheringsOwnDateAndCity`
and `ScheduleGapProjectorTest.eachConflictingGatheringCarriesItsOwnLocalDateAndCity`, both
mutation-verified (rebuilding side B from side A's timestamps fails them). Original analysis:
`detectGatheringConflicts` built the problem from `a.startTime()/a.endTime()`,
`b.startTime()/b.endTime()` and **`a.date()`**. For the midnight-straddling case the instant-based
detection exists for (SF Oct 3 evening vs Tokyo Oct 4 morning), the message showed B's times under
A's date — times that occur on a different local day. Cosmetic, but it read as wrong exactly when
the new detection did its job.

## Implementation phases

### 1. Value type + resolver — `[x]` done
Add `ZonedTimestamp` (domain), `LocationZoneResolver` (+ city/country table, throws
`ZoneResolutionException` on a miss — no default), `CommonZone` enum. Unit-test the resolver
directly (per renderer/services testing convention).

Shipped: `ZonedTimestamp`, `CommonZone`, `LocationZoneResolver`, `AirportZoneResolver`,
`StationZone`, `FlightEndpointZone`, `ZoneResolutionException`, each with unit tests.

### 2. Events & commands → `ZonedTimestamp` — `[x]` all five types migrated
Remaining work is planned in detail in `docs/GatheringConferenceUtcRolloutPlan.md` (previous slice:
`docs/TrainFlightUtcRolloutPlan.md`).

- `[x]` Hotel (`f35b7d6`), train (`daa7107`), flight (`d2884fb`): events, commands, contexts,
  requests and controllers all carry `ZonedTimestamp`.
- `[x]` Gathering (2026-07-27, `ddf4ba8`): `GatheringPlanned`/`GatheringChanged` collapsed to
  `startsAt`/`endsAt`; commands, contexts, handlers, views, projectors, web forms, upcaster and
  tests all landed. Renderer `<time>` treatment deferred to phase 4 with the other list views.
- `[x]` **Conference** (2026-08-05): `ConferencePlanned` on `ZonedTimestamp`,
  `PlanConferenceCommand` a `DomainCommand` record with a new context and handler, the
  `ConferencePlanning`→`CommandExecutor` rewrite, view/projectors/web/upcaster, plus every
  ride-along below. **Behavior change:** the "start at least one day out" rule is now "a later
  calendar day at the venue" (`isOnDayAfter`, matching gatherings) rather than 24 wall-clock hours,
  so a conference starting tomorrow morning is accepted where it used to be rejected.

**Ride-alongs to fold into the conference slice** (from review §2 — do these in the same slice,
they each anchor to a step that is being rewritten anyway):

- `[x]` **"Start at least one day out" reuses `ZonedTimestamp.isOnDayAfter`** (decided and done
  2026-08-05): the rule is now "a later calendar day at the venue", matching gatherings. Deliberate
  behavior change — a conference under 24h out but starting tomorrow is now accepted. Original note: The existing
  rule is `startDate.isBefore(now.plusDays(1))` on wall-clock
  (`PlanConferenceCommand.java:11`). The gathering slice built the day-granularity,
  entry-zone, `Instant.MIN`-sentinel-safe version (`ZonedTimestamp.java:68`). Re-deriving it with
  `now.plus(Duration.ofDays(1))` would silently change semantics ("24h out" vs "a later calendar
  day") *and* re-open the `Instant.MIN` overflow trap its javadoc warns about. Decide explicitly
  which rule conferences want; if it's "later day", it's one method call.
- `[x]` **Application-layer architecture guard added** 2026-08-05:
  `ApplicationServicesUseCommandExecutorTest` (plain reflection, `CommandExecutor` itself excluded
  as the authorized holder). CLAUDE.md TODO deleted. Original note: `ConferencePlanning` is the last `EventStore`-injecting service (the
  CLAUDE.md rule + TODO). The moment the rewrite lands, add the enforcement test so it can never
  regress: a plain reflection test over `application`-package constructors (in
  `src/test/java/.../architecture/`, styled like `NoFullyQualifiedClassReferencesTest`) — no
  ArchUnit dependency needed. Delete the CLAUDE.md TODO when done.
- `[x]` **Read-only guard moved into `CommandExecutor`** 2026-08-05: `execute` and `appendEvents`
  both throw `ReadOnlyModeException` before `saveCommand`, pinned by two `CommandExecutorTest`
  cases; `PlanConferenceController` reads the flag through `ConferencePlanning.isReadOnly()`.
  Per-controller `isReadOnly()` boilerplate elsewhere is now redundant and can be dropped
  opportunistically. Original analysis (parity did *not* hold): `ConferencePlanning` throws `ReadOnlyModeException` *before* saving the command, but
  `CommandExecutor.execute()`/`appendEvents()` call `persister.saveCommand(...)` unconditionally —
  no read-only check anywhere in the executor, and `EventStore.append()` doesn't check either (it
  only *sets* the flag on persist failure). The guard exists purely as controller discipline
  (each controller checks `service.isReadOnly()` before posting). Read-only mode can engage while
  the database is still *writable* — startup replay can fail for a data reason — so a forgotten
  controller check would write command rows. Migrating `ConferencePlanning` to `CommandExecutor`
  as-is would therefore *weaken* its guarantee. **Fix:** throw `ReadOnlyModeException` at the top
  of both `execute` and `appendEvents` (which also correctly blocks imports in read-only mode),
  add a test, and later drop the per-controller boilerplate opportunistically.
  `PlanConferenceController` also calls `isReadOnly()` directly and needs a replacement source for
  that flag (`CommandExecutor.isReadOnly()`, as the other services already delegate).
- `[x]` **Sentinel cleanup** done 2026-08-05: `IMPORT_BYPASS_NOW` and `IMPORT_BYPASS_DATE` had no
  users left and are deleted; only `IMPORT_BYPASS_INSTANT` remains. Original note: delete the dead ones — they're exactly the kind of trap the
  next new command grabs.
- `[x]` **`ScheduleGapProjector` moved to instants** (bug R2) — done 2026-08-05, and wider than
  just the conference paths; see R2. Original note: in the same pass as
  `detectDifferentCityConflicts` — the slice's step 5 mentions `CityOccupancy` but not the
  leg-vs-conference comparisons around `ScheduleGapProjector.java:150-188`.
- `[x]` **Golden conference sample switched to the legacy path** (see phase 5) — done 2026-08-05.

Original spec, still authoritative for the conference type:
- Change events + commands/contexts. Web requests keep binding `datetime-local` wall-clock as
  `LocalDateTime` (`@DateTimeFormat` stays) and gain an optional `CommonZone` selector. At the
  boundary, per endpoint: if a `CommonZone` was chosen, use it; otherwise call `resolve(address)`.
  If that throws `ZoneResolutionException` and no `CommonZone` was supplied, **command validation
  fails** and the form re-renders *requiring* a `CommonZone` selection. Then build
  `ZonedTimestamp`(s), capture `Instant.now()`.
- **Validations run in the entry zone**, not UTC: checkout-after-checkin
  (`BookHotelCommand.java:21`), conference start-before-end, and not-in-the-past comparisons use
  `utc.atZone(zone).toLocalDate()` / instant comparison — never UTC dates (that would reintroduce
  a midnight-boundary bug).
- **Edit forms prefill** `datetime-local` from `utc.atZone(entryZone)`. (The gathering slice
  settled the picker behavior: prefill the entry-zone wall-clock and leave the picker on "derive
  from location", matching the hotel edit form — not "preselect the stored zone".)

### 3. Evaluation — `[x]` done 2026-08-05 (the conference stopgap is gone)
`TemporalView.relevantUntil()` returns `Instant`; views derive it from their end `ZonedTimestamp`.
`TimeView.includes(...)` compares `Instant`s; the five list controllers pass `Instant.now()`.

- `[x]` `TemporalView`/`TimeView` on `Instant`, controllers pass `Instant.now()`, `TimeViewTest`
  updated (`5dab535`).
- `[x]` `PlannedGatheringView.relevantUntil()` returns `endsAt.utc()` (gathering slice).
- `[x]` `ConferenceView.relevantUntil()` returns `endDate.utc()` (2026-08-05); the
  `systemDefault()` stopgap is gone, and with it the last live instance of this plan's original
  bug. `ConferenceProjectorTest` pins the FUTURE filter against a venue zone
  (America/Los_Angeles) rather than the UTC-pinned test JVM.

### 4. Display — `[x]` done 2026-08-05
- `[x]` `ZonedTimeTag` helper emitting `<time datetime="…Z" data-fmt="…">`, used by
  `BookedHotelsRenderer`, `BookedTrainsRenderer`, `BookedFlightsRenderer` (`a97e96e`, `7b31d6d`).
- `[x]` Same `<time>` treatment everywhere: `ConferencesRenderer` (conference slice),
  then `PlannedGatheringsRenderer`, `ScheduleProblemsRenderer`, `ItineraryRenderer` and
  `CalendarViewBuilder` (2026-08-05).
  - The itinerary needed the entry records to stop flattening to wall-clock:
    `Flight`/`Train`/`Hotel`/`GatheringItineraryEntry` now hold `ZonedTimestamp`s and
    `ItineraryProjector` passes them straight through, with `anchorTime()` still returning the
    entry-zone `LocalDateTime` that does the day bucketing. `eachFlightEndpointKeepsItsOwnZoneAndInstant`
    and its train twin pin the pass-through — a gap the renderer unit tests could not see,
    found by mutation-testing the projector.
  - The calendar needed structured subtitles: `CalendarEntry.subTitle()` is now a
    `List<SubtitleLine>` (`Text` / `At` / `Range`) instead of pre-formatted strings, so the
    flight and train lanes keep their moments through to the renderer. Rippled through all five
    calendar projectors, `CalendarEntryRedactor` and their tests.
- `[x]` Bug R3's fix landed with the `ScheduleProblemsRenderer` work — see R3 above.
- `[x]` Browser-zone upgrade script (`BrowserZoneScript`), role switch (`ViewerZonePolicy` →
  `ZoneDisplay`), and the anonymous `?tz=` toggle (`ZoneToggle`, CSS in `site.css`), wired into
  `/itinerary` and `/calendar` — the two routes FAMILY and ANONYMOUS can reach. Booking lists are
  OWNER-only, so they stay entry-local with no script, per decision 4.
  - `DisplayZone` (`ENTRY`/`BROWSER`, with `fromParam`) mirrors `TimeView`.
  - The script translates `data-fmt` (a `DateTimeFormatter` pattern — the server stays the single
    source of the format) into `Intl.DateTimeFormat` options, formatting with `en-US` to match
    the server's `Locale.ENGLISH`: the viewer's *zone* changes, not their locale.
  - Precedence on load: an explicit `?tz=` beats a remembered `localStorage` choice, which beats
    the entry-local default. Toggling rewrites `?tz=` via `history.replaceState` so a copied link
    carries the choice.
- `[x]` The `js`-tier tests: `BrowserZoneJsTest` (8 cases) — two pinned `timezoneId` contexts
  showing the same instant as 1:00 PM in New York and 2:00 AM in Tokyo, OWNER shipping no script,
  toggle there-and-back, reload persistence, fresh-URL persistence, `?tz=` precedence, and the URL
  rewrite. `JsBehaviorTest` gained `pageInZone(timezoneId)`; the fixtures are served by Playwright
  route interception rather than `setContent`, because `localStorage` and reloads need a real
  origin — still no server, Spring, DB or auth.
- `[x]` Day bucketing by entry zone for migrated types (now including gatherings):
  calendar/itinerary/gap projectors read `ZonedTimestamp.localDateTime()` (entry-zone wall-clock),
  so grouping is already entry-local; conference follows when its slice lands.

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
  `utc.atZone(entryZone).toLocalDate()`; `ScheduleGapProjector`'s night bucketing / "missing hotel"
  logic likewise uses entry-zone local dates — but its *comparisons and sequencing* use instants
  (bug R2), never cross-zone wall-clock.
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

### 5. Backward compatibility — `[x]` event path covered; import path needs no upcaster (by design)

- `[x]` Zone audit shipped (`/admin/zone-audit`, `LocationZoneAudit`) and **passed 2026-06-21**
  (17 locations, all resolved). **Must be re-run before the conference deploy:**
  `LocationAuditProjector` now also sweeps `GatheringChanged` (a gap found during the gathering
  slice), so the audit covers strictly more events than the green run did.
- `[x]` `EventPayloadUpcaster` wired into the `event_log` read path via `PostgresPersister`;
  handles `HotelBooked/Changed`, `TrainBooked/Changed`, `FlightBooked/Changed`,
  `GatheringPlanned/Changed` (the key-merging gathering case); idempotent on new-shape payloads.
- `[x]` Conference upcaster case (`ConferencePlanned`, simple hotel-style in-place
  rewrite) — done 2026-08-05, resolving from `venueAddress.{city,country}`.
- `[x]` **Golden legacy-shape coverage exists.** `GoldenEventDeserializationTest` has the
  `deserializeLegacy` upcast-then-deserialize path and legacy samples for hotel, train, flight
  (`legacyFlightBookedUpcastsEachEndpointFromItsAirportCode`) and gathering, alongside the
  new-shape samples.
- `[x]` Done 2026-08-05: `conferenceTentativelyPlannedLegacyPayloadWithStateFieldDeserializes`
  now goes through `deserializeLegacy` and asserts the venue-zone instants, and
  `conferenceTentativelyPlannedNewShapePayloadDeserializes` sits beside it.
- `[x]` Upcaster/golden test gaps closed (test backfill items 2–3, confirmed present 2026-08-06):
  `EventPayloadUpcasterTest` covers `FlightBooked`, `FlightChanged`, a new-shape flight
  pass-through and the conference case; the `GatheringChanged` legacy golden is
  `GoldenEventDeserializationTest.legacyGatheringChangedWallClockTrioIsUpcastToStartsAtAndEndsAt`.
- `[x]` Legacy zone-less command **round-trip import** (test backfill item 4, confirmed present
  2026-08-06):
  `CommandExportImportRoundTripTest.legacyZoneLessCommandsImportAndDeriveTheirZonesFromTheLocation`.

**Import path — no command upcaster, by design** (corrected 2026-08-05; the earlier "the
`command_log`/import path is not upcast and `CommandImporter` needs the upcaster" framing was
obsolete): the import wire type is the *request* — `ImportableCommandTypes` registers
`BookHotelRequest` & co., not the domain commands. Requests deliberately keep their scalar
wall-clock fields plus an optional `zone` (decision 9), and `events()` re-resolves zones during
import validation. Old scalar backups therefore import **unchanged, with no command-path
upcaster** — absent `zone` means "derive from location", which the passed zone audit guarantees
succeeds for known data. What backward compatibility still needs on this path is *proof*, not
mechanism: the round-trip tests below.

Spec (unchanged where still relevant):
- **Existing-data audit (done, re-run before each slice ships):** sweep every distinct location in
  `event_log` (plus airport codes via the flight path) through `resolve`; assert each succeeds.
  With no default, an unresolvable legacy location has nowhere to go and replay would throw.
  **Scope correction (2026-08-06):** the audit reads `event_log` *only* —
  `LocationAuditProjector` is an `EventStreamConsumer` — so it reports on data already in the
  database and **cannot pre-check an import file**. Earlier wording here claimed
  "`event_log` / `command_log`"; that was never true. A production import failed on three venues
  the audit could not have warned about. **The right instrument now exists:**
  `CommandImporter.validateJson` plus the "Validate only" button on `/admin/import` — a dry run of
  pass one that writes nothing and reports every bad entry in the file *before* importing. Use that,
  not the zone audit, to check a backup.
- **Read-time JSON upcaster** keyed by type (beside `EventTypes`): a bare scalar datetime →
  resolve zone from the same payload's location → rewrite to a `ZonedTimestamp` object before
  record binding. **No default:** an unresolvable location fails loudly. New rows pass through
  untouched. Applies to the **`event_log` read/replay path only** — the import path stays
  wall-clock at the wire (see above).
- **Golden/contract tests:** legacy-format samples pass *through the upcaster*
  (`deserializeLegacy`); new-shape samples bind directly. Keep `EventJsonMapperEquivalenceTest`
  green with the nested `ZonedTimestamp`.
- **Export/import round-trip:** old (scalar, zone-less) and new backups both import; verify in
  `CommandExportImportRoundTripTest`. No backfill/rewrite of stored rows (preserves old-backup
  compatibility — per the export/import-compat rule).

### 6. Conventions & other consumers — `[x]`
- `[x]` `TimeFilterToggleConventionTest` needed no change: phase 4's display-mode parameter went
  onto `ItineraryRenderer.render` and `ConfirmedCalendarRenderer.render`, neither of which is a
  `render(List, TimeView)` list view. The five list renderers kept their signatures, so the
  convention still holds and the test stayed green.
- `[x]` `pom.xml:183` pins the test JVM to UTC. Every zone-sensitive test added in this plan sets
  an explicit zone (venue zones in the projector/renderer tests, `timezoneId`-pinned Playwright
  contexts in the `js` tier), so none of them can pass by agreeing with a UTC server by accident —
  which is exactly what the mutation runs confirmed.
- `[x]` iCal / Google Calendar export / notification paths: none exist in the codebase — nothing to
  route. Revisit if one is added.

### 7. Full test pass — `[x]`
Green 2026-08-05: "All Tests" IDEA run configuration (670 tests, 0 failures) and
`./mvnw test -Pjs-tests`. All new files staged for review.

## Test backfill (from the 2026-08-05 review §4)

**Complete** — every item is done (item 6 dropped with R1), re-verified against the tree
2026-08-06. Every item below was
mutation-verified: the production code was temporarily broken in a way the test should catch, the
failure confirmed, and the code restored. Item 8 found a live bug that way.

1. `[x]` **The four cross-zone `ScheduleGapProjector` scenarios** — written 2026-08-05 as
   `ScheduleGapProjectorTest.CrossZoneConflictDetection`: Amsterdam 19:00–20:30 CEST vs London
   19:30–21:00 BST (locals overlap, instants meet but don't) ⇒ no conflict; SF Oct 3 18:00–21:00
   PDT vs Tokyo Oct 4 09:00–12:00 JST (different local dates, instants overlap) ⇒ conflict; Tokyo
   Oct 8 06:00 JST vs a Chicago conference ending Oct 7 17:00 CDT ⇒ `DifferentCityConflict`; plus a
   same-zone Tokyo pair as the control. **Verified by reverting** `overlapsWith` and
   `detectDifferentCityConflicts` to their wall-clock forms: the first three fail, the control
   passes — so they pin the instant logic rather than merely exercising it.
2. `[x]` **`EventPayloadUpcasterTest` flight cases** — done 2026-08-05: `FlightBooked`,
   `FlightChanged` (nothing covered it before), a new-shape flight pass-through using unknown
   airport codes, and a conference case. Mutation-verified: dropping `FlightChanged` from the
   case list, and collapsing the two endpoints onto one zone, both fail the new tests.
3. `[x]` **Golden legacy sample for `GatheringChanged`** — done 2026-08-05 (Tokyo venue, so a
   zone read from the server instead of the location is visible). Mutation-verified: dropping
   `GatheringChanged` from the upcaster's case list fails it on the leftover `date` key.
4. `[x]` **Legacy zone-less command import round-trip** — done 2026-08-05 in
   `CommandExportImportRoundTripTest`, inline JSON text blocks, Tokyo + San Francisco venues.
   Mutation-verified by making `VenueZone` fall back to `systemDefault()`: this test fails while
   the existing export/import round trip does **not** (it exports and re-imports through the same
   resolver, so it agrees with itself) — which is exactly the gap it closes.
5. `[x]` **`MigrateConferenceToGathering.events()` direct test** — done 2026-08-05
   (`MigrateConferenceToGatheringTest`): zone resolved from its own location; unresolvable location
   throws.
6. ~~**Import-twice/resume test** for the internal commands~~ — **dropped with R1** (see there):
   there is no re-import workflow to protect.
7. `[x]` **Handler zone-rule backfill for hotel/train/flight** — done 2026-08-05:
   `BookHotelHandlerTest`, `BookTrainHandlerTest`, `BookFlightHandlerTest` (16 cases), each
   covering explicit pick wins / derive from location / unresolvable+no-pick throws, plus the
   per-endpoint independence that hotels don't have and the raw-IANA-zone path flights do.
   Mutation-verified: hotel ignoring the pick, train sharing one zone across endpoints, and flight
   defaulting to UTC instead of throwing are all caught.
8. `[x]` **`@WebMvcTest` assertions on the zone `<select>` and the rejection re-render** — done
   2026-08-05 for both the gathering and conference forms. **This found a real pre-existing bug:**
   `PlanConferenceController`'s POST used a bare `@ModelAttribute`, so Spring bound the form under
   `planConferenceRequest` while `plan-conference.html`'s `th:object` is
   `planConference` — *every* validation failure on `/plan-conference` (the date rules
   too, not just the zone) threw at render time instead of showing the error. Fixed by naming the
   attribute, as `PlanGatheringController` already did. Mutation-verified: removing the selector
   block, reverting the attribute name, and swallowing the gathering's `ZoneResolutionException`
   are each caught.
9. `[x]` **Phase 4 `js`-tier tests** — done 2026-08-05 as `BrowserZoneJsTest`; see phase 4.
   Mutation-verified: disabling the localization, giving OWNER the browser zone, dropping the
   stored-preference read, dropping the explicit-`?tz=` precedence, and dropping the URL rewrite
   are each caught by a distinct case. The URL-rewrite case was *added* because the first
   mutation run showed nothing failed without it — `localStorage` alone was carrying the reload.

## Smaller design improvements (from the 2026-08-05 review §5 — non-blocking)

1. `[ ]` **`events()` implementations construct their own `LocationZoneResolver`**
   (`BookHotelRequest.java:81`, `MigrateConferenceToGathering.java:46`, and siblings). Fine while
   the resolver is a stateless, dependency-free table, but import validation cannot be exercised
   with a stub resolver, and every site goes stale if the resolver ever gains configuration (e.g.
   the ISO-code aliases below). If that happens, thread the resolver through `events(...)` (or an
   import context parameter) in one sweep — the interface change touches all eleven
   implementations. Not worth doing preemptively; written down so it's a decision, not a surprise.
2. `[~]` **Location codes and unlisted towns.**
   - `[x]` **State/province resolution** (done 2026-08-06, prompted by a real failed production
     import: Lone Tree CO, North Kawartha ON, North Gower ON). `LocationZoneResolver` precedence is
     now city → **region** → country. The region table covers every US state, Canadian province and
     Australian state under both the postal abbreviation and the spelled-out name (stored data uses
     both), **scoped by country** so `WA` cannot mean Washington and Western Australia at once.
     `resolve(Address)` had been discarding `address.region()` entirely — that was the actual gap.
     Zone-split states (FL, TX, ID, …) are keyed to their predominant zone; the city table runs
     first and is where an exception belongs. `Address`-carrying call sites now pass the region:
     the upcaster's hotel/gathering/conference paths, and `CityCountry`/`LocationAuditProjector`/
     `LocationZoneAudit` so the audit resolves exactly the way live entry does. Train stations have
     no region and pass `""`.
   - `[ ]` **ISO alpha-2 aliases for single-zone countries.** Still open. The gathering slice's
     watch-out ("a manually-typed `GB` does not resolve") already bit test fixtures, and hotel
     golden samples store `"country": "GB"` — evidence real data can carry codes. Single-zone
     countries only (multi-zone ones now go through the region step above). Re-run
     `/admin/zone-audit` after.
3. `[x]` **`ConferenceProjectorTest` moved to `application/`** 2026-08-05.
4. `[ ]` **`EventSourcingConfig` projector wiring** repeats the subscribe-then-replay triple
   fifteen times; a small private `wire(projector)` helper would collapse it without Spring
   cleverness. Cosmetic; do it opportunistically.
5. `[ ]` **`CommonZone` coverage** remains USA/Canada/UK/CET while itineraries include Japan — the
   "promote the picker" follow-up below stands; no action now, but the conference form should
   reuse whatever list exists rather than fork it.

## Critical files
- New: `domain/ZonedTimestamp.java`, `application/LocationZoneResolver.java` (+ city/country table),
  `CommonZone` enum, the JSON upcaster, a shared time-formatting helper + browser-zone toggle.
- Events: `HotelBooked/Changed`, `FlightBooked/Changed`, `TrainBooked/Changed`,
  `GatheringPlanned/Changed`, `ConferencePlanned`.
- Commands/contexts + web requests: `BookHotelCommand`/`ChangeHotelCommand` (+ flight/train/
  conference/gathering), `BookHotelRequest`/`ChangeHotelRequest` (+ siblings); internal commands
  `MigrateConferenceToGathering`, `ClearDifferentCityConflict`.
- Eval: `TemporalView`, `TimeView`, the five list controllers.
- Display: renderers (`BookedHotelsRenderer` + siblings), `ItineraryProjector`/`CalendarViewBuilder`/
  `ScheduleGapProjector`; role read as in `GeneralController.java:42`.
- Flight zone source: `AeroDataBoxClient.parseLocal` (preserve offset, capture airport zone).
- Compat: `EventJsonMapperFactory`/`EventTypes`/`ImportableCommandTypes`,
  `GoldenEventDeserializationTest`, `CommandImporter`, `CommandImportSafetyTest`,
  `CommandExportImportRoundTripTest`, `EventJsonMapperEquivalenceTest`.

## Verification

Status: 1 `[x]` (hotel filtering fixed in `5dab535`) · 2 `[x]` · 3 `[x]` (train) · 4 `[x]`
(entry-zone validation done for all five types as of 2026-08-05) · 5 `[x]` (legacy goldens through
the upcaster exist for all five, and the zone-less command round-trip landed) · 6 `[x]` · 7 `[x]` ·
8 ~~resume idempotence~~ (dropped with R1 — no re-import workflow).

1. **Bug repro:** a hotel with checkout earlier today shows under `/booked-hotels` (FUTURE) pre-fix;
   post-fix it drops off once `Instant.now()` passes the checkout instant.
2. `LocationZoneResolver` unit tests: flight API zone, single-tz country, multi-tz city, common-zone
   manual pick, and `ZoneResolutionException` on an unresolvable location; plus a DST
   spring-forward/fall-back case.
3. Per-endpoint zones: a Frankfurt→Paris train stores two different zones; durations correct.
4. Entry-zone validation: checkout same calendar day as checkin still rejected across UTC midnight.
5. `GoldenEventDeserializationTest`: legacy scalar samples upcast via `deserializeLegacy`;
   new-shape files bind directly. `CommandExportImportRoundTripTest`: old (zone-less) + new backups
   both import.
6. Display by role: OWNER entry-local; FAMILY browser zone; ANON entry-local + working toggle;
   no-JS falls back to entry-local; calendar buckets by entry-zone day. `js` tier: a UTC instant
   renders correctly in two browser zones, **and a Playwright toggle test confirms clicking the
   browser-zone toggle switches the displayed times and toggling back restores entry-local**.
7. "All Tests" IDEA config + `./mvnw test -Pjs-tests` green.
8. ~~**Resume idempotence:** importing the same backup file twice reports every entry skipped on the
   second run and appends zero events.~~ **Dropped with R1** — the workflow is wipe-then-import-once,
   so nothing ever re-imports onto existing data.

## Open follow-ups (not in this plan)
- Promote the common-zone picker to a richer per-entry zone override/search if the curated list and
  city table prove too coarse in practice.
