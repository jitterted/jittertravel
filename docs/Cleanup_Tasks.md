# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`. For an index of every
**plan doc** and its status, see `Backlog.md`.

**This file is the source of truth for small work, and `Backlog.md` points at it rather than
summarizing it** (2026-09-08). It used to keep a roll-call table of these items, and that table went
stale in both directions — three of nine deferred items were listed, and two done items had grown a
*second, independently written* account there. So there is nothing to keep in step: add an item here
and you are finished. The rule that keeps it that way is stated in `Backlog.md`'s header — a row
there may not carry a fact that is not in the doc it points at.

Three sections: **Open** is work that is wanted, **Deferred (until needed)** is work whose shape is
known but whose need has not arrived — each item names the trigger that would promote it — and
**Done** is the record.

Some items below were **lifted out of a shipped plan doc** when that doc moved to `docs/archived/`
(2026-08-21). The archived doc keeps the reasoning and is still worth reading before starting;
this list is what makes the item findable, because an archived doc is history and nobody scans it
for open work.

**When an entry declares some *other* component stale, that same change opens an item against that
component** (2026-09-09). Writing "X is not a substitute", "X went stale", or "X gave no warning
before Y failed" into the record of building **Y** files the supersession under the replacement and
nowhere else — and once that entry is ticked it is history, which by the paragraph above nobody
scans for open work. The reasoning is never cheaper to write down than at the moment you are
already making it, so spend the one line then. It does not have to propose the deletion; "superseded
by Y, does it still earn its place?" is enough to make X findable.

**The incident.** `/admin/zone-audit` was called runtime-only and stale in the boot-replay preflight
entry, backwards for the wipe-then-import workflow in the dry-run validation entry (it "gave no
warning before the 2026-08-06 production import failed on three venues"), and "not a substitute" in
`DEPLOYMENT.md` — three verdicts, each written to justify building its *replacement*, all ticked and
archived. Meanwhile its only appearance in **Open** was an item to *add* `PrivateEventPlanned`
coverage to `LocationAuditProjector`: scheduled investment in a tool three documents had already
retired in prose, with neither half aware of the other. Nothing in the tree proposed ending it, and
it took Ted asking — *"why wasn't zone audit deletion already scheduled or completed without my
prompting?"* — for the two to meet. Same shape as the `Pre-Push-Tasks.md` rule: a need not written
down when it is created does not get written down later.

## Open

- [ ] **DISCUSS: the conference fold is now written out in three read models.** Raised by Ted
      2026-09-09 while approving the `/itinerary` conference sync: *"this looks like computing the
      same information in 2 (or more) different places."* He is right —
      `ConferenceCalendarProjector`, `ItineraryProjector` and the dashboard's `ConferenceProjector`
      each keep their own `Map<ConferenceId, ConferenceProgress>` and each write out the **same nine
      `case` arms** (`ConferencePlanned`, `ConferenceCancelled`, `ConferenceAttendanceConfirmed`,
      `ConferenceAttendanceDeclined`, and the five talk events) to drive it.
      **What is already shared, and what is not.** `ConferenceProgress` holds the *rules* — the
      auto-commit on acceptance, the auto-drop on rejection, that an invitation commits nothing —
      and its javadoc explains why they are shared: written out three times they would be three
      chances to disagree, and one of the three is the anonymous calendar, where disagreeing means
      leaking. What is **not** shared is the *fold* — the switch that walks the event stream and
      applies those rules. That is the duplication.
      **The constraint any answer has to respect:** R12 forbids one read model being built from
      another, so the fix is **not** "let the itinerary read the calendar projector". The shape to
      discuss is a shared *folder* — something that turns a `Stream<StoredEvent>` into
      `Map<ConferenceId, ConferenceProgress>` — that each projector composes while still building
      its own view. That keeps every read model event-sourced and removes only the copied switch.
      **Do not act on this without the discussion**, for two reasons. It is a fourth user arriving,
      so "no abstraction before the second user" no longer objects — but the three current copies
      are not quite identical (each also builds its own view type in the same switch, and the
      dashboard keeps dropped conferences that the other two remove), and pulling out a shared fold
      that has to carry those differences may buy nothing. **Measure first:** the honest question is
      how many lines actually coincide once the view-building is set aside.

- [ ] **`/itinerary` and `/calendar` disagree on the speaking chip's wording and colour.** Noticed
      2026-09-09 while adding the conference chips to the itinerary. `/calendar` says **"A Ted
      Talk"** on a near-black pill (`.entry-speaking-badge`, `#111827`); `/itinerary` says
      **"Speaking"** on purple (`.speaking-badge`, `#7c3aed`) — and has done since gatherings
      arrived. The new conference chip on the itinerary follows its own page rather than the
      calendar, so the page is at least self-consistent, but the same fact now wears two names and
      two colours depending on which surface Ted is looking at. The "Maybe" chip was deliberately
      **not** given this treatment: it uses the calendar's amber (`#b45309`) on both pages, because
      CLAUDE.md's reasoning for that colour (amber so it reads as a different statement from the
      speaking chip; solid rather than muted, since muted reads as *cancelled*) is about the
      meaning, not the page. Deciding which wording wins is a judgment call for Ted, not a cleanup
      to apply.

- [ ] **Locations and addresses should be valid before they are stored.** Raised by Ted 2026-09-07,
      while closing the alpha-2 zone-alias item: the real problem in that area is not a missing
      country alias, it is that **anything typed into a location field gets stored**. Production
      data carries `"Brussels"` as a *country* (14 rows — a city in the country field) and
      `"Germany "` untrimmed (9 rows). Both are survivable today by accident rather than design:
      `LocationZoneResolver.normalize()` trims, `Address`'s compact constructor repairs the space on
      binding, and Brussels resolves **only** because someone added an `antwerp` entry to the city
      table to rescue that one hotel.

      **The shape already exists in the tree** — `EnteredLocation` rejects a station name pasted
      into a city box on the four train/hotel write commands (CLAUDE.md, "A city that is really a
      station is rejected on the write path"). This is the same rule pointed at the country field,
      and at the kinds `EnteredLocation` was never wired to (gatherings, conferences, private
      events, ground transfer — two of their stored events would trip its first rule today).

      **Two constraints carry over from that rule and are not negotiable.** Reject in the *command*,
      never in the record's compact constructor: Jackson binds stored payloads through the
      constructor, so a rule there applies retroactively to every event in the log and one old row
      breaks a replay *and* a restore. And **validate a proposed rule against the production backups
      before shipping it** — a plausible-sounding fourth rule was written and deleted the same day
      once it scored one false positive and no true ones.

      Open questions, all genuinely open: does a country field become a picker rather than free
      text (which ends the class of bug outright, at the cost of every existing row needing to map
      onto the list)? Does an unresolvable *zone* stay the enforcement mechanism, or does the
      country get its own rule? And what happens to the 14 Brussels rows — left as history, or
      repaired by a migration? Note the `antwerp` city entry is load-bearing until they are.

- [ ] **Two conferences at one venue join on the city string, and a conference cannot be edited.**
      Raised by Ted 2026-09-08, planning JavaLand 2027 (Mar 2–4) alongside the co-located DevLand
      (Mar 3–5), each with its own CFP. **The overlap itself is fine and needs no work**: conference
      × conference is never compared (`overlappingOccupancies` pairs gatherings and private events;
      `differentCityConflicts` is gathering × conference), `CalendarViewBuilder` stacks overlapping
      same-lane bands into extra sub-rows, one hotel over the whole span merges into a single
      missing-hotel run, and the two CFPs are separate rows, separate iCal UIDs and separate Google
      links because everything is keyed by `ConferenceId`.

      **What is not fine is the spelling of the venue city.** `Place.of(venueAddress)` yields
      `locationForMatching`, which defaults to the typed city, and `HomeCities.sameLocation` is
      exact apart from case and trim. Enter one as `"Nürnberg"` and the other as `"Nuremberg"` and
      `ScheduleTimeline` sees two cities: a false `MissingTravel` gap between them, the missing-hotel
      run splits at the city change, a hotel booked under one spelling does **not** cover the nights
      attributed to the other — a missing-hotel row for a room that is booked — and any gathering in
      those days raises a `DifferentCityConflict` against one of the pair. This is event 92's failure
      (CLAUDE.md, "Typed text is normalized where it lands") through a third door: not a stray space,
      not a station name in a city box, but two spellings of one place that only *look* like one
      place because they are never displayed side by side.

      **Conferences are the worst kind to hit it with, for two reasons.** `plan-conference.html`
      exposes `venueCity` but **not** `locationForMatching` — hotels, gatherings and private events
      all have that override input, so on those kinds a mismatch is repairable by naming a shared
      match location. And there is **no conference edit at all**: no `ChangeConference` command, no
      `ConferenceChanged` event, no template. A mistyped venue city on a conference is therefore not
      fixable from inside the app.

      **Today's mitigation is a convention, which is why this is written down**: when entering the
      second of two co-located conferences, copy the city from the first. Fixes worth weighing, in
      rising order of cost — add the `locationForMatching` input to `plan-conference.html` (one
      field, matches four existing forms, and repairs the *next* pair rather than an existing one);
      warn on the plan form when a city is a near-miss for one already in the schedule; or build
      conference editing, which is wanted anyway and settles the whole class. Related: the
      validate-locations item above, whose `EnteredLocation` rule is also not wired to conferences.

- [ ] **Three notes lifted from `archived/UtcDatetimeStoragePlan.md`** when it was archived
      2026-09-07. The archived doc has the full reasoning for each; these lines exist so they stay
      findable.

      - **`events()` implementations construct their own `LocationZoneResolver`** (improvement 1).
        Eleven implementations each `new` one up. Harmless while the resolver is a stateless,
        dependency-free table, but import validation cannot be exercised with a stub, and every site
        goes stale the day the resolver gains configuration. If that day comes, thread it through
        `events(...)` (or an import context) in **one** sweep — the interface change touches all
        eleven. Deliberately not done preemptively; written down so it is a decision, not a surprise.
      - **`EventSourcingConfig` projector wiring** (improvement 4) repeats the subscribe-then-replay
        triple fifteen times; a small private `wire(projector)` helper collapses it without Spring
        cleverness. Cosmetic — do it opportunistically.
      - **`CommonZone` coverage** (improvement 5) is USA/Canada/UK/CET while itineraries already
        include Japan. No action yet, but any new form must **reuse whatever list exists rather than
        fork it**.

- [ ] **An overlap and a location problem still take two submits.** The train forms report every
      problem in one response (CLAUDE.md, "A rejected form reports everything it can see"), but
      `OverlappingLegRefused` is outside that mechanism: `BookTrainCommand` runs
      `TrainStations.check()` first and throws, so a bad city hides an overlap that would also have
      been reported. Fix the city, submit, meet a fresh error — the exact shape the rule exists to
      prevent.

      **Part of the ordering is right and must stay.** An overlap is only meaningful once the
      window itself is valid, so it belongs after the date rules. The wrong part is that it also
      sits after the *location* rules, which the times do not depend on at all.

      **Why it was not fixed with the rest on 2026-09-06.** `OverlappingLegRefused` is shared with
      the two flight paths, while `InvalidTrainEntry` — the only thing that carries several problems
      at once — is train-shaped. Merging them means either a train-only overlap channel (a second
      vocabulary for one rule) or generalising the multi-problem carrier across kinds, which is the
      same work as extending field-level errors to flights and hotels, already queued below. Do them
      together.

- [ ] **Test isolation: the test half is enforced, the production half is not.**
      Raised by Ted 2026-09-06 (*"if the tests can't be isolated due to production code, that is
      absolutely a problem with the implementation"*); designed and built the same day, parked on a
      misdiagnosed deadlock, **and the test half shipped 2026-09-11**. What is left open is
      `/admin/database`: a truncate or restore still leaves `EventStore` stale in a running app.
      The rule itself is in CLAUDE.md, "Every test is isolated".

      **The problem.** `EventStore` fills its in-memory event list once at boot and only ever
      appends. No database truncation reaches it, so the seven integration classes sharing a Spring
      context shared event state — with each other and, because the container is `withReuse(true)`,
      with previous runs. `CommandExecutor.eventsForDecision()` folds **every write-path decision**
      from that list, which is why this was never only a test problem: `/admin/database` truncate
      and restore both leave it wrong — **still true today** — and a stale entry can make the domain
      refuse a booking that is fine. Invisible until 2026-09-06 because every earlier fold asked
      about one specific id; the overlapping-legs rule is the first cross-aggregate question.

      **The test half SHIPPED 2026-09-11**, and the deadlock that parked it was a misdiagnosis —
      see below. Three of the four reverted pieces are back, plus a fourth the pre-commit review
      added:
      1. `EventStore.reload()` — the boot replay extracted so it can run more than once. A failed
         load leaves the previous list in place rather than emptying it, for the reason `append`
         persists before it notifies. Deliberately does **not** rebuild read models, since
         projectors hold accumulated state and replaying a shorter stream over them removes nothing.
         **Read-only stays a one-way latch**: a later successful reload does not lift it, only a
         restart does. Deliberate, said in the javadoc, and pinned by (4) below — read-only means a
         person should look, and a store that healed itself on the next reload would hide what set
         it. It only became a question worth answering because `reload()` is reachable at runtime.
      2. A `@BeforeEach` in `AbstractTestcontainerIntegrationTest` calling `reload()` and then
         **asserting the store is empty** — the assertion is the point, so the next leak fails
         loudly. Injected as `ObjectProvider<EventStore>`, because `PostgresPersisterTest` extends
         the base class with a `@JdbcTest` context that has no such bean. Remove the `reload()` and
         **16** methods fail on it by name — every method of the five classes whose context has an
         `EventStore`, which is how much was leaking. `ifAvailable` is the one place this design
         degrades quietly: an integration test whose context ever loses the bean gets no guard and
         no signal. One of seven subclasses today, and named in the comment.
      3. `<runOrder>random</runOrder>` in the Surefire config, so order-dependence fails on the run
         that introduces it instead of hiding behind the default `filesystem` order. **Use the
         seed** — every run logs `To reproduce ordering use flag -Dsurefire.runOrder.random.seed=<n>`
         and re-running with it reproduces the order exactly. That line is what stops a shuffled
         failure from reading as flake and being re-run until green.

         **Extended in the pre-push review to the methods as well**, because `runOrder` shuffles
         **classes only** and says nothing about the methods inside one — and one method leaving
         state the next one reads is the commoner shape. `junit-platform.properties` sets
         `junit.jupiter.testmethod.order.default=…MethodOrderer$Random`. **That needed a third
         file to be honest**: JUnit prints its seed at CONFIG level, nothing else here logs below
         INFO, so the method order of a failing run was unrecoverable — `logging.properties`,
         named from `argLine`, drops `org.junit` alone to CONFIG so the line appears. A shuffle
         whose seed is invisible is a flakiness generator, not a guard, so the three ship together
         or not at all. Both seeds have to be passed back to reproduce a run exactly.
         The whole suite was run with methods shuffled before this landed: **green**, so there is
         no method-level order-dependence in the tree today.
      4. **`EventStoreTest` cases for `reload()` itself** (added 2026-09-11 in review, before the
         commit). The `@BeforeEach` guard asserts the store is *empty* immediately after the `@Sql`
         truncate, so it **cannot distinguish a real reload from a bare `events.clear()`** —
         demonstrated by deleting the re-read, which left all 2413 tests green. It also left the
         *boot replay* unguarded, which it had been all along. Two unit cases now carry the
         contract: reload **replaces** the list from the durable log and re-points `nextSequence`,
         and a **failed** load leaves the previous list in place and **latches** read-only.
         Mutation-verified five ways — drop the re-read (2 fail), drop the clear (1), move the
         clear before the load (1), drop the `nextSequence` set (1), lift read-only on a successful
         reload (1). The lesson generalises past this item: **a guard that asserts a component is
         empty proves the emptying, never the refilling.**

         The fifth of those came from the pre-push review, and it is the same lesson once more:
         the latch was *documented* in three places and asserted in none, so the case that says
         "enters read-only" was extended to reload successfully afterwards and assert it is still
         read-only. Written down and mutation-verified are different claims, and only the second
         one survives a refactor.

      The fourth *reverted* piece — an `AFTER_TEST_METHOD` truncate — **is not coming back, and was
      never needed.**
      Its purpose was to stop the reused container carrying rows into the *next run's* boot replay,
      which is a once-per-JVM concern that a per-method `@Sql` was the wrong tool for; (2) covers the
      same ground from the other side, by reloading rather than by leaving the tables clean.

      **Still open: the production half.** `reload()` exists but nothing calls it outside the
      constructor. `/admin/database` truncate and restore still leave the in-memory list wrong, and
      `CommandExecutor.eventsForDecision()` folds every write-path decision from it — so a stale
      entry can still make the domain refuse a booking that is fine. Note that `reload()` alone is
      not the whole answer there: the projectors are stale too and only a restart rebuilds them,
      which is what the "Surface *restart needed* after a truncate" item above is about. Decide
      those two together.

      **Decide the guard with them, and an arch test is the wrong one.** Asked 2026-09-12 (Ted):
      should an ArchUnit rule assert that only tests call `reload()`? **No, and not in this shape** —
      the rule points away from the fix, since the open work above is to *add* a production caller,
      so the test would have to be deleted the day that lands (CLAUDE.md's own "a test that has to
      be edited on every change stops guarding"). It would also be the tree's first ArchUnit
      dependency for one rule, which `ApplicationServicesUseCommandExecutorTest` and
      `DomainIsPureTest` both deliberately declined — if such a rule is ever wanted it is a plain
      source scan beside them. And the surface hardly needs it: application services cannot take an
      `EventStore` at all (that same test), so the only production holders are `CommandExecutor`
      (append), `ProjectorBootstrapper` (`subscribe` + `findAll` at boot) and `GeneralController`
      (one `isReadOnly()` read).
      **The decisive reason is that a call-site rule cannot see the hazard.** The trap is not that
      someone called `reload()`, it is that they called it and left the projectors stale — the
      thing its javadoc warns about. Ban the call and the admin fix is blocked; allow it and
      nothing has been asserted about what bites. So the guard that ships with the production half
      is **behavioural**: truncate through `/admin/database`, then assert the store reports empty
      *and* whatever is decided about the projectors. The one arch-flavoured rule worth having is
      narrower and only becomes writable once that caller exists — *"`/admin/database` is the only
      production caller of `reload()`"*, as a source scan.

      **The deadlock: what it actually was.** Diagnosed 2026-09-11 and it is **not** a production
      issue and **not** the boot replay. `PostgresPersisterTest` is a `@JdbcTest` slice, so it is
      transactional-with-rollback: each method runs inside a test-managed transaction. Spring calls
      `afterTestMethod` on its listeners in **reverse** order, so `SqlScriptsTestExecutionListener`
      fires an `AFTER_TEST_METHOD` `@Sql` *before* `TransactionalTestExecutionListener` rolls that
      transaction back. With `transactionMode = ISOLATED` the truncate takes a second connection,
      `TRUNCATE` wants `ACCESS EXCLUSIVE`, and the only thread that could end the blocking
      transaction is the main thread — which is blocked on the truncate. Permanent, by construction,
      and `PostgresPersisterTest` **alone** reproduces it.

      **The misread that cost a day, and it is subtler than the one recorded here before.** The
      blocking connection's last statement was read off `pg_stat_activity.query` as
      `loadAllEvents()` — the boot replay — and everything followed from that. It was
      `findAllEventsForBackup()`. Collapse both to one line and they are **identical for the first
      ~80 characters** (`SELECT sequence, event_id AS eventId, command_id AS commandId, timestamp,
      type, payload::text AS payl…`), which is all the column shows. Two checks would have caught
      it: that context has **no `EventStore` bean at all**, so no boot replay happens in it; and the
      connection never ran `SELECT COALESCE(MAX(sequence), 0)`, the replay's own first statement.

      **So read the statement history, not the snapshot.** `log_statement=all` on the container,
      then the whole connection's story in one grep — that is what made it obvious:

      ```
      docker exec jittertravel-test-postgres psql -U test -d test -c "ALTER SYSTEM SET log_statement='all'"
      docker exec jittertravel-test-postgres psql -U test -d test -c "ALTER SYSTEM SET log_line_prefix='%m [%p] '"
      docker exec jittertravel-test-postgres psql -U test -d test -c "select pg_reload_conf()"
      # reproduce, find the blocked pid, then:
      docker logs --since 3m jittertravel-test-postgres 2>&1 | grep '\[<pid>\]'
      ```

      It printed one `BEGIN` at the start of the test method, the method's own writes, the SELECT,
      and then nothing — a transaction still open with no COMMIT and no ROLLBACK, which is exactly
      what `@JdbcTest` is *supposed* to look like mid-method. (`ALTER SYSTEM RESET log_statement`
      afterwards; it is noisy.)

      **Cost of `runOrder=random`: none measurable.** 24.4s in filesystem order, then 32.1s,
      25.1s and 27.9s under random — and the 32.1s run was the one that recompiled. So the old note
      stands: the guess that shuffling would cost time by destroying Spring context-cache locality is
      still unsubstantiated. Watch the clock anyway, for the opposite reason: a run stretching past a
      couple of minutes is a deadlock, not slowness. 2411 tests, green on every run.

      **What is no longer load-bearing:** `BackupRestoreRoundTripTest` derives each booking's window
      from its own flight id so no sibling method and no leg replayed from a previous run can occupy
      it. That was the whole workaround while the base class was untouched. It is harmless and
      stays, but it is no longer what keeps that test green.


- [x] **A fix action does not come back to the report it was launched from. FIXED 2026-09-06.** Ted, 2026-09-06:
      *"I'm fixing schedule problems and keep ending up somewhere else and have to return to the
      schedule problems page. not huge, just annoying."*
      **Three-quarters of this already exists.** `ProblemFix.explaining` appends `&from=<origin>` to
      every fix href, `FixOrigin` already holds the way back for all three surfaces — including
      `?view=list` vs `?view=calendar` — and `fragments/problem-context.html` renders it as a Back
      link. **The gap is the POST.** No form carries `from` through: every `th:action` drops the
      query string, so all eight fix targets redirect to their own hardcoded default
      (`/booked-hotels`, `/booked-trains`, `/calendar`, `/itinerary`). Ted clicks Book hotel from
      the report, books it, and lands on the hotels list.
      **`ClearConflictController` is the half-done case and shows the shape of the bug:** it already
      redirects to `/schedule-problems`, but hardcoded — so it loses the view and drops a reader who
      came from the list onto the calendar.
      **Proposed fix, small and shared.** (1) `ProblemContextAdvice` exposes the raw `from` as a
      model attribute, the same way it already exposes `problemContext` — an advice rather than six
      constructor dependencies, for D6's reason exactly. (2) Each fix target's form carries it
      (hidden input), so it survives both the POST and a validation re-render. (3) Each controller
      redirects to `FixOrigin.fromParam(from).backHref()` when present, else its current default —
      one line each. (4) A convention test mirroring `ProblemContextFragmentConventionTest`, which
      walks every href `ProblemFix` can emit, so a new fix target cannot forget the return trip in
      the same silent way it could forget the banner.
      **Watch two things.** The redirect target is derived from a query parameter, so it must be
      resolved through `FixOrigin.fromParam` and never used as a raw path — an open redirect is
      exactly what a hand-edited `?from=` would otherwise buy. And a fix that *fails* validation
      must keep `from` on the re-rendered form, or the second submit loses the way back.
      **Built as proposed, all eight targets.** `FixOrigin.returnTo(String)` returns
      `Optional<String>` — **empty when absent**, which is the whole subtlety: `fromParam` defaults
      a missing value to the calendar so a hand-typed link still renders a Back link, and reusing
      that on a redirect would send every ordinary booking made from a nav card to the report.
      `ProblemContextAdvice` exposes the raw `from` as `fixOrigin` (no projector, no clock, so it
      works in a slice that has neither); each template carries it in a `th:if`-guarded hidden
      input; each controller's **success return only** goes through a two-line `returnTo` helper —
      a read-only refusal or a stale-link miss has fixed nothing and still goes where it did.
      `ClearConflictController` stopped hardcoding `/schedule-problems` and now keeps the view.
      Guarded by two new cases in `ProblemContextFragmentConventionTest` — one walking every
      `ProblemFix` href to assert its form carries the origin, one asserting an unrecognized
      `?from=` resolves to the calendar rather than becoming a path — plus seven round-trip cases
      in `CancelTrainControllerTest`. Mutation-verified three ways: ignoring the origin on success
      failed 3, treating absent as calendar failed 3, dropping a hidden input failed 1.
      **One near-miss worth recording:** restoring a mutated template with `git checkout` silently
      reverted it to a *stale index*, losing the hidden input — caught only because the suite was
      re-run afterwards. Restore mutations from a copy, not from git, while work is staged.


- [x] **`ScheduleProblemsRenderer` partitions by `instanceof`, so a new problem type renders
      nowhere and nothing fails. FIXED 2026-09-06** with slice 2 of
      `CancelTrainAndOverlappingLegsPlan.md`, which is the change that would have hit it. The five
      filters became one exhaustive `switch` in a private `Sections` record, so the compiler now
      stops a sixth variant here as it already did in the other three places. Found 2026-09-06 while planning
      `CancelTrainAndOverlappingLegsPlan.md`, and independent of it — this is a hole *today*.
      `render(List<ScheduleProblem>)` splits its argument with **five separate `instanceof` filters**
      into five explicit sections. `ScheduleProblem` is sealed, and the three other places that map
      over it — `ProblemKey.of` (`ProblemRef` today), `ProblemFix.fixesFor`, `ProblemBand.from` —
      are all **exhaustive switches**, each with a javadoc saying in as many words that a new problem
      type cannot be added without deciding the question it asks. The list view is the one that
      silently opts out: add a sixth variant and the compiler stops you in three files and says
      nothing about the fourth, so the problem is detected, keyed, linked and drawn on the calendar,
      and is **absent from `/schedule-problems` itself**. Fix is small: replace the five filters with
      one exhaustive `switch` appending into per-kind lists. Do it whether or not the overlapping-legs
      detector is ever built — the next variant is the one that pays for it.
      Note the assertion to write with it must not be a hard-coded list of kinds, per the
      `CalendarDayMenuTest` rule (a test that has to be edited on every change stops guarding).

- [x] **`/calendar` never names the month except on the 1st.** **Done 2026-08-31.** The month tint is too faint to answer
      "which month am I looking at", and the only text naming one is the day label on the 1st — so
      any week not containing a 1st leaves a reader counting. Ted, 2026-08-31: *"i completely lose
      what month it is for weeks that have entries."* Note **weeks that have entries**: this is the
      ordinary case, not a side effect of collapsing anything.
      **Built and then reverted the same day**, only because it rode in on the quiet-week-runs branch
      (`archived/QuietWeekRunsPlan.md` D5b) — Ted's verdict on it was *"i like the months"*. The
      shape: a sticky `.calendar-month-header` band, one per month, parked under the weekday header
      (`top: var(--calendar-weekday-header-height)`, `z-index` below the header's 10). A week is
      filed under the month its **Sunday** falls in, so a straddling week belongs to one month and
      not both; the alternative puts two bands between two adjacent weeks. One test —
      `everyMonthGetsOneBandAboveTheFirstWeekThatStartsInIt` — mutation-verified by filing a week
      under its Saturday instead.
      Re-landed on its own, as intended — then **removed again 2026-09-01** with the year overview
      (`archived/YearOverviewPlan.md`). Two reasons, and the second is why it cannot come back in
      this shape: they were built for orientation *while scrolling* to find a month, which the jump
      replaces; and a week is filed under its **Sunday**, so Sep 1–5 rendered under a band reading
      "AUGUST 2026", and a `gridEnd` on the 1st–5th left a month with no band at all. That is also
      why they could not be the jump anchors — those are the month-start day cells, whose set is
      complete by construction. The 1st still names its month in its own day label.
- [x] **Nothing enforced that the public calendar handles the same removal events the owner's does.**
      Raised by review of the S2 refactor 2026-08-21; **done the same day** — Ted chose
      lifecycle-propagation scenarios over a source-scan convention test, the option that fits the
      standing preference (sealing `Event` was rejected). `CalendarRemovalPropagationTest` drives one
      event stream into **both** read models and asserts the entry is in both, then gone from both,
      for all four removal events (`HotelBookingCancelled`, `GroundTransferCancelled`,
      `ConferenceCancelled`, `ConferenceAttendanceDeclined`) plus the confirmed-then-cancelled
      conference, whose entry both projectors have already rewritten once. The presence assertion is
      what makes it meaningful: without it a creation event one side ignored would leave that side
      empty from the start and the removal assertion would pass for the wrong reason.
      **It found a real hole on the way in:** `GroundTransferCancelled`'s branch in
      `GroundTransferCalendarProjector` had **no test at all** — deleting it left the entire suite
      green. Now caught.
      **Still true, and deliberately so:** adding a *fifth* removal event needs a new row in that
      test, and nothing forces it. That is the known cost of the scenario-test approach. The
      source-scan alternative (compare the two switches' matched event types, in the style of
      `PublicCalendarBuildsOnlyPublishableEntriesTest`) stays available if this ever proves too easy
      to forget.
- [x] **No test covered the `PublicCalendarProjector` bean's registration.** From the 2026-08-21
      review; **done the same day**. Every test that renders `/calendar` supplies it as a
      `@MockitoBean`, so reducing `bootstrapper.register(...)` to a bare `new PublicCalendarProjector()`
      would have left the projector neither subscribed nor replayed — **a permanently empty calendar
      for every anonymous visitor**, with the whole suite green.
      Fixed generally rather than for the one bean, as noted: `EveryProjectorBeanIsRegisteredTest`
      asserts that **every `@Bean` returning an `EventStreamConsumer`** calls
      `bootstrapper.register(...)` — 23 beans today. The set is derived by **reflection** over
      `EventSourcingConfig`, so a new projector bean is covered the day it is written and there is no
      fixture to forget; only "does it call register" is answered by reading source, that call being
      the whole of what there is to check. A second test pins the guard against its own rot (the
      config class moving, or the `bootstrapper` parameter being renamed), since either would
      silently reduce it to checking nothing.
- [ ] **A gap *into* home is dated by the wrong end — the mirror image of D14.** Lifted from
      `archived/ScheduleProblemsRewritePlan.md` (its whole "Open" section) when that plan was
      archived 2026-08-21. D14 fixed one direction: a gap *out of* home is now dated by the day
      Ted must be away, not by the landing. The other direction was never done — a gap *into* home
      still spans every day from the last away fact to the next home departure, so it reads as far
      longer than it is. **Why this is not a symmetric edit:** collapsing it moves
      `relevantUntil()` earlier, which changes when the problem drops out of the FUTURE filter, so
      it needs a decision about what the gap's *end* means before any code moves. Read D13 and D14
      in the archived plan first — they set the vocabulary this has to fit.
- [ ] **Change Private Event (the edit flow) — now owned by `ChangePrivateEventPlan.md`
      (2026-08-24), not by this list.** It outgrew a cleanup line: the plan puts **Cancel** in
      front of the edit as slice 1, because a private event has no booking (so the common
      correction is removing a wrong entry) and a wrong one is a false presence fact in
      `ScheduleGapProjector`. **Slice 1 shipped 2026-08-24** — cancel is live, linked from the
      **itinerary**, OWNER-only; the edit flow itself (slice 2) is what is still open here, and it
      brings the pencil *and* the calendar's bin with it. Read the plan, not this bullet. Lifted
      here from
      `archived/PrivateSocialEventPlan.md` 2026-08-21; the third item on that list, the itinerary
      entry, **shipped** — `PrivateEventItineraryEntry` is live.
- [ ] **Extract the venue-event request seam — BLOCKED until Change Private Event slice 2 ships**,
      which writes the fourth copy that makes it worth doing. Full costs-and-gains in
      `ChangePrivateEventPlan.md` **A4** (revised 2026-08-24); the short version:
      - **What duplicates:** a one-line `getLocation()` in `PlanGatheringRequest`,
        `ChangeGatheringRequest` and `PlanPrivateEventRequest` (four after slice 2), and the
        handler preamble `getLocation()` → `venueZone.resolve(getZone(), location)` → two
        `ZonedTimestamp.fromLocal(getDate().atTime(…), zone)` across the matching handlers.
      - **Extract (C), not the bare interface (B).** A `VenueEventRequest` on its own is a type with
        no client — abstraction in anticipation. Extract a helper that takes one and returns
        `(location, startsAt, endsAt)`, extending the `VenueZone` seam by one step; the interface
        then earns its keep as that helper's parameter type, which is how `HotelStayRequest` earns
        its own.
      - **The four handlers cannot merge into one.** Unlike `HotelHandler` (book+change of one
        kind), these span two kinds whose commands differ in type, id type and trailing fields.
        Only the inputs are shareable.
      - **`PlanGroundTransferHandler` stays out** — same date+times shape, but its zone comes from
        two endpoints rather than one address.
      - While you are there: `PlanPrivateEventRequest`'s Javadoc still says the shared interface is
        "deferred pending Ted's call". It is not; point it at A4.
- [x] **`/planned-private-events` list view — SHIPPED 2026-08-24**, and it outgrew this list on
      the way: it is now owned by `PlannedPrivateEventsListPlan.md`, not by this bullet. Read the
      plan, not this line. The reason it was worth more than "one more list": a private event's
      `street`, `region` and `postalCode` were carried by `PrivateEventPlanned` and read by **no
      view at all**, so the address Ted typed into the plan form went to the log and never came
      back. Lifted here from `archived/PrivateSocialEventPlan.md` 2026-08-21.
- [x] **The private-event nav card's placeholder icon — SETTLED 2026-08-24.** Ted's answer ("🍴")
      picked FA Pro `utensils`, which is the SVG the `/plan-private-event` card was already
      carrying — the placeholder turned out to be the right icon. The new `/planned-private-events`
      card uses the same one. From `archived/PrivateSocialEventPlan.md`.
- [ ] **Full travel calendar in the subscription feed (Phase 2).** Lifted from
      `archived/CalendarSubscriptionFeedPlan.md` 2026-08-21, which shipped Phase 1
      (cancel-deadline reminders) and named this as "left open (not built now)": flights, trains,
      hotel stays, gatherings and conferences served from the same feed. The assembler seam is
      already there — today it is just "the assembler returns a `List<ICalEvent>`", and per
      "no abstraction before the second user" the `ICalEventSource` interface waits for this, the
      actual second contributor. **Decide feed shape with it**, also deferred to this work: one
      feed with everything, or scoped feeds (`…/deadlines.ics` vs `…/all.ics`). Watch the token:
      the feed URL is the only credential, so widening what it serves widens what one leaked URL
      exposes.
      **Partly answered from the other side, 2026-09-08:** `web/GoogleCalendarLink` puts a
      per-entry "Add to Google" push on conferences, gatherings and private events — a plain
      pre-filled `render?action=TEMPLATE` link, no API. It is a *different* answer, not this one:
      the push is manual, one entry at a time, and lands in Google specifically, where this item
      is automatic, everything, and subscribed. The two do not conflict, but whoever picks this up
      should ask which entries still want a feed once the push exists — the deadline reminders
      clearly do (a push cannot remind), the schedule may not.
- [ ] **One open question on the problem calendar**, lifted from `archived/ProblemCalendarPlan.md`
      2026-08-21 (slices 1–5 all shipped):
      - Should a **day number link to `/itinerary?date=`**? It is a fix link, so it belongs with the
        slice-5 vocabulary — but as its own step: the day cell is not interactive today, so this
        changes the grid rather than a band.

      Answered 2026-08-21: **the calendar is now the default view**, `ProblemView.fromParam`
      falling back to `CALENDAR` for an absent or unrecognized `?view=`. The list held the default
      only to keep pre-calendar links showing the page they always had, which is a migration
      concern spent once; with the same day's colour and affordance fixes the calendar is the view
      that answers when a problem is wrong and how it sits against the trip. The list is one click
      away on the toggle.
- [ ] **Conferences have no `locationForMatching` — but ask whether that is still a problem
      (Ted, 2026-08-20).** `PlanConferenceRequest:130` always passes `null` for the venue
      `Address`, so the compact constructor falls back to the city and a conference can only ever
      match on that. A conference in **Lone Tree, CO** therefore never matches the **Denver** that
      `StaticAirportCityResolver` gives for a `DEN` flight, and `/schedule-problems` reported
      missing travel between them (found 2026-08-20). Hotels, gatherings and private events all
      expose the field on their forms; conferences do not, and there is no Change Conference flow
      to correct it after the fact.

      **Ground transfer may have removed the reason to build this.** The motivating case is now
      answered honestly: there *is* a journey from DEN to a Lone Tree venue, and
      `archived/GroundTransferPlan.md` lets Ted record it, which closes the gap. Setting the conference's
      `locationForMatching` to "Denver" would instead have **hidden a real hop** — the field
      silences the gap rather than answering it, and a silenced gap is indistinguishable from one
      that was never there. That cuts both ways: the same objection applies to using
      `locationForMatching` on a *hotel* to paper over an airport-to-suburb drive.

      What might still justify it: two nearby addresses entered under different city names with
      **no journey between them at all** — a venue and its hotel in one complex, straddling a
      municipal boundary. That is narrow, and it is not the case that raised this item.

      **So decide before building.** If it goes ahead, the shape is unchanged: add the input to the
      conference venue address (the `fragments/address-paste.html` pattern) and pass it through. If
      it does not, delete this item and say why in `Backlog.md`, so the Lone Tree case does not
      re-raise it in six months. See the item below: the same reasoning may retire the field from
      hotels too, in which case adding it to conferences would be building the thing we are removing.
- [ ] **`locationForMatching` may be droppable from hotels — and from the forms generally
      (Ted, 2026-08-20).** Raised alongside the conference item above, and the evidence is stronger
      than expected. Four facts, all checked 2026-08-20:

      1. **`ScheduleGapProjector` is its only reader.** Nothing else in `src/main` consults it; the
         controllers merely pass it through. It exists to feed one matcher.
      2. **The geocoder writes it equal to the city.** `AddressParseService:89-95` returns
         `coalesce(locality)` for *both* `city` and `locationForMatching`, so every address filled
         by the paste-and-parse widget already has the two identical.
      3. **`Address`'s compact constructor falls back to `city` when it is blank.** So an absent
         value and a geocoded value produce the same match.
      4. Therefore it only ever *does* anything when **hand-edited** — and the hand-edit is exactly
         the widen-a-suburb-to-its-metro move (Lone Tree → Denver) that ground transfer now
         answers honestly, and that hides a real journey when used.

      **What would still be lost:** normalizing two spellings of one place — "Frankfurt" vs
      "Frankfurt am Main" — which is a different job from widening, and a legitimate one. The paste
      widget already handles it by giving both addresses the same locality; only a **hand-typed**
      address can still disagree with itself. Judge whether that is worth a field.

      **Two very different scopes, and only the first is cheap:**

      - **Stop offering the input** on the five forms that expose it (`book-hotel`, `change-hotel`,
        `plan-gathering`, `change-gathering`, `plan-private-event`) and stop the fragment filling
        it. No event-schema change at all: the field stays in the payload and simply always equals
        the city. **Reversible** — put the input back and it works again.
      - **Remove the field from `Address`.** An event-schema change (R6): an upcaster, every
        golden sample, and **backup-file compatibility** — every exported backup carries it, so
        this is the kind of change to warn about before making. The field costs nothing at rest,
        so there is little to gain and a compatibility commitment to lose.

      Recommendation: do the first, live with it, and treat the second as probably-never.
- [ ] **Itinerary: add-entry day dropdown** (like the calendar's). The `/calendar` future-day
      disclosure menu lets the owner add an entry for a specific day; the itinerary has no such
      affordance. Add the same per-day "add an entry" dropdown to the itinerary so a day can be
      populated directly from that surface (OWNER-only; reuse the `DAY_MENU` pattern).
- [ ] **Action affordances that still move (general rule: they must not).** The standing rule and
      its state-vs-authorization split are in CLAUDE.md.
      **`/conferences` no longer follows it, deliberately (Ted, 2026-08-22).** The two-slot fix of
      2026-08-19 — greyed, non-interactive `Confirm` text so Decline could not move — was removed
      with slice 4 of the conference plan, which made the row's actions a *state machine*: what a
      conference offers depends on where its talk stands, and most of those moves are not
      unavailable-for-now but meaningless (`Accepted` on a conference nothing was submitted to
      names an event that could never be true). Carrying nine greyed labels on every row to hold
      positions fixed would say less, not more. That is the nuance Ted agreed to: **the state
      machine wins where the two rules disagree**, and the disable-don't-hide rule keeps its force
      wherever an action is genuinely the same action, merely not available yet. Two other places
      still have the plain moving defect and were left alone:
      - `PlannedGatheringsRenderer.actionsCell` (`PlannedGatheringsRenderer.java:157`) stacks an
        optional `Event page →` above an always-present `Edit` in a column flex
        (`.gathering-actions`, CSS at `:57`), so **Edit sits on the first line on gatherings with no
        info URL and the second line on those with one** — the link's vertical position changes row
        to row. Needs a reserved slot rather than the conferences fix (the shift is vertical, and
        this list stacks on narrow viewports).
      - `ItineraryRenderer` train card (`ItineraryRenderer.java:163`) puts the OWNER edit pencil
        after an optional service-ID span, so the **pencil slides to the start of the line on trains
        with no service id**. The neighbour is text rather than an action, but the pencil is the
        thing being aimed at.
- [ ] **A collapsed past week on `/calendar` is tappable and nothing says so.** Found 2026-09-04
      auditing for hover-only affordances after the `/conferences` CFP link was fixed
      (CLAUDE.md, "never have an affordance that relies on `:hover`"). `.calendar-week--collapsed
      { cursor: pointer; }` (`CalendarRenderer.java:233`) is the *only* always-on signal that a
      collapsed week expands when clicked — and a cursor is a pointer affordance, so **on the iPad
      there is none at all**.
      **Why it is on this list rather than in that fix:** it is the weakest remaining case, not a
      clear-cut one. Two things soften it — the `.day-badge` entry count renders on collapsed weeks
      only, so a week does say "there is something here you cannot see", and the global
      "Show/Hide past weeks" toggle is a second, fully visible route to the same content. Nobody is
      stranded. But the badge says *"3"*, not *"tap to open"*, and the rule is about the control
      being visible as a control.
      **`HoverIsNeverTheAffordanceTest` does not catch this and cannot.** Its rule is about a
      `:hover` rule that introduces `text-decoration: underline`; this is `cursor: pointer` with no
      hover rule at all, and whether an always-on affordance exists depends on markup
      (`.day-badge`) rather than CSS. A mechanical version would have to reason across both, so
      this one needs a person.
      **Shape of a fix:** a small caret in the day-label row of a collapsed week, or making the
      badge itself read as a control. Mind the standing rule that affordances never move — whatever
      goes there must occupy the same slot on expanded weeks or be absent by a state rule, not
      shift the row.

- [ ] **Surface "restart needed" after a truncate, next to the read-only banner.** `PostgresPersister
      .truncateAllTables()` (via `/admin/database/truncate`, `AdminController.java:128`) empties the
      tables, but `EventStore`'s in-memory list and every projector keep the old data — the app goes
      on serving read models for events that no longer exist, and only a restart clears it. That is
      the known stale-after-truncate bug: the live `reset()`/`rebuildFromPersistence()` rebuild was
      built and then **reverted** for the email-sender hazard (`archived/EventOrientedBackupRestorePlan.md`),
      so a restart is the fix and the app should say so. It bites Ted's standard wipe-then-import
      workflow every time. Detection looks cheap and derivable: the persisted event count (or max
      sequence) being **lower** than what `EventStore` holds in memory can only mean the tables were
      emptied underneath it. Render it like the existing read-only banner (`index.html:312`,
      `role="alert"`, model attribute from `GeneralController:62`) rather than as a post-deploy task
      — it says the data on screen is wrong *now*. Split out of `PostDeployTaskBannerPlan.md`
      (decision 4, 2026-08-19), which deliberately excludes it.
- [ ] **Extract a shared admin nav bar.** Every admin page hand-rolls its own: `admin-tasks` uses
      one shape (as did `admin-migrate-conferences`, retired in `4b9d9d4`), `admin-eventlog` another
      (`<nav><h3><a href="/admin">Admin</a> · <a href="/">JitterTravel</a></h3></nav>`), and
      `migrate-legacy-events` / `database` / `zone-audit` a third — each with its own CSS, and each
      needing the same edit when a link changes (as on 2026-08-19, when four pages had to gain a
      home link one at a time). Three pages — `admin-calendar-feed`, `admin-restore`,
      `admin-restore-success` — still have **no nav at all** and are dead ends. Extract one fragment
      (a Thymeleaf `th:replace` fragment, since these are all Thymeleaf pages) taking the current
      page's label, and apply it everywhere including the three with none. Compare
      `Page.viewNav(NavAudience, activePath)`, which already does exactly this job for the eight
      j2html view pages.
- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] **Read-only mode redirect is untested on the conference action controllers.** Both
      `ConfirmConferenceAttendanceController` and `DeclineConferenceController` catch
      `ReadOnlyModeException` and return `redirect:/read-only`, and neither slice test exercises
      that branch — the `catch` could be deleted and both suites stay green. Add a case to each
      (`willThrow(new ReadOnlyModeException(...))` on the application service, assert the redirect).
      Noticed 2026-08-19 reviewing the commitment slice; the same gap predates it on the decline
      side. Worth checking whether the other write controllers have the same hole.
- [ ] **A malformed conference id on the GET of `/conferences/{id}/confirm` (and `/decline`) is
      untested.** `lookup(...)` catches the `IllegalArgumentException` from `UUID.fromString` and
      redirects to `/conferences`; only the POST path has a `malformedConferenceIdRedirects...`
      test, so the GET-side catch is unpinned.
- [ ] **A CFP deadline cannot be cleared, so an open-space conference can keep firing reminders for
      a CFP it does not have.** `OpenCfpCommand` refuses an `OPEN_SPACE` conference, and there is no
      "retract this CFP" command at all — so once a conference with a recorded `CfpOpened` is
      re-marked open-space (the pending SoCraTes/PLoP re-marking, `Backlog.md`), the stored deadline
      is unreachable from every surface. `CfpDeadlineSource` does not filter by format, so it keeps
      putting three alarms on Ted's phone for a call for papers that does not exist. **Both surfaces
      now say so** rather than hiding it — the detail page's CFP panel and the dashboard's deadline
      line show the date unlinked (2026-09-06) — which makes it visible but not fixable. The fix is
      a `CfpWithdrawn`/`CfpRetracted` event, or making the format change itself retract one. Noticed
      in review 2026-09-06; today it is only reachable by re-marking a format, which is itself a
      capability that does not exist yet, so this is not urgent.
- [ ] **`RecordTalkController.legalOutcomes` is a second copy of the talk-side rules.**
      `ConferenceActions` says what a surface offers; `legalOutcomes` says what the catch-up page
      offers, and it is deliberately wider — "what the domain would accept" rather than "the
      expected next step". Fine as an intent, but it is a **hand-written restatement of the four
      `*TalkCommand` guards**, and nothing fails if the two drift: the domain has tests, this copy
      does not. It also ignores `commitment` entirely, which is what made `GOING`/`SUBMITTED`
      reachable while `ConferenceActions` had no way back out of it (see
      `ConferenceStateMachine.md`). Worth deriving it from the commands' own guards, or at least
      pinning it against them in a test. Noticed in review 2026-09-06.
- [ ] **The catch-up page has no link anywhere.** `RecordTalkController`'s javadoc describes reaching
      `/conferences/{id}/talk` bare, "offering every move that is legal from where the conference
      stands now" — and Ted asked for it — but every href in the app carries `?outcome=`, so the
      bare page is reachable only by editing the URL. That is a hover-rule-shaped problem in a
      different key: a capability with no visible affordance at all. Either link it (the detail
      page's action band is the obvious home) or drop the bare mode. Noticed 2026-09-06.
- [ ] **A conference's event history has no surface.** Option C from the detail-page layout round
      (`ConferenceDetailAndChangePlan.md` D6) — the conference read as a timeline, newest first —
      was the most interesting of the four and the only one that answers *how* it got here rather
      than *where* it is. Ted chose the status layout instead, and this is worth building as a
      **second** page rather than a replacement. It needs data that is not there:
      `ConferenceDetailView` carries no occurrence timestamps, because `ConferenceProjector` folds
      events and keeps only the result — so this is a projector change, not a renderer one, and it
      overlaps `EventOccurrenceTimestampsPlan.md`. Noticed 2026-09-06.
- [ ] Add event-type filtering to `/admin/eventlog` (the command-log filter is already done).
- [ ] `/admin/commandlog`'s "Out of order" badge only detects divergence *within* a page.
      `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` to `Long.MIN_VALUE` on every
      call (`PostgresPersister.java:291`), so a command whose event sequence numbers interleave
      with those of a command on the *previous* page is silently unflagged — the first entry of
      any page can never be marked. Fix means seeding `runningMaxSeq` from the max event
      sequence of all commands before the page's window rather than starting fresh. Pre-existing
      behaviour, untouched by the newest-first paging fix (`PageWindow`), which only changed
      *which* window is fetched, not how it's scanned.
- [ ] **Retire the `setState` shims on `BookHotelRequest` / `ChangeHotelRequest`.** Lifted from
      `archived/CuratedResolversToDomainPlan.md` 2026-08-23, which named it as the one thing left
      open after the `Address` alias retirement. Both are one line —
      `public void setState(String state) { this.region = state; }`, commented "backward compat for
      old exports" (`BookHotelRequest.java:45`, `ChangeHotelRequest.java:46`) — and both exist for
      the **command-export** format, which today's `BackupService` cannot read at all. The same
      measurement that retired `Address`'s `@JsonAlias("state")` applies one layer up: no restorable
      artifact carries the old spelling, and nothing in `src/main`, the templates, or the tests
      binds `state` on either request. Delete both setters and the `region` field is the only
      spelling left. **Not to be confused with** `AddressParseService.java:85`, which reads `"state"`
      out of a *geocoder* response — that is an external wire format we do not control and it stays.
- [ ] **`LocationAuditProjector` never sees a private event.** It handles `GatheringPlanned` and
      `GatheringChanged` — records of exactly the same shape — but not `PrivateEventPlanned`, so a
      private event's city and country never appear on `/admin/zone-audit`. Noticed 2026-08-30 while
      chasing the trailing-space bug above. **Probably harmless today**, which is why this is a
      cleanup and not a fix: a private event's zone is resolved at plan time and stored on its
      `ZonedTimestamp`s, and it has no legacy payload shape needing read-time resolution, so the
      audit has nothing to warn about. It is an inconsistency waiting for the day one of those
      stops being true; add the branch if you are in that file anyway.
      **Do the item below first** — if `/admin/zone-audit` goes, this item goes with it, and
      extending a superseded tool is the exact waste that rule in the header exists to prevent.
- [ ] **DECIDE: does `/admin/zone-audit` still earn its place?** Filed 2026-09-09 under the header
      rule above, which this case produced; **not yet decided, and nothing deleted.** Three
      documents already call it superseded, each from inside the record of building its replacement:
      the boot-replay preflight entry and `DEPLOYMENT.md` both say it "is not a substitute: it is
      runtime-only, and it went stale", and the dry-run validation entry says it reads `event_log`
      — data *already imported* — "which is backwards for a wipe-then-import workflow; it gave no
      warning before the 2026-08-06 production import failed on three venues". Add two known
      coverage holes: it silently missed `GatheringChanged` (`MigrationLessonsLearned.md`, where an
      edited venue would have passed the audit and killed replay) and it never sees
      `PrivateEventPlanned` (the item above). **The case against keeping it is that a green audit
      with holes is a false assurance, which is worse than no audit.** Its two replacements are
      `BootReplayPreflightTest` (pre-deploy, against a real production dump) and
      `BackupService.validateJson` (pre-import, via `/admin/restore/validate`) — between them they
      cover both directions the audit was reaching for, and neither went stale.
      **Scope if it goes:** `ZoneAuditController`, `LocationZoneAudit`, `LocationAuditProjector`,
      `admin-zone-audit.html`, the nav card in `admin-home.html:208`, two `EventSourcingConfig`
      beans, three test classes, plus mentions in `EventPayloadUpcaster`'s Javadoc and
      `BootReplayPreflightTest`. No `SecurityConfig` matcher of its own (covered by `/admin/**`).
      Two methods in the cancellation-propagation tests assert *about the audit projector*
      (`locationAuditStillReportsTheCancelledStaysLocation`) and go with it — no cancellation
      coverage is lost. Ted declined the deletion on 2026-09-09 and asked for it to be filed
      instead, so this is a decision waiting on him, not agreed work.
- [ ] **`PlanGroundTransferHandler` compares two addresses with `equals`.** `:30` rejects a transfer
      whose origin equals its destination by comparing whole `Address` records, so the record's
      generated (case-sensitive) `equals` decides — "Hamburg" and "hamburg" are two different
      places to it, and the guard lets that transfer through. Since 2026-08-30 both sides are at
      least trimmed. The comparison every other call site uses is `Place.matches`; this is the one
      that does not. Small, and nothing has hit it — the endpoints are usually picked, not typed.
- [ ] **The booked-flight change history displays the store's envelope timestamp, at UTC, unlabelled.**
      Noticed 2026-08-27 while reading the history rendering; deferred by Ted the same day. Two
      faults in `BookedFlightsProjector`, both in the string the row shows:
      `bookingEntry`/`changeEntry` (`:85`, `:90`) format `storedEvent.timestamp()` — the **event
      store's envelope** — into `ChangeEntry.displayText` ("Booked on 2026-05-20 12:22PM"), which is
      exactly what **R11** in `EventSourcingRulesHeuristics.md` forbids: a displayed time is a
      payload field, never the envelope. And `toLocal` (`:99`) converts at `ZoneOffset.UTC`, so the
      reader gets a UTC clock reading with **no zone label** — every other time on that page goes
      through `ZonedTimeTag` and carries its zone.
      The renderer is not the problem: `BookedFlightsRenderer.renderFlightCard` (`:153`) just prints
      `entry.displayText()` into the `<details>` list, so the whole fix is in the projector plus
      whatever payload field ends up carrying the occurrence time.
      **Check `EventOccurrenceTimestampsPlan.md` before starting.** If that plan puts an occurrence
      timestamp on the payloads, this is one of its consumers and fixing it standalone means doing
      the work twice.
- [ ] **Hotel forms give one sentence for two different causes.** `/book-hotel` and
      `/booked-hotels/{id}/change` catch `ZoneResolutionException` and
      `rejectValue("zone", "zoneUnresolved", …)` with *"Could not determine the time zone from the
      location — please choose one."* Note they are **already field-level** — better than the
      trains' old banner, and the smaller half of the job is done. Apply what remains of the four
      rules in CLAUDE.md, **"A rejected form reports everything it can see, under the input that
      fixes each thing"**. Follow the train implementation (`TrainEndpoints`, `InvalidTrainEntry`,
      `TrainFormErrors`).
      **A hotel is the easy half of the pattern, not a copy of it.** It has one location, not two,
      so rule 1's per-end/per-trip ordering distinction — the part that took two attempts on trains
      — does not arise at all. What *does* apply is the **cause split**, which is the change worth
      making here: `HotelHandler` needs to say whether the country box was blank (fix: type a
      country, so the error belongs on the country input) or held something no zone follows from
      (fix: pick a zone, error stays on the zone select). Today both land on the zone select, so
      the blank case points at the escape hatch instead of the fix. Plus the count banner and terse
      wording.
      The location half is already shared: `EnteredLocation.of(hotelName, address)` and
      `InvalidLocationEntry` are the same types trains use, and the terse messages landed with the
      train change — so `check(role)` can become `problem(role)` here for free, and `LocationRole`
      is already `STAY`. Also drop `required` from the hotel name/city inputs for the reason it
      went from the train forms: a browser-blocked submit leaves the previous render on screen and
      reads as "my fix changed nothing".
      Three tiers of test, as the CLAUDE.md section above this one requires: `EnteredLocationTest`,
      the command, and a `@WebMvcTest` asserting the rendered `<span class="error">`.
- [ ] **Flight forms have the trains' two-endpoint blind spot, and no country to type.** `/book-flight`
      and `/change-flight` reject globally with *"Could not determine the time zone for an airport
      — …"*, naming neither end of a flight that has two. Same four rules as the hotel item above,
      with one difference that changes the design: a flight endpoint is an **airport code**, so
      `AirportZoneResolver` either knows the code or does not, and there is no
      `COUNTRY_MISSING`/`COUNTRY_UNRECOGNISED` split to make. Every failure's only fix is "pick a
      zone", which means the whole win here is rule 1 (both ends reported at once) and rule 3
      (under the right zone select) — the cause split does not apply, and inventing one would be
      cargo-culting the train shape.
      `FlightEndpointZone` is the per-endpoint seam, the analogue of `StationZone`, so it is where
      the role gets attached. Note flights have no `EnteredLocation` check at all today (a code is
      not a venue/city pair), so there is no location/zone ordering question either — this is
      strictly the zone half.
      Do this **after** hotels: hotels exercise the cause split and flights exercise the two-end
      collection, and doing the simpler-shaped one first keeps the shared vocabulary honest.
- [ ] **`/conferences/{id}/cfp` is the one date form still on browser `required`, and
      `RequiredEntryAdvice` cannot reach it.** After `846ee9d` it is the **only** `required`
      attribute left on a date input in the tree (`open-cfp.html:107`) — the three others are the
      hotel name/city text inputs queued two items above. The advice does not cover it *by design*:
      `OpenCfpController.openCfp` binds `closesOn` as a `@RequestParam`, and
      `RequiredEntryAdvice.formBeanType` returns null for a binder with no form bean, so the
      required-fields machinery never runs.
      **Not a 500** — that is the difference from the eleven forms the advice was written for.
      `@RequestParam` defaults to `required = true` and Spring raises
      `MissingServletRequestParameterException` *after* conversion when a present-but-blank value
      converts to null, so a blank deadline is a 400 page rather than
      `ZonedTimestamp.fromLocal(null, zone)`. It is still the failure `required` was dropped from
      the train forms for, one door along: the browser bubble blocks the submit and the server
      never hears about it, and when it does hear, the answer is an error page rather than
      "Required" under the input. CLAUDE.md says a blank date is a field error **everywhere**, and
      this is the one form where it is not.
      Two ways out, and the choice is the work: give `OpenCfpRequest` a form-bean binding (it is
      already a record, so `@ModelAttribute` + constructor binding puts it inside the advice's
      reach and inside the `bindingResult.hasErrors()` shape every other POST now has), or teach
      the advice about `@RequestParam` date parameters. Prefer the first — it is the shape the app
      already has eleven times, and the second means the advice knowing about parameters that have
      no `<span class="error">` to render into.
      Whichever, the `required` attribute goes, and `open-cfp.html` needs an error span for the
      deadline. Pin it in `RequiredEntryConventionTest` alongside the gathering and hotel cases.

Not listed, and a decision rather than an oversight: **gatherings, conferences, private events and
ground transfer** have the same banner. They are lower-traffic entry surfaces, and ground transfer
picks its endpoints from a dropdown rather than typing them, so its zone failure is a different
problem. Promote them if one of them actually bites.

- [ ] **Stable `data-testid` attributes on the `index.html` nav groups.** Lifted from `Backlog.md`
      2026-09-08, where it had lived since `archived/GeneralControllerRefactorPlan.md` was archived —
      it was in a "loose follow-ups not tracked anywhere else" list that no longer exists, this file
      now being where small work lives. The authorization tests assert on `href` substrings and on
      the literal `>Admin</span>`: `SecurityAuthorizationTest:101` and `:205` are
      `doesNotContain(">Admin</span>")`, which is precisely the too-loose absence assertion
      CLAUDE.md's precise-HTML rule warns about — rename the label or restructure the group and the
      claim passes for the wrong reason, on a **security** test. Testids would let each assertion
      name the group it means. `index.html` plus the two test classes.

- [ ] **The shared renderer infrastructure the j2html migration proposed was never extracted.**
      Lifted from `Backlog.md` 2026-09-08, from `archived/j2html_Migration_Analysis.md`. There is no
      `TemporalFormatter`, `ProblemCardRenderer` or `EntryCardRenderer`; `web/Page.java` (plus
      `PageWindow`) is the only shared piece across **11** `*Renderer` classes, so formatting and
      card markup are duplicated between them. Overlaps `Refactoring_Opportunities.md` §2, §6 and §7,
      which measure the same duplication from the projector/template side — read it first, and do
      not treat this as a separate programme. Deliberately not queued: CLAUDE.md's standing rule is
      no abstraction before a second user, and the case for each extraction has to be made on the
      duplication that exists now, one renderer pair at a time.

## Deferred (until needed)

Items with a known shape and a named trigger, deliberately **not** queued: the cost of carrying
them is a paragraph, and building any one of them now would be work ahead of a need. Move an item up
to **Open** when its trigger fires — do not treat this section as a backlog to work down.
(Said "either one" until 2026-09-08, from when the section held two; it has grown since, and the
count is deliberately not stated here so it cannot go stale again.)

- [ ] **The near-term empty lane band, 120px → 80px.** Lifted from `archived/QuietWeekRunsPlan.md`
      2026-08-31. Ted proposed it there and it was neither taken nor refused; the plan it belonged to
      was then reverted, so this is now a standalone question about the linear calendar's density.
      Against it: that window is where Ted still plans by tapping a day, and
      `--calendar-empty-band-min-height` exists so an empty week reads as open space rather than a
      thin strip of dates.
      **Trigger:** the linear calendar feeling too airy once the year overview is carrying the
      "sense of things" job. One token in `CalendarRenderer`.

- [ ] **No way to change a conference.** **Now owned by `ConferenceDetailAndChangePlan.md`
      (planned 2026-09-04, slice 3)** — keep this entry only until that plan ships, then delete it
      rather than ticking it, since the plan is the record. The *view* half it also answered is
      **done**: the venue shipped on `/conferences` 2026-09-04 (`b380f0b`) and `/conferences/{id}`
      shipped 2026-09-05, so what is left here is exactly the edit half named below.
      Lifted from `archived/ConferenceSubmissionTrackingPlan.md`
      2026-08-23, when that plan was archived — it names this gap and nothing else tracked it.
      There is no `ChangeConferenceController` to match `ChangeGathering`, so a conference's name,
      dates, venue and **`infoUrl`** are set once at plan time and cannot be corrected. Known and
      accepted when `infoUrl` was placed on `ConferencePlanned` (slice 4b, 2026-08-22): the CFP half
      has a repair path (`/conferences/{id}/cfp`, which re-records and replaces), and the conference
      half has none. Related: the `locationForMatching` item above says the same thing from the
      other end — a conference cannot be corrected after the fact either way.
      **Trigger:** Ted needing to fix a conference he has already entered — a moved venue, a
      corrected date, or an `infoUrl` he did not have when he planned it. Cancel-and-re-enter is not
      the workaround it is for a ground transfer, because a conference carries a CFP, a talk
      pipeline and a commitment that would all have to be re-recorded.
- [ ] **A conference with two separate CFPs.** Raised by Ted 2026-09-08: JavaLand runs a
      *training day* call for proposals (deadline 1 Sept, submitted) alongside the regular
      conference CFP, which is still open and not yet submitted to. The model has no room for the
      second track, in three places:

      - **One CFP per conference.** `CfpOpened` is keyed by `ConferenceId` and the fold takes the
        last one (`ConferenceProjector`, `withCfp`) — deliberately, since re-recording is how a
        moved deadline is corrected. So recording the regular CFP **silently overwrites** the
        training day's deadline and `submissionUrl`. `CfpDeadlineSource` is one-per-conference to
        match: its uid is `{id}-cfp@jittertravel` off a single `view.cfpClosesOn()`, so one of the
        two deadlines gets no alarms.
      - **One speaking status per conference.** `SpeakingStatus` names this exact case as the cost
        the conference-keyed design accepted — *"two proposals with different outcomes can only be
        recorded as one… per-talk state is the change to make if that ever bites"*. It is biting.
      - **No state for "submitted on one track, not the other."** `ConferenceActions` picks a row's
        moves from a single `speakingStatus`, so the row offers `Accepted · Rejected · Withdrawn`
        while half the story is still `Submitted · Ticket Bought · Decline`.

      **The sharpest consequence is a wrong public badge.** Training day accepted, main talk
      rejected folds `ACCEPTED` then, last-wins, `REJECTED` — and `ConferenceProgress.speaking()`
      answers **false** for `REJECTED`, so the "A Ted Talk" badge disappears from a conference Ted
      genuinely speaks at. Not reachable from any surface (`ConferenceActions` offers only
      `Withdrawn` from `ACCEPTED`), but `RejectTalkCommand` refuses only `NOT_SPEAKING` and
      `INVITED`, so `/conferences/{id}/talk?outcome=REJECTED` typed by hand does it.

      **Workaround, and it is a good one: two conference entries.** "JavaLand Training Day" as its
      own conference with `ACCEPTANCE_REQUIRED`, and JavaLand itself as `CALL_FOR_PAPERS`. Each gets
      its own deadline, alarms, pipeline and badge, for zero code — and the format does the right
      thing by itself, since a rejected training-day proposal drops that entry off both calendars
      and leaves the conference untouched. Verified nothing objects to two conferences on
      overlapping or adjacent days: `ScheduleGapProjector.overlappingOccupancies()` walks gatherings
      and private events only, and `differentCityConflicts()` is gathering↔conference and same-city
      here anyway. The costs are two rows on `/conferences` that Ted has to remember are one trip,
      and the entry **name being public** — a stranger reads "JavaLand Training Day — Maybe" on
      `/calendar`, which discloses no more than any watched conference does, but is a name Ted chose
      to publish.

      **The real fix is a track key on the CFP and submission axes** — the per-talk state the
      submission-tracking plan deferred. It ripples: a `CfpOpened` schema bump, `ConferenceView`
      going plural on `cfpClosesOn`/`cfpSubmissionUrl`, a uid suffix in `CfpDeadlineSource`,
      `ConferenceProgress` holding a map instead of a status, `speaking()` becoming "any track
      accepted", and `ConferenceActions` going per-track — which also breaks its three-move budget
      and the dashboard's fixed 240px Actions column.
      **Trigger:** a *second* conference running two CFPs, or one where the two tracks resolve
      differently and the two-entry workaround has already been used and found wanting. One rare
      instance is not a second user (CLAUDE.md, "no abstraction before a second user").

- [ ] **The hotel zone divergence on the transfer submit path.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23, which named it and deliberately
      left it alone. `GroundTransferEndpointResolver.hotelEndpoint` calls
      `locationZones.resolve(hotel.address())` at submit time although `HotelBooked.checkIn()`
      already carries the zone resolved at booking: two sources for one fact, and the **only** reason
      `ZoneResolutionException` is reachable on this path at all. Slice 3 showed what the fixed shape
      looks like — the `train:` branch reads the zone off the trip's own `ZonedTimestamp` and cannot
      fail — so this is now the odd one out rather than the norm. Doing it means (a) an audit of the
      kind `LocationZoneAudit` already models, asserting the two agree for every hotel in the log,
      then (b) deciding whether `PlanGroundTransferController`'s now-unreachable `catch` and its "fix
      the hotel's address first" copy get deleted. **Trigger:** a transfer whose hotel end is stamped
      in a zone that disagrees with the stay beside it, or the next time that `catch` has to be
      reasoned about. Related: `HotelDetailsView` drops the zone the same way `TrainDetailsView` used
      to, which is the actual mechanism.
- [ ] **Conference and gathering venues as ground-transfer endpoints.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23. `GroundTransferEndpointChoices`
      documented the hole as two things — "a train station, a conference venue" — and slice 3 closed
      the first half only. Same shape as the train work: a `TransferEnd` pair, arms in
      `TransferEndpointProjector`, a `venue:` branch in the resolver, optgroups. **Do the record
      reshape first** (below) if this one is taken up. **Trigger:** a missing-travel gap that starts
      or ends at a venue Ted has to be driven to. A venue name is *public* where a hotel's and a
      station's are private, so the redaction question is the opposite one and worth asking before
      building.
- [ ] **Reshape `GroundTransferEndpointChoices` around `TransferEnd`.** Noticed 2026-08-23 while
      shipping slice 3: the record is six positional `List`s, and adding trains meant editing its
      construction in five test files. A `nothing()` factory and a test-local helper absorbed that
      round, but a third kind would do it again. The only thing holding the current shape is that
      `plan-ground-transfer.html` reads the lists by name (`endpointChoices.trainArrivals`), so the
      reshape is a template change too. **Trigger:** the venue item above, or any third endpoint
      kind — not worth doing for its own sake at two kinds.
- [ ] **Carry `Place` through `ScheduleProblem` and the renderers.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23 (its D2). `MissingTravel.fromCity()`
      / `toCity()` and `ScheduleTimeline.Movement`/`Stay`/`Occupancy` still hold plain strings, so
      `GroundTransferEndpointChoices` re-wraps them (`new Place(option.city())`) to compare. D2's
      reasoning still holds and is why this is deferred rather than open: the thing that had to agree
      is *which field of which event becomes the place*, and that is now written once, so threading
      the type deeper touches `ProblemRef`, `ProblemBand`, `ScheduleProblemsRenderer`,
      `ProblemCalendarViewBuilder` and the itinerary for no additional guarantee. **Trigger:** a
      second comparison that has to case-fold by hand, or a bug traced to one of those strings being
      compared with `equals`.
- [ ] **Ground-transfer endpoint prefill from a fix link.** Lifted from
      `archived/ProblemContextOnFixPagesPlan.md` 2026-08-23, when that doc was archived — it was
      the one piece of future work that doc still named, and nothing else tracked it. Today
      `/plan-ground-transfer` receives only `?date=` from a fix link, because the gap knows
      **cities** while the form takes **endpoint tokens** (`airport:DEN`, `hotel:<bookingId>`), and
      one city maps to zero, one or many of them. **Why this is not a wasted click if it guesses
      wrong:** preselecting the wrong endpoint writes a `GroundTransferPlanned` event that *removes
      the very gap it was entered to close* — the failure hides itself. The safe shape is named in
      both docs: preselect only on an unambiguous single match, group the candidates when there are
      several, say so when there are none. Reasoning is D13 in `archived/GroundTransferPlan.md`.
      **Trigger:** Ted following a travel-gap fix link to `/plan-ground-transfer` often enough that
      re-picking both ends annoys — most likely alongside the Change-a-ground-transfer item above.
- [ ] **Private events in `DifferentCityConflict`** — tabled by Ted 2026-08-20, and it outlived the
      slice it was pencilled into: it was to ride along with problem-calendar slice 4, but slice 4
      shipped 2026-08-20 as clash *markers* only, so this now has no home but this list.
      **What is wrong:** detection is already indifferent to what kind of thing a conflicting entry
      is, but the *clearing* event types its subject as a `GatheringId`, so a private event in the
      wrong city cannot be reported (it would be unclearable, and an unclearable row is worse than
      no row). **The work is additive:** a `PrivateEventCityConflictCleared` event, or a one-of
      subject on the existing one, plus the detector branch — no schema change to anything stored.
      **Trigger:** Ted plans a private event on a day a conference runs elsewhere and wants the
      clash surfaced. Until then `/schedule-problems` is quietly incomplete in one direction only,
      which is the safe direction — it under-reports rather than reporting something he cannot act
      on. See `archived/ScheduleProblemsRewritePlan.md` and `archived/PrivateSocialEventPlan.md`.
- [ ] **`GET /logout` is still a 404** — the remnant of a larger item, most of which **shipped
      2026-09-08**; see "Sign out, from the top-left of the home page" in **Done**. What is left is
      only the bare URL: typing `/logout` in the address bar 404s, because with CSRF on
      `LogoutFilter` matches POST and the generated confirm page is gone (below). Nothing links to
      it, so nothing depends on it.
      **What is true today**: `POST /logout` works, lands on `/login?logout`, and is reachable from
      a **Sign out** button on `/` for anyone signed in.
      **Why it went:** `.formLogin(form -> form.loginPage("/login"))` arrived in `0435623` with the
      custom login page. A custom login page makes Spring Security drop
      `DefaultLoginPageGeneratingFilter`, and the *same* configurer registers
      `DefaultLogoutPageGeneratingFilter` — which is what used to serve `GET /logout` as a generated
      "are you sure?" form that POSTed back with the CSRF token. With CSRF on, `LogoutFilter` matches
      **POST only**, so losing the generated page left `GET /logout` unmapped. Nothing was
      misconfigured; the way to *reach* logout was collateral.
      **Do not suggest driving `POST /logout` from the console as a workaround:** the CSRF cookie is
      deliberately `httpOnly(true)`, so page scripts cannot read a token to submit and `CsrfFilter`
      rejects it. That is the cookie working as designed.
      **The work, if it ever lands:** restoring `GET /logout` means writing a confirm page, since
      the generated one is not coming back while the login page is custom — and a confirm page for
      an action that is already one button away is ceremony. So this stays deferred on purpose.
      **Trigger:** somebody actually types `/logout` and is confused by the 404, which now means
      somebody who has not seen the button.
- [ ] **Change a ground transfer** — the other half of D11 in `archived/GroundTransferPlan.md`. Cancel
      shipped 2026-08-20 and took the urgency with it: correcting a transfer is now
      cancel-then-enter, two forms instead of one, and **nothing is lost in the round trip** —
      both ends are snapshots in the event by design, so there is no live reference an edit would
      preserve. **The work, if it ever lands:** a `GroundTransferChanged` full-snapshot event
      (mirroring `HotelChanged`/`TrainChanged`), a change command + handler reusing
      `GroundTransferEndpointResolver` wholesale, a form that is the plan form with its fields
      hydrated, and `put`-branches in the four projectors that already handle `Planned`.
      **Trigger:** Ted finds himself re-entering the same transfer often enough to notice — most
      likely if a mode/notes field ever arrives (D7), since that is the kind of detail you edit
      rather than re-type. Not before.
- [ ] **Editing a hotel's check-in earlier than its existing `cancelBy` fails on a field nobody
      touched.** Lifted from `Backlog.md` 2026-09-08, where it had sat since the Phase 1 `cancelBy`
      review at the bottom of `HotelCancelReplacePlan.md`. `ChangeHotelCommand` (the `cancelBy` guard,
      `:45`) refuses a deadline after check-in — correct in itself — but `change-hotel.html` prefills
      `cancelBy` from the existing booking, so moving check-in earlier rejects a value Ted did not
      edit, and the message names the deadline rather than the date he changed. **Recorded as
      accepted behaviour, not a bug:** the alternative is clamping `cancelBy` to the new check-in,
      which silently rewrites a deadline, and refusing is the safer of the two.
      **Trigger:** Ted actually hits it and finds the refusal unhelpful — at which point the answer
      is probably a field-level message on `checkIn` naming the deadline, not clamping. (The other
      two findings from that review — the wrong cancel-by hint text and a duplicated
      `cancelBy(LocalDateTime, ZoneId)` helper — were fixed inside `4efccaf` before it was committed;
      the review had been written against the pre-fix working tree.)
- [ ] **Externalize the auth `HttpSession` for multi-instance.** Lifted 2026-09-08 out of the
      **done** `Login "have to sign in twice"` item (2026-08-18), whose closing sentence was the only
      record of it anywhere in the tree — a deferral buried in a ticked box is a deferral nobody can
      find, which is exactly how Ted came to go looking for it. Unchanged in substance: the
      CSRF-cookie fix is **single-instance only**; running more than one replica also needs the
      authentication session off the heap — Spring Session JDBC on the existing Postgres
      (`spring-session-jdbc`, its two tables added to `schema.sql` the way everything else is).
      **This is not the fix for being signed out by a restart** — that shipped 2026-09-08 as
      remember-me with persistent tokens (see **Done**), and it also covers the idle timeout, which
      a persisted session would not. Externalizing the session would survive a restart as a side
      effect, but it is a much larger change to get there and it is not the reason you would do it. The other half of going
      multi-instance is `CommandConsistencyEventStore.md` (the in-memory `EventStore` and a
      conditional append); it does not mention sessions, so neither doc is complete on its own.
      **Trigger:** actually running more than one replica.

## Done

- [x] **Every reading on the cookie probe carries a verdict** (2026-09-09). Ted, on being shown the
      raw values: *"showing me values without knowing if they're good or bad is useless."* The case
      that proves it is `X-Forwarded-Proto (none)` on a **healthy** deploy — it sits beside two green
      values looking like a gap, and is in fact the evidence the thing worked. With
      `server.forward-headers-strategy=framework`, Spring wraps the request in a
      `ForwardedHeaderExtractingRequest extends ForwardedHeaderRemovingRequest`, which hides all
      seven forwarded headers from `getHeader` **after** applying them; a controller runs downstream,
      so while the strategy works that value is *always* blank. A reader could have "fixed" that gap
      and broken a working config. Verified against `spring-web-6.2.6`, not from memory.
      `SecureCookieProbe` now derives one `Outcome` from the two readings and switches over it
      exhaustively for every sentence — so the lines cannot disagree, and a fifth state will not
      compile until each says what it reads in it. The template **loops** over
      `ProbeValue(label, value, verdict)` rather than naming the three values, which is what makes
      "a value always has a verdict" structural; `everyReadingCarriesAVerdictInEveryState` drives all
      four states and pins that none is blank. Two wording fixes in the same pass: the `forwardedProto`
      Javadoc said blank meant the proxy had stopped sending it and now names all three causes, and
      the `!secure && blank` branch no longer asserts "the proxy is not sending one" — a header
      saying `http` is applied *and* stripped too, so blank cannot support that claim (there is a
      `doesNotContain` on the old wording). Also `text-align: left` on `.probe-values`: the page
      centres its nav cards and that inherited in, so the three readings never formed a column —
      caught by screenshotting the real rendered response at 820px, not by reading the CSS. Both new
      assertions mutation-verified (blanking a verdict reddens 3 tests incl. the render test).
      **The general rule is now in memory, and the other admin diagnostics have not been swept for
      it** — `/admin/database` table stats and `/admin/zone-audit` are the candidates, the latter
      only if it survives the DECIDE item above.
- [x] **Sign out, from the top-left of the home page** (2026-09-08). Deferred since 2026-08-21 on
      "incognito is sufficient", and remember-me is what fired the trigger: a persistent cookie in
      a private window dies with the window, so the device that actually stays signed in is the
      normal browser and the iPad — and `POST /logout`, the only thing that revokes it, had no
      affordance anywhere. Ted asked for the button in the same breath as the feature.

      **Where and what.** Top-left of `/`, opposite the local badge's top-right, in **normal flow**
      rather than `position: fixed` so it can never land on top of the read-only, tasks or pending
      banners underneath it. A Thymeleaf POST form — CSRF is on, `LogoutFilter` matches POST only,
      and `th:action` supplies the token; j2html renderers stay uncoupled from Spring MVC's CSRF
      per the standing split. 44px minimum height, because the iPad has no pointer to aim with.

      **Not red**, and that is the colour rule doing its job rather than an oversight: signing out
      destroys nothing and you can sign back in, so it is not what red is reserved for. No typed
      word either, for the same reason.

      **Absent for anonymous, not greyed.** `signedIn` comes from `request.getRemoteUser() != null`
      — not from the two role flags, so a future role gets signed out rather than stranded. A
      stranger could never trigger it, and a visible-but-dead control would tell them an account
      exists here; the affordances rule greys what a viewer could trigger later and hides what they
      never could, and redaction wins where the two appear to disagree.

      **Revocation came free.** `AbstractRememberMeServices` is a `LogoutHandler`, so the same POST
      deletes this device's `persistent_logins` row and cancels the cookie. No new code, and it is
      why the button closes the gap the remember-me work opened rather than merely covering it.

      Three tests, mutation-verified (dropped the `th:if` → the anonymous case went red; narrowed
      `signedIn` to OWNER → the family case did). One thing worth knowing for the next such
      assertion: the anonymous check asserts the **whole button element**, because `.signout-button`
      lives in this page's inlined `<style>` and therefore ships to every viewer — a
      `doesNotContain("signout-button")` fails on the stylesheet and never reaches the markup.
      Rendered headlessly at the iPad's 820px to confirm placement.

      **Left undone deliberately:** `GET /logout` is still a 404 (see **Deferred**), and signing out
      does not clear the 400-day `viewerZone` cookie — a zone is not a credential.

- [x] **Staying logged in across a restart** (2026-09-08). Raised when Ted went looking for the
      deferral and could not find it — the only record was a trailing sentence inside the **done**
      `Login "have to sign in twice"` item, and that sentence defers the *multi-instance* problem
      (still in **Deferred**), not this one. The CSRF-cookie fix made the first login after a
      restart *succeed*; it did nothing to keep anyone logged in, so every redeploy and every
      devtools restart signed Ted out.

      **Built as Spring Security remember-me with persistent tokens**, decided over Spring Session
      JDBC after checking what the session actually holds: there is no `HttpSession` use anywhere in
      `src/main`, `viewerZone` is a cookie, CSRF moved to a cookie in the 2026-08-18 fix, and the
      only contents are the `SecurityContext`, the saved request, and flash attributes living for
      one redirect. **Nothing in that session was worth persisting except the authentication
      itself**, which is exactly and only what remember-me persists — so Session JDBC would have
      added a dependency and a serialized-blob table to save the one thing in the box. It also would
      not have fixed the whole complaint on its own: the session timeout is 30 minutes and
      unconfigured, so it survives a redeploy but not an idle afternoon.

      **Persistent tokens, never the hash-based variant, and this is the trap worth remembering.**
      `TokenBasedRememberMeServices` signs `username:expiry:password:key` with the **encoded**
      password, and `userDetailsService` BCrypt-encodes `TED_PASSWORD` at every startup with a fresh
      salt — so the signature would stop matching on the very restart the cookie exists to survive.
      `PersistentTokenBasedRememberMeServices` + `JdbcTokenRepositoryImpl` depends on no hash,
      rotates the token on every use (so a replayed cookie reads as theft), and makes revoking one
      device a `DELETE`. Verified on the classpath first: Boot 4.0.7 → Security 7.0.6 has all of it,
      so **no new Maven dependency**.

      **As shipped.** `persistent_logins` added to `schema.sql` as `CREATE TABLE IF NOT EXISTS`
      (`spring.sql.init.mode=always` runs it on boot, so no manual migration on Railway) with
      Spring's own column types verbatim — the one table here that is deliberately not
      `TIMESTAMP WITH TIME ZONE`, because `JdbcTokenRepositoryImpl` binds a `java.util.Date`. Two
      beans in `SecurityConfig` and `.rememberMe(...)` on the chain; the DSL takes the key from the
      services (`RememberMeConfigurer.getKey()`), so `REMEMBER_ME_KEY` is stated once. **30 days**
      (Ted): each use rotates the token and rewrites `last_used`, so the window only reaches an idle
      device. **A checkbox, ticked by default** (Ted) — staying signed in is the point, and
      unticking it is the only "not on this device" control the app has. **FAMILY and OWNER both**
      (Ted), and the Danger Zone was deliberately **not** put behind `fullyAuthenticated()`.
      Revocation needs no code: `AbstractRememberMeServices` is a `LogoutHandler`, so `POST /logout`
      already deletes the series and cancels the cookie.

      **`server.forward-headers-strategy=framework`, and it is not incidental.** Railway terminates
      TLS and speaks plain HTTP to the container, so without it `request.isSecure()` is false in
      production and the remember-me cookie — a credential — ships unmarked. `useSecureCookie(true)`
      was rejected as the fix: it would be silently dropped by the browser over the plain-http local
      prod-preview, so the feature would look broken locally while being fine deployed. The strategy
      is correct in both. It also repairs the `viewerZone` cookie's Secure flag, which had the same
      `request.isSecure()` line and was very likely shipping unmarked in production all along.

      **`/admin` grew a `SecureCookieProbe`** because that is the one claim a local run cannot
      check. It leads with the derived answer ("Cookies on this request are marked Secure") over the
      raw `isSecure()` / scheme / `X-Forwarded-Proto` beneath, and distinguishes *no header arrived*
      from *header arrived and was ignored* — the two causes are indistinguishable from the summary
      line, and they have different fixes. Deliberately **uncoloured**: "not secure" is the correct
      answer over local http, so amber there would cry wolf daily and stop being read in production,
      where it is the only thing that matters.

      Six `@WebMvcTest` classes importing `SecurityConfig` gained a `@MockitoBean
      PersistentTokenRepository` and a `REMEMBER_ME_KEY` property — a slice has no `DataSource`, so
      the JDBC store cannot be built there. Four new tests, all mutation-verified: the ticked
      checkbox, the 30-day HttpOnly cookie, that unticking suppresses it, and the probe's branches.
      Local `REMEMBER_ME_KEY` stand-in added to `application-prod-preview.properties` — **required**,
      since a random per-boot key would fail the local restart test for the wrong reason and look
      exactly like the feature not working.

      **The revocation gap this opened was closed the same day** — see "Sign out, from the top-left
      of the home page" above. Still unverified until deployed: that production actually reports
      secure. The probe is on `/admin` for exactly that check.

      **Two gaps found on 2026-09-09, before the push, when Ted went looking for the Railway
      variable and found nothing telling him about it.** Both now fixed.

      **First: it was never written down where deployment is written down.** This item recorded the
      variable and `application-prod-preview.properties` supplies a local stand-in, but
      `DEPLOYMENT.md`'s "Application secrets" table was untouched — and the same audit found
      `CALENDAR_FEED_TOKEN` and `JITTERTRAVEL_BASE_URL` missing from it too, so this was a habit and
      not a slip. All three added. The fix for the habit is **`Pre-Push-Tasks.md`** (repo root), a
      checklist of manual, outside-the-repo setup that unpushed commits are waiting on, plus a
      CLAUDE.md rule ("A change needing manual setup outside the repo writes itself into
      `Pre-Push-Tasks.md`") saying a new variable goes in **both** files, because they answer
      different questions: the table is the standing description of a configured instance and stays
      true forever, a box is a one-shot instruction that stops being true when it is ticked.
      `DEPLOYMENT.md` names the checklist as the fourth pre-push gate, beside the two automatic ones
      and the boot-replay preflight. **Deliberately not mechanized:** the hook cannot tell a
      genuinely done box from an unticked one, and a gate that has to be overridden routinely is the
      docs gate's `DOCS_OK=1` all over again.

      **Second: the bean's javadoc was wrong about the mechanism**, saying `REMEMBER_ME_KEY` "signs
      every remember-me cookie". True of `TokenBasedRememberMeServices`, not of the persistent
      variant we chose: checked against Spring Security 7.0.6's sources, our cookie is a random
      series + token from `SecureRandom` held in `persistent_logins`, and `AbstractRememberMeServices`
      passes the key only to the `RememberMeAuthenticationToken` it mints (line 198), whose
      `key.hashCode()` — 32 bits — `RememberMeAuthenticationProvider` compares. So the *consequences*
      the javadoc listed were right (stable across restarts, changing it revokes every device) while
      the reason was wrong, which matters because it overstates the secrecy and understates the
      stability — and stability is the whole property. Corrected in the javadoc and explained under
      "`REMEMBER_ME_KEY`: stability matters more than secrecy" in `DEPLOYMENT.md`.

- [x] **A city typed with a trailing space was a different city** (2026-08-30). Ted planned a
      private event in Hamburg from an iPhone; the space bar that committed an autocorrect
      suggestion left `"Hamburg "` in `city`, `country` and `locationForMatching` (production event
      92). Nothing between the keyboard and the event log removes it — `type="text"` submits its
      value verbatim — and the schedule's comparison is exact apart from case, so
      `/schedule-problems` reported **"No travel — Hamburg → Hamburg"** plus a missing hotel for two
      nights the Hamburg booking already covered. HTML collapses the space, so both ends of the
      phantom gap rendered as the same word.
      **Fixed by normalizing where the string lands, not where it is typed:** `Address` and
      `TrainStationAddress` trim (and null-guard) every field in their compact constructors, and
      `Place` trims as the last net. Because Jackson binds stored payloads through those
      constructors, event 92 reads clean on every replay — **no migration, no data re-entry, and
      the stored JSON is untouched**, so backup files stay byte-compatible. The two comparisons
      that were exact-but-untrimmed were fixed too: `HomeCities.sameLocation` (whose neighbour
      `includes` had trimmed all along — that inconsistency *was* the bug) and the raw
      `equalsIgnoreCase` in `ScheduleGapProjector.differentCityConflicts`.
      Pinned by `AddressTest`, `TrainStationAddressTest`, additions to `PlaceTest` and
      `HomeCitiesTest`, a golden sample carrying the real dirty payload
      (`GoldenEventDeserializationTest.addressFieldsAreTrimmedWhenAStoredPayloadIsRead`), and the
      regression where it was visible — `ScheduleGapProjectorTest.LocationsTypedWithStrayWhitespace`.
      **What this does not catch:** `trim()` leaves U+00A0 non-breaking space alone, and nothing
      sees homoglyphs — the SoCraTes UK venue's street starts with a **Cyrillic М**
      (`"Мilton Hill, Steventon, "`, event 5). Streets are never matched on, so nothing depends on
      it; a normalizer for one event was not worth building.

- [x] **The same space, on every other field: trimmed at the boundary** (2026-08-30, same day).
      Ted's question after the fix above — what about a hotel name, a venue name, the fields nothing
      compares *yet*? An audit of all 19 form requests and every event's `String` components found
      no comparison outside the places already fixed (airport codes and zones fail loudly instead;
      every enum `fromParam` already trims), so the exposure was cosmetic — but "nothing compares
      it" is a property that changes silently, and bad text outlives the form in every backup.
      `TrimTypedTextAdvice` is a `@ControllerAdvice` registering `StringTrimmerEditor(false)`: every
      `String` bound from a form or query parameter is trimmed, on every controller, **including
      forms nobody has written yet** — which is the point, versus normalizing a dozen event records
      one field at a time. `false` keeps `""` as `""`, so the no-null-Strings rule is untouched.
      Pinned by `TrimmedTypedTextConventionTest` through two real controllers, one `@ModelAttribute`
      and one `@RequestParam`, because the advice is invisible at every call site — the arrangement
      `ProblemContextAdvice`/`ProblemContextFragmentConventionTest` already uses.
      **Two knock-ons, both agreed:** `" DELETE "` now opens the Danger Zone (the word proves intent,
      not typing precision — CLAUDE.md says so at the gate), and the three renderers that guarded
      optional text with `isEmpty()` now use `isBlank()`, so whitespace-only text reads as absent
      instead of rendering an empty chip or a link to `" "` (`BookedTrainsRenderer` ×2,
      `ScheduleProblemsRenderer`). Two others in that family — `ItineraryRenderer:302`,
      `ConferencesRenderer:561` — were fixed for free by the `Address` change above.
      **The division of labour this settles:** the boundary trims everything *typed*; a record
      normalizes everything *compared*. Both are needed, because a restore binds stored JSON through
      Jackson and never passes the boundary — which is exactly how event 92 gets repaired.

- [x] **Itinerary: where he is on an eventless day** (2026-08-21). A stay produces only Check-In
      and Check-Out entries, so every night in between rendered as "Nothing scheduled" — the
      itinerary went blank precisely on the days Ted is somewhere. A day with **no entries at
      all** now renders a `.whereabouts` row in that slot instead, on a tint far lighter than the
      lodging card's (`#f0fdf4` against `#dcfce7`) and with no left border, so it reads as context
      rather than as something scheduled. Two shapes:
      - **In a stay** — two lines, hotel icon: `In Frankfurt, DE` over `Grand Hotel Frankfurt`.
        (Shipped first as a one-liner joined with a `·`; it wrapped in a third-of-a-column, so
        Ted asked for the compact 2-liner. `OngoingStay.label()` became `locationLabel()` and the
        hotel name moved to its own line.)
      - **Away with no bed** — two lines and a fix link, in **amber**: `In Denver` over
        `No hotel booked`, then `Book hotel →`. Amber because a schedule problem to look at is
        work waiting and recoverable; green is for a night that is sorted. (`/schedule-problems`
        colours its own cards by problem *kind* — blue for hotel — but there every card is a
        problem, while here the row has to stand out from the settled days beside it.)
      - **At home** — one line, house icon in the same lodging green: `You’re Home`.

      Shape: `OngoingStay(hotelName, city, country)` view record;
      `ItineraryProjector.ongoingStayOn(date)` derived from the hotel entries already held (no new
      state — the check-in entry of every stay whose check-in day is *before* and check-out day
      *after* the date, earliest check-in winning if stays overlap);
      `ScheduleGapProjector.missingHotelOn(date)` and `.atHomeOn(date)` for the other two.
      `ItineraryDay` carries all three as separate fields, because two of them can hold at once (a
      hotel booked in a home city is both a stay and a night at home); the renderer picks, most
      specific first: **stay → no-bed → home → nothing scheduled**.

      **The no-bed row is answered from the `MissingHotel` read model, not from a raw location
      lookup.** That is what makes it say exactly what `/schedule-problems` says, with the same
      dates behind the same link: the fix links come from `ProblemFix.forProblem`, whose whole
      point is being the one mapping every view reads, so the itinerary is now its third reader
      (after the problems list and the problem calendar). Rendered as plain links rather than the
      report's disclosure menu — a missing hotel has exactly one fix, and a menu holding one item
      is a worse door — and **OWNER-only**, since `/book-hotel` is a form family could never
      submit. It also inherits the night sweep's two exclusions for free: a night at home and a
      night in transit demand no bed, so neither is ever mislabelled. The run is half-open,
      `[checkIn, checkOut)`: the checkout day is the morning he leaves.

      **It cost both problem views their one-item menus, and the calendar its blue.** Ted spotted
      that the "a menu holding one item is a worse door" argument condemned `Fix ▾` on the two
      single-answer cards, then stated the rule behind it — *a dropdown only above three choices,
      or where space is constrained; if unsure, ask* — and, reviewing the calendar, two more
      problems with it. All three are now in CLAUDE.md and shipped the same day:
      - `ScheduleProblemsRenderer.fixSlot` renders **up to three fixes as links**, above three a
        menu. That leaves one menu on the page: a hotel booked four ways.
      - **Calendar bands are all amber now.** Ted missed a run of missing hotels because they were
        blue while travel gaps were amber; every band shares `--pc-problem-bg` and keeps its kind
        only as the 4px left edge. This also retired the last red *fill* on that view, which the
        colour law reserves for the irreversible.
      - **Calendar bands say they are clickable.** One answer makes the whole band a link; several
        keep the menu (a week-grid cell is genuinely constrained); either way a `.pc-band-fix` chip
        names the action or announces the menu. The whole band being a silent click target was a
        hidden affordance — "knowing I have to click" is not one.

      F9 in `archived/ProblemCalendarPlan.md` is annotated as superseded for both views, with the
      colour reversal spelled out against F1. `ProblemFixMenuJsTest` now builds its fixtures from
      the two cases that still *have* menus — a four-way duplicate on the list, a travel gap's
      three answers on a band.

      Found by Ted's own worked scenario (2026-08-21): flight out Sep 21, ExploreDDD in Denver
      Sep 23–25, flight home Sep 28, **no hotel at all**. Before this third source, Sep 22, 26 and
      27 all read "Nothing scheduled" while `/calendar` striped them away and `/schedule-problems`
      was already demanding a bed in Denver for every one of those nights — three surfaces, and
      the itinerary the only one with nothing to say. That scenario is now a test.

      **The home claim needs positive evidence, and "not in `awayDays()`" is not evidence.** The
      away band is built from nights the walk fills *between* points, so a trip with no return
      booked yet — flown out, no hotel entered, nothing after it — bands **nothing**, and trusting
      the band alone would announce Ted is home while he is in Amsterdam (exactly what he asked
      us to prevent). So a fourth cached read model, `ScheduleTimeline.homeByLastFactOfDay()`,
      records whether each day's *last* fact left him in a home city, and `atHomeOn` requires
      **both** that and the absence of an away band. Consequences, all deliberate: no home cities
      configured ⇒ never home (rather than always); no facts at all, or a date before the first
      fact ⇒ never home; the travel-home day itself ⇒ not home, so the row and the calendar's band
      always agree; an old fact that placed him home keeps holding, since he is home by choice and
      no news is the normal state of being there.

      Scoped deliberately (Ted's call): **only fully empty days** — a mid-stay day that has a
      conference or gathering on it is left exactly as it was. Check-in and check-out days are
      excluded because their own entries already say where he is, and more precisely. Both booking
      intents count: the itinerary draws a tentative stay like a final one. No redaction impact —
      `/itinerary` is OWNER/FAMILY only, and none of this reaches `/calendar`. Mutation-verified
      thirteen ways across the three rounds (both stay date bounds, the stay row suppressed, the
      stay row forced onto a day with entries, the hotel name dropped, home winning over a stay,
      the away guard dropped, the home-evidence check dropped, the tint darkened, the two
      controller wirings cut, both no-bed range bounds shifted, the booking link shown to family).
      Caught twice in passing: a CSS comment quoting "Nothing scheduled", then a
      `doesNotContain("whereabouts-detail")` matching its own CSS rule — the bare-word assertion
      trap CLAUDE.md warns about, working as intended both times.
- [x] **Cancel ground transfer** (2026-08-20), the day after the slice it followed. D11 in
      `archived/GroundTransferPlan.md` shipped that slice without cancel, so a mistyped transfer could not be
      removed from inside the app: it stayed on the calendar and kept feeding a false presence fact
      into `/schedule-problems`, where it masked a real missing-travel gap. Built as the task
      described: a `GroundTransferCancelled` event (the id alone — no reason, since a transfer has
      no booking to explain away), command + context + application service folding existence from
      the event stream, `/ground-transfers/{id}/cancel` GET-confirm + POST with its own matcher and
      matrix row, and removal branches in the calendar, itinerary, gap and new details-view
      projectors — the gap the transfer was closing correctly returns, which
      `GroundTransferCancellationPropagationTest` pins. Amber, plain confirm, no typed word, as
      specified. Reachable from **both** the itinerary card and the calendar entry (Ted's call): a
      `.cancel-bin` in the edit pencil's slot, OWNER-only, via a new `CalendarEntry.cancelPath` that
      the redactor drops. Details in `archived/GroundTransferPlan.md` → "Cancel, as built".
- [x] **Retired `/admin/migrate-conferences`** (2026-08-19). The one-off conference→gathering
      migration had served its purpose — Ted ran it — so the whole write path went: both
      `AdminController` handlers, `ConferenceMigrationService`, the `MigrateConferenceToGathering`
      command record, `admin-migrate-conferences.html`, the admin-home card, the bean in
      `EventSourcingConfig`, and `ConferenceProjector.migratableViews()`, which existed only to feed
      that page. Two things deliberately stayed: the **events** it emitted (`GatheringPlanned` /
      `ConferenceCancelled`) are ordinary history and keep replaying, and the **command_log rows**
      still resolve — command payloads are stored and rendered as raw JSON
      (`PostgresPersister` reads `payloadJson` as a string), never deserialized back into the record
      class, so deleting the class cannot break the command log.

- [x] **Rename `ConfirmedCalendar*` → `Calendar*`** (2026-08-19). "Confirmed" distinguished nothing:
      the calendar is *the* calendar, the route has been `/calendar` all along, and the adjective
      survived only in class names. `ConfirmedCalendarRenderer` → `CalendarRenderer` (plus its single
      call site in `CalendarController`), and the three tests → `CalendarRendererTest`,
      `CalendarDayMenuJsTest`, `CalendarToggleJsTest`. Pure rename, 27 usages: **no** route, template,
      CSS class, event, or stored-data impact, and no name collision (`CalendarViewBuilder` is a
      different thing, and no `CalendarRenderer` existed). Docs naming the class swept — including a
      few code-fence references the IDE's rename doesn't reach. 962 unit + 36 js green, with both
      renamed js-tier tests discovered and running under their new names.
- [x] **Login "have to sign in twice"** (2026-08-18). The custom sign-in form's CSRF token was
      session-bound (default `HttpSessionCsrfTokenRepository`), so it died whenever the in-memory
      session did — every redeploy, every local devtools restart, every idle timeout. A login page
      rendered before that death then submitted a token with no session to match; the `CsrfFilter`
      rejected it and the custom `accessDeniedHandler` bounced it **silently to `/`**, looking
      exactly like "not logged in". The retry rendered a fresh `/login` with a matching token and
      worked — hence "log in twice, then it sticks". Two changes in `SecurityConfig`: (1) CSRF token
      now lives in an **HttpOnly cookie** (`CookieCsrfTokenRepository`), not the session, so it
      outlives restarts/timeouts and needs no server session — the first login after a restart
      validates (kept HttpOnly: the form gets its token server-side, so JS never reads it, no weaker
      than a session token against XSS); (2) a rejected CSRF token (`CsrfException`) now routes to
      `/login?expired` with a notice instead of silently to `/`, where an expired login is
      indistinguishable from never having signed in — authenticated-but-wrong-role denials still go
      to `/`. `login.html` shows the `?expired` notice. New mutation-verified test
      `SecurityAuthorizationTest.staleCsrfTokenReturnsToLoginWithExpiredNotice`; reproduced and
      verified end-to-end against the running app; full suite green (938 + 36 js). **Single-instance
      only fixes CSRF** — going multi-instance would also need the auth `HttpSession` externalized
      (Spring Session JDBC on the existing Postgres), **deferred until actually scaling**.
      That deferral now has its own entry under **Deferred** (promoted 2026-09-08, because a
      deferral recorded only inside a ticked box is unfindable). The separate question this item
      does *not* answer — staying logged in across a restart on one instance — shipped the same day
      as remember-me with persistent tokens; see its own entry above.
- [x] **Lateral nav across the read-only view pages** (2026-08-17). The old "nav" was the same
      two-link `JitterTravel · Calendar` breadcrumb on every page (and inconsistently built — the
      calendar's was an inline-styled indigo link, trains wrapped it in `<h3>`), so from any view
      you could only reach `/` and `/calendar` — every other view page was a lateral dead-end.
      Replaced it with a single shared `Page.viewNav(NavAudience, activePath)`: a flex-**wrapping**
      bar (no horizontal scroll) that links each view page to the others the viewer may reach, with
      the current page rendered as a non-link `<span class="active" aria-current="page">`. Applied
      to all eight j2html view renderers — `/itinerary`, `/calendar`, `/booked-flights`,
      `/booked-trains`, `/booked-hotels`, `/planned-gatherings`, `/tentative-conferences`,
      `/schedule-problems`. **Tier-gated (deny-by-default), matching `SecurityConfig`:** OWNER sees
      all eight; FAMILY sees only Itinerary + Calendar (the pages it can open); anonymous on the
      public calendar sees only the home link — a link to a page the viewer would 403 on is both a
      papercut and a hint the page exists, so it's never rendered. `NavAudience.of(isPublicUser,
      isOwner)` derives the tier from the flags controllers already hold. Base `.view-nav` styling
      in `site.css`; the calendar scopes a `4rem` horizontal margin so the bar aligns with its
      `.calendar-outer` body. New `PageTest` (link sets per tier, active-span, `NavAudience.of`) and
      two new `CalendarRedactionSecurityTest` cases (anonymous exposes no owner/family surface;
      owner links to the other views) — all mutation-verified (anonymous-leak, missing active-span,
      owner-missing-links). **The Schedule Problems nav link is unconditional (OWNER only).** A
      state-aware version was built and then **reverted on 2026-08-18**: making the link appear only
      when `ScheduleGapProjector.problems(now)` was non-empty meant threading a
      `hasScheduleProblems` flag through `Page.viewNav` and all eight view renderers *and* injecting
      `ScheduleGapProjector` into seven view controllers that otherwise have no interest in it. Ted's
      call: that's a large increase in coupling, test setup, and constructor noise for a small UX
      gain, so the bar now reflects only the viewer's tier and the report page renders its own empty
      state when the schedule is clean. The **home card** on `index.html` stays state-aware —
      `GeneralController` legitimately depends on the projector for its count (see the entry below).
      Full suite green at 936. **Deliberately scoped this session (Ted's
      call):** *view pages only* — the Thymeleaf **form** templates keep their existing breadcrumb
      (nav matters less there, and forms are reached from the lists / the calendar's add-entry
      dropdown), and **no footer** (the calendar dropdown covers adding entries). Admin pages left
      out too. This retires the "many pages are dead-ends" cleanup item as far as the view surfaces
      are concerned.
- [x] **Consistent edit affordances on calendar and itinerary entries** (2026-08-17). Every editable
      entry kind now exposes the same OWNER-only edit pencil from **both** surfaces. Before this,
      the calendar showed a pencil only for flights and trains (`editPath` set), and the itinerary
      showed one for flights/trains/hotels but not gatherings. Filled the two gaps: `HotelCalendarProjector`
      and `GatheringCalendarProjector` now set `editPath` (`/booked-hotels/{id}`,
      `/planned-gatherings/{id}`), so the calendar renders the pencil for hotels and gatherings too;
      `ItineraryRenderer.renderGathering` now takes `isOwner` and appends the pencil (needed a new
      `gatheringId` field on `GatheringItineraryEntry`, threaded from `ItineraryProjector`). The
      pencil is OWNER-only on both surfaces — the calendar redactor drops `editPath` and the
      renderers gate on `isOwner`; a new `CalendarRedactionSecurityTest` case plus the strengthened
      lodging redactor test prove the hotel deep link never reaches anonymous eyes (full-stack
      mutation-verified). **Conference and private event are intentionally excluded** — they have no
      edit flow yet (`ChangeConference` / `ChangePrivateEvent` are separate unbuilt features), so
      there is nothing to link to; when each edit page ships it should set `editPath` / take `isOwner`
      the same way and inherit the affordance. Projector + renderer + both-tier redaction tests, all
      mutation-verified.
- [x] **Split every page combining an Edit and a Cancel form** (2026-08-17). No page hosts a
      second cancel *form* — the only entry cancel form, `cancel-hotel.html`, has been its own page
      since `f5971ef`, and every other edit page hosts a single edit form (`change-flight.html`'s
      second form is the flight-number **lookup**, not a cancel; `change-train.html` /
      `change-gathering.html` have one form each; there is no `change-private-event` page). The
      other entry kinds had **no cancel action at all** at the time — separate features, and two
      have since shipped on their own pages as this line required: the **private event**
      (2026-08-24, `ChangePrivateEventPlan.md`) and the **train** (2026-09-06,
      `CancelTrainAndOverlappingLegsPlan.md` slice 1). Still open: **flight**, **gathering**
      (out of scope in `archived/ChangeGatheringPlan.md`) and the `ConferenceCancelled` organizer
      cancel. When each ships it must land on its own page from the start, per the "errors render
      on the form page" convention.
      **Correction (same day):** the first pass called this done on the form-vs-link technicality
      and left the "Cancel this booking" **section** (a `.danger-zone` heading + hint + link to the
      cancel page) sitting on the `change-hotel` edit page — which still reads as edit-and-cancel
      combined. Removed that whole section (and its now-dead `.danger-zone`/`.danger-link` CSS), so
      the change page is genuinely edit-only; cancel is reached from the per-row **Cancel** link on
      `/booked-hotels` (which already exists next to Edit). `ChangeHotelControllerTest`'s GET render
      test now asserts the page `doesNotContain` "Cancel this booking" or "/cancel" (mutation-verified),
      and the explanatory note on the template is a Thymeleaf parser comment so it isn't rendered.
- [x] **Calendar day-number popup now dismisses** (2026-08-17). The owner future-day disclosure
      menu on `/calendar` is a native `<details class="day-menu">`, which on its own never
      dismisses — clicking away left it open, Escape did nothing, and opening a second day left the
      first open so the absolutely-positioned menus stacked and overlapped. Added `DAY_MENU_SCRIPT`
      in `CalendarRenderer` giving the three behaviors a popup is expected to have: only
      one open at a time (a `toggle` listener closes the others when one opens), close on
      outside-click (document `click` where the target isn't inside a `.day-menu`), and close on
      Escape (document `keydown`). Harmless when no day menus are present (owner-only render).
      Covered by `CalendarDayMenuJsTest` (`@Tag("js")`, `page.setContent`, no server) —
      three cases (outside-click, Escape, no-stacking), each mutation-verified. **Note:** while
      doing this I found `CalendarToggleJsTest` was **pre-existing broken** (failed without
      any of my changes) — the 2026-08-16 "default `from` = one week before today" change
      (`0435623`) shrank the rendered range so the tests' expected collapsed-week counts no longer
      held; the `js` tier is opt-in (excluded from the default build), so it shipped invisibly.
      **Fixed 2026-08-17** (green, 5/5), and the native pre-push MUST-PASS gate now runs the `js`
      tier too so a broken js test can no longer ship unseen.
- [x] **Add a private social event type** (2026-08-13). Shipped as its own entry kind — see
      `archived/PrivateSocialEventPlan.md` (done) and the Backlog row. `EntryKind.PRIVATE_EVENT` with a
      `PlanPrivateEvent` command / `PrivateEventPlanned` event / context, `PrivateEventCalendarProjector`,
      a plan form + controller + nav card, and a `PrivateEventItineraryEntry` on `/itinerary`. The
      redactor gets its own `PRIVATE_EVENT` branch: an anonymous viewer sees `Busy`, a zone-labelled
      time range, and city+country — never the title — via the plain-text `SubtitleLine.FixedRange`
      (no `<time datetime>` leak). Both tiers of redaction test plus command/projector/controller/golden.
      Retires the "private dinner can only be a public GATHERING" leak. Follow-ups still open (edit
      flow, `/planned-private-events` list) live in the plan doc.
- [x] **Eager-migrate legacy events + per-event schema-version stamp** (2026-08-16). Owning doc
      `archived/LegacyEventEagerMigrationPlan.md` (now `built`). Added an `event_log.schema_version` column
      (nullable; per-type version in `EventTypes`, the nine `ZonedTimestamp` types = 2, others = 1);
      the append path stamps new rows, restore carries it verbatim, backup format bumped to **v3**
      (restores v2 and v3, so old backups aren't orphaned). `LegacyEventMigration` +
      `PostgresPersister.migrateEventPayloads` rewrite each stale row's payload and stamp in one
      transaction (identity columns untouched); idempotent (already-current rows skipped),
      validate-then-apply (one unbindable/unresolvable row aborts with zero writes), read-only-guarded.
      OWNER-only `/admin/migrate-legacy-events` (GET preview + POST run) with an `AuthorizationMatrixTest`
      row and an admin nav card. Decided with Ted: **column not payload-key**, accept the backup bump,
      in-place admin UPDATE, FQCN→logical `type` normalization **deferred** (that pass is now **built**,
      2026-08-19 — see `archived/EventTypeColumnNormalizationPlan.md`; the same `UPDATE` now writes `type`),
      versioning *framework* deferred (stamp only for now — **that framework is now built**, 2026-08-18,
      shaped by the `format` v2→v3 migration: `EventPayloadUpcaster` is a version-ladder composite of
      `EventUpcaster` rungs, see `EventPayloadUpcasterDesign.md`).
      Every new/changed test mutation-verified. Retirements still gated
      on old backups leaving rotation: the upcaster's legacy timezone rungs (the `*TimeZoneUpcaster`
      classes — see `EventPayloadUpcasterDesign.md`), the FQCN mapping, and the Antwerp-style resolver
      hacks.
- [x] **Boot-replay preflight (pre-deploy)** (2026-08-16). `BootReplayPreflightTest`, a
      `@Tag("replay-preflight")` tier excluded from the default build (`mvn test -Preplay-preflight
      -Dpreflight.dump=/path/to/backup.json`; no dump ⇒ skips). Restores a production backup into a
      scratch Testcontainer DB — whose validate pass runs the exact `upcast → classFor → bind` boot
      uses, incl. zone resolution — then drives `loadAllEvents()` over the loaded rows, failing with
      the offending row named on anything that would abort boot. Verified end-to-end against a clean
      dump (passes) and a bad one (fails, naming the unresolvable row) — the 2026-08-16 Morocco/Antwerp
      failure mode. This is the tool that certifies each retirement the eager migration unlocks;
      `/admin/zone-audit` is not a substitute (runtime-only, went stale).
- [x] **Startup-failure (read-only) warning banner on the home page** (2026-08-16). When
      `EventStore.isReadOnly()` is true — a failed boot replay or a failed save flipped the app to
      read-only — the home page renders a prominent red `role="alert"` banner across the top
      ("Read-only mode — a startup or save error occurred, so changes are disabled and some data may
      be missing"), shown to **every** viewer (the banner reveals no travel detail, and an anonymous
      visitor seeing a degraded site is honest). `GeneralController` now injects `EventStore` and
      exposes a `readOnly` model flag; the banner sits above the local badge and pending banner in
      `index.html`. Two `@WebMvcTest` cases (shown when read-only, hidden when writable),
      mutation-verified by hardcoding the flag to `false`. `EventStore` mock also fed to the two
      `GeneralController` auth slices (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
      Motivated by the 2026-08-16 deploy, where a replay bug dropped the app to read-only with empty
      projections and nothing on the page said so.
- [x] **"Schedule problems" nav card is state-aware** (2026-08-15). `GeneralController` surfaces
      `scheduleProblemCount` (OWNER-only) from `ScheduleGapProjector.problems().size()`; the card in
      `index.html` is amber only when there are problems, a green tint otherwise, and its subtitle
      shows the count ("3 problems" / "1 problem" / "No problems"). Three `@WebMvcTest` cases
      (amber+count, green+none, singular), all mutation-verified. Also fed the new dependency to the
      other `GeneralController` web-slice tests (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
- [x] **GET stale-link not-found no longer attaches a dead flash** (2026-08-15). The four edit-page
      GET handlers (`ChangeHotel`/`ChangeFlight`/`ChangeTrain`/`ChangeGathering`) redirected a
      not-found id to their view-only j2html list with a `notFoundMessage` flash the list can't
      render. Dropped the flash (and the now-unused `RedirectAttributes` param/import) so they
      navigate to the list silently. `CancelHotelController` has the same dead-flash pattern but is
      out of scope here and has a test asserting the flash — left as a separate item.
- [x] **`CancelHotelController` dead flash removed** (2026-08-16). The follow-up to the item above.
      All three `notFoundMessage` flashes (GET stale link, POST malformed id, POST already-cancelled)
      redirected to the view-only `/booked-hotels` j2html list, which can't render a flash, so each
      was silently dropped. Removed all three (and the now-unused `RedirectAttributes` param/import
      from both handlers) so they navigate silently. The one test asserting the flash
      (`unknownBookingRedirectsWithAFlashMessage`) was renamed to
      `alreadyGoneBookingRedirectsToListInsteadOfThrowing` and now asserts only the redirect;
      mutation-verified. All 7 controller-slice tests green.
- [x] **Schedule-problem day-boundary anchored to "Anywhere on Earth" instead of UTC** (2026-08-16).
      The two `LocalDate`-only `ScheduleProblem` variants (`MissingHotel`, `DifferentCityConflict`)
      anchored `relevantUntil()` at `ZoneOffset.UTC`, so west of UTC — the owner's whole realistic
      range (SFO home is UTC-7/-8, all US travel) — a problem dropped off FUTURE during the *previous*
      local afternoon: a missing hotel with checkout tomorrow vanished right as the owner arrived for
      the last night. Now both anchor at `ANYWHERE_ON_EARTH` (`ZoneOffset.ofHours(-12)`), keeping a
      day-granularity problem live until its date has passed everywhere the owner could be. The
      boundary only ever moves *later* (12h) than a UTC anchor, never earlier, so the fix is strictly
      surfacing-safe — it cannot hide a problem the old code showed; the only cost is a moot problem
      lingering up to ~12h longer (accepted papercut for a safety-net view). Deep boundary coverage in
      new `ScheduleProblemTest` (exact instants, never-earlier-than-UTC guard, SFO/Hawaii last-night
      surfacing, inclusive-boundary + one-second-past exclusion, east-of-UTC lingering, and guards
      that the two instant-backed variants stay anchored to their endpoint instant). Both mutants
      (AoE→UTC per record) verified; full suite green at 814.
- [x] **Responsive, no-horizontal-scroll treatment on the other list views** (2026-08-15).
      `/tentative-conferences` (`995caab`), `/booked-trains` (`86af549`, `9bb5245`, `bebf6e6`),
      `/booked-flights` (`b6c5f92`, `9bb5245`, `370c33a`, `c67444f`, `1027144`, `46e39e6` superseded)
      and `/planned-gatherings` (`375761e`, `8c558ac`, `0755edd`). Dropped every `.page`/container
      `max-width` cap and the `overflow-x` scroller; date/time uses
      `ZonedTimeTag.renderDateTimeStacking`. The grid views (flights, trains, conferences,
      gatherings) were unified on **one grid + `grid-template-columns: subgrid`** so columns align
      across rows with min-content floors (no drift, no overlap), collapsing to a single stacked
      column with per-column leg labels below 640px. Flights keep the change-history disclosure by
      putting the list inside the `<summary>` (so it spans all columns) and hiding it while closed.
      `/planned-gatherings` was additionally reshaped from cards into a
      When/Speaking/Gathering/Venue/actions table. Each renderer's exact-markup tests updated and
      mutation-verified. Note: the `minmax(fixed, fr)` and plain-`min-width:0` attempts were tried
      and rejected (overlap / drift) — subgrid is the chosen approach.
- [x] **`ClearConflictController` POST now has error handling** (2026-08-15, `6df5f63`).
      `clearConflictSubmit` wraps the parse + `clearConflict(...)` in a `try/catch`: a malformed id
      or generic append failure re-renders the `clear-conflict` form with a global error via
      `bindingResult` (never redirecting the error to the view-only `/schedule-problems`), and
      `ReadOnlyModeException` redirects to `/read-only`. The conflict-summary fields ride the POST as
      hidden inputs so a rejected submit re-renders the summary intact. Three `@WebMvcTest` cases
      (malformed id, service failure, read-only), all mutation-verified — including a hardened
      assertion matching the visible `<strong>` summary markup, since the value also rides a hidden
      input's `value=` and a bare substring check passed even with the visible summary broken.
- [x] **`/booked-hotels` now shows the booking's real `bookingIntent`** (2026-08-15, `6df5f63`).
      `BookedHotelsProjector.put` takes a `BookingIntent` and threads it from both
      `HotelBooked.bookingIntent()` and `HotelChanged.bookingIntent()` into the view instead of
      hardcoding `TENTATIVE`, so a FINAL booking no longer reads "Tentative" next to its own FINAL
      edit form. New projector test books a FINAL hotel and asserts the view status;
      `hotelChangedOverwritesBookingUnderSameId` was extended to assert the change to FINAL lands.
      Both mutation-verified by reinstating the hardcode. Promoted to a rule in
      `EventSourcingRulesHeuristics.md` (**R8**: a projector that derives a field must derive it
      from the events, never ignore relevant data the events carry).
- [x] **Dry-run validation for an import file** (2026-08-06). `CommandImporter.validateJson` runs
      pass one of the real import — deserialize every entry and recompute its events — and writes
      nothing, returning a `ValidationReport(validCount, errors)`. A "Validate only" button on
      `/admin/import` posts the textarea to `/admin/import/validate` (covered by the existing
      `/admin/**` OWNER matcher, so no `SecurityConfig` change) and re-renders the same page with
      either every problem found or "all N entries would import. Nothing was written." The
      textarea keeps its content so the file can be fixed in place. Written because
      `/admin/zone-audit` reads `event_log` — data that is *already imported* — which is backwards
      for a wipe-then-import workflow; it gave no warning before the 2026-08-06 production import
      failed on three venues. Mutation-verified: making `validateJson` call `apply` fails both
      dry-run tests. *(2026-08-11: `CommandImporter` was retired with the event-oriented
      backup/restore rework; this dry run now lives in `BackupService.validateJson`, posted from
      `/admin/restore` to `/admin/restore/validate`, and validates event payloads rather than
      recomputed command events.)*

- [x] **Every application service goes through `CommandExecutor`** (2026-08-05, with the conference
      UTC slice). `ConferencePlanning` was the last service injecting `EventStore` directly; it now
      uses `CommandExecutor` like the rest. The enforcement test landed in the same change as
      `ApplicationServicesUseCommandExecutorTest` — plain reflection over `application`-package
      constructors, **no ArchUnit dependency**, with `CommandExecutor` itself excluded as the
      authorized holder. This unblocks the conditional-append work in
      `CommandConsistencyEventStore.md`: no service can now bypass the guard that lives in
      `CommandExecutor`.