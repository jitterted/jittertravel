# JitterTravel — Claude Code Notes

## Architecture Rules

### Event Storage: use CommandExecutor, never EventStore directly

Application services must **never** receive `EventStore` as a constructor dependency.
All event appending from application services must go through `CommandExecutor`:

- `commandExecutor.execute(...)` — for domain commands with a decision context
- `commandExecutor.appendEvents(...)` — for internal actions (clearing conflicts, migrations, etc.)

**Why:** `EventStore.append()` requires the command to already exist in `command_log` (foreign
key constraint). `CommandExecutor` enforces this ordering. Bypassing it causes FK violations
and partial writes (some events land, others don't). `CommandExecutor` also throws
`ReadOnlyModeException` before writing anything, so read-only mode holds even if a controller
forgets to check — including on the import path.

Enforced by `ApplicationServicesUseCommandExecutorTest` (plain reflection over `application`
constructors, no ArchUnit dependency).

### EventStore ordering invariant: persist before notify

`EventStore.append()` persists to the database **before** adding to the in-memory list
and notifying subscribers. This guarantees projectors only ever see events that are
durable. If persistence fails, the exception propagates and subscribers are never called.

Covered by `EventStoreTest.subscribersNotNotifiedWhenPersistenceFails()`.

### Restore is validate-then-apply, and resumable

Backup/restore is **event-oriented**: `BackupService` writes every `event_log` row verbatim
(same ids, sequences, timestamps, `schema_version` stamp) and restores them verbatim — it does
**not** re-execute commands (commands ride along as opaque history for a future undo; see
`docs/archived/EventOrientedBackupRestorePlan.md`). `BackupService.restoreJson` runs two passes: pass one
deserializes, upcasts, and **bind-checks** every event **writing nothing**, pass two applies them
(via `CommandExecutor`, per the rule above). Any validation error means zero writes, and *all* bad
entries are reported together. `validateJson` exposes pass one on its own as a dry run for
`/admin/restore/validate`.

**Why:** restore failures are usually data problems in a few events (an address whose zone
doesn't resolve, a schema-incompatible payload). Applying events as they are read leaves a
half-populated database that has to be wiped. Pass two also skips events already present in
`event_log`, so a partially applied restore resumes on re-run instead of colliding on the primary key.

Backup format is at **v3** (per-event `schema_version`); restore still reads v2 (unstamped)
files, so older backups aren't orphaned.
Covered by `RestoreSafetyTest`.

### Redaction: anonymous viewers are a first-class threat model

The calendar at `/calendar` is the one page anonymous visitors can see, and
`PublicCalendarProjector` is the only thing standing between them and Ted's travel details.
Treat it as security code, not formatting code.

**It is an allow-list, not a deny-list** (shipped 2026-08-21, replacing `CalendarEntryRedactor`).
The public calendar is its own read model, built straight from events by a projector that reads only
the fields it names — so a field it never reads cannot leak, and forgetting to handle a new event or
a new field leaves the data *absent* from the public calendar rather than exposed. A leak now takes a
deliberate line of code. The old redactor was the other way round: it stripped fields from a read
model that already held the hotel names and booking links, so every new field was public until
someone remembered to strip it.

**Private, never render for an anonymous viewer:**

- hotel names and street addresses
- any URL that resolves to a place Ted sleeps or a booking — hotel `mapsUrl`, `editPath`
- travel times of day — departure/arrival/check-in/check-out. Anonymous viewers get *day*
  granularity only (the grid column), never a clock time, for flights, trains, and hotels.
- carrier/service identifiers — flight numbers, train `serviceId`, booking references
- links into owner/family surfaces (`/itinerary`, `/booked-*`, `/planned-*`)
- the whole `/schedule-problems` report (conflict/gap times, names, internal ids) — OWNER-only
- everything about a conference *except* the collapsed commitment level: the submission
  pipeline (talk titles, submitted/accepted/waitlisted/rejected/withdrawn and their dates), CFP
  window dates, and the commitment **basis** — `AttendanceBasis`, i.e. whether Ted is going
  because a talk was accepted, he was invited, or he bought a ticket. The basis is the easy leak,
  because it re-states the submission outcome; the public projector reads the confirmation event and
  never reads that field, which is the pattern to copy — do not carry a private value into a view
  and strip it later.

**Public by decision** (do not "fix" these without asking Ted): the fact that travel is
happening on a given day, airport codes and city names for flights/trains/hotels, and
**conferences and gatherings in full** — name, venue, city, `infoUrl`, and start/end times.
Both are public events Ted speaks at or attends publicly. That Ted is **speaking** at a
gathering is public too (shipped 2026-08-17): `speaking` is a component of
`EntryDetails.PublicGathering` and renders as a "Speaking" badge on the anonymous `/calendar`
(the venue and time are already public, so the badge reveals nothing new). The conference
half of the speaking badge waits on submission tracking; a **private** talk at a company is
neither — it has no public venue/time and collapses to `EntryDetails.Busy` like every other
private kind, never modelled as a gathering *or a conference* to earn the badge.

A conference's **attendance commitment** is public too (shipped 2026-08-19):
`commitment` is a component of `EntryDetails.PublicConference` and renders as a
"Maybe" chip on the anonymous `/calendar` — the same chip owner and family see. It is publishable
only because `ConferenceCalendarProjector` has **already collapsed** every speculative state
(CFP not open, submitted and waiting, rejected but undecided, not submitting) into one
`AttendanceCommitment.WATCHING`, so the chip cannot distinguish them; the private
`AttendanceBasis` is read there and discarded rather than carried and stripped. If you ever
un-collapse that enum — add a value that a viewer could map back to a submission outcome — the
chip stops being publishable. `GOING` renders no chip, and a declined or organizer-cancelled
conference leaves the calendar entirely, for everyone.

The **away band** is public too (shipped 2026-08-20): the turquoise stripe under a day label
saying Ted is out of town that day renders for every viewer, anonymous included. It reaches the
calendar as a plain `Set<LocalDate>` from `ScheduleGapProjector.awayDays()`, which never meets
`PublicCalendarProjector` — deliberately, so do not "fix" that by routing it through the public
projection.
The band aggregates only day-granularity facts already public above, and assembling "he is away
that week" from the public calendar takes no effort (Ted, 2026-08-20). Note what that argument
rests on: the band says *when*, never *where* or *why*. A future variant that labelled the trip,
or that banded days no public entry accounts for, would be a new disclosure and needs asking.

**Private social events are their own kind (shipped 2026-08-13).** `EntryKind.PRIVATE_EVENT`
(a dinner with friends) is built publicly as `EntryDetails.Busy`: an anonymous viewer sees `Busy`, a
zone-labelled time range, and city/country, and nothing else — never the title, which the public
projector does not read. Do **not** model a private-ish event as a GATHERING to reuse its rendering
(gatherings are fully public).

**Any *new* private-ish kind reuses `EntryDetails.Busy` — it does not get a public type of its
own.** That is redaction, not economy: a second private kind with its own public lane would let a
stranger tell it apart from a dinner by lane alone. Give it its own *owner* details type and its own
branch in `PublicCalendarProjector` that collapses to `Busy`. See
`docs/archived/PrivateSocialEventPlan.md`.

**Rules for writing the code:**

1. **Redaction is deny-by-default, and the default is "don't read it."** In
   `PublicCalendarProjector`, publish a value only by naming the event field it comes from. Never
   derive a public entry from an owner `CalendarEntry` — that is the deny-list the allow-list
   replaced, and it puts the private value one refactor away from the page. If a kind needs
   something publishable that the event does not carry directly (a transfer's route, whose owner
   title names a hotel), build it there from the event's own data.
   **The kind-specific half of a public entry is an `EntryDetails.Publishable`**, and those records
   have no slot for an edit path, a cancel path, a maps URL or a hotel name — so a future change
   cannot fill one in by mistake. Every entry the projector builds goes through its private
   `entry(...)` helper, whose last argument is a `Publishable`; keep it that way, because that is
   what makes the allow-list a compiler check rather than a convention. Adding a *public* field
   means adding it to one of those records, which is the moment to ask whether it may be published
   at all.
2. **On travel entries (FLIGHT, TRAIN, GROUND_TRANSFER, LODGING) a `SubtitleLine` carrying a
   `ZonedTimestamp` must never reach the public calendar.** `ZonedTimeTag` emits
   `datetime="<UTC instant>"` into the markup, so a time leaks in the attribute even when
   the visible text looks harmless. Public travel subtitles are `SubtitleLine.Text` or
   nothing — today the projector gives flights and trains no subtitle at all, and a transfer only
   its route. (Conference and gathering times are public — see above. The private event's
   `FixedRange` is the one deliberate exception, its time being public in its own zone.)
3. **Every new route is deny-by-default too.** `SecurityConfig` ends in
   `.anyRequest().permitAll()`, so a new `@GetMapping` is public unless you add a matcher.
   Hiding the nav card in `index.html` (`th:if="${showDataEntryNav}"`) is *not* access
   control — the URL is still open. Add the route to `SecurityConfig` **and** to the
   `policy()` matrix in `AuthorizationMatrixTest` in the same change.
4. **The audience is chosen at the boundary and applied inward.** `CalendarController` derives
   `isPublicUser` from `request.getRemoteUser()` and picks the read model — `PublicCalendarProjector`
   for a stranger, `CalendarAggregator` for everyone else. Renderers must never re-derive viewer
   identity, reach for `SecurityContextHolder`, or strip anything: `CalendarRenderer` draws exactly
   what it is handed, and `CalendarRendererTest` pins that.
5. **Every redaction change needs both tiers of test**: a unit test in
   `PublicCalendarProjectorTest` asserting the private value is not in what the projector emits,
   and a `CalendarRedactionSecurityTest` case asserting the rendered anonymous body
   `doesNotContain` the secret through the real security chain. Assert on *absence* of the
   private value, not just presence of the placeholder. Anonymous fixtures in the security test are
   built by driving **real events through a real `PublicCalendarProjector`** — never by hand-writing
   a public `CalendarEntry`, which would let the test assert whatever it wished.
   `PublicCalendarProjectorTest.everyEntryCarriesOnlyPublishableDetails` is the invariant that
   replaced the redactor's compile-time forcing function; it is written so that adding a kind does
   **not** require editing it, and it must stay that way — a test that has to be edited on every
   change stops guarding, because editing it is exactly what a leaking change would do.
6. **When in doubt, redact and ask.** A missing detail on a public calendar is a papercut;
   a leaked one is unrecoverable.

### Time comes from the injected Clock — never the ambient system clock

Production code must **never** call `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`,
`System.currentTimeMillis()`, or any other no-arg "what time is it" call. They are unmockable:
a class that reads the ambient clock cannot be tested at a chosen instant, so anything that
depends on *when* it runs — a FUTURE/ALL filter at a day boundary, a cancellation deadline, an
expiry, a "today" column — has no way to be pinned down in a test.

Take the time from the injected `Clock` instead:

- `Instant.now(clock)` or `clock.instant()` in a controller
- capture it at the boundary and pass it inward — services, projectors, and the domain receive
  a `now`, they never ask for one (see "external inputs from the boundary")

The **only** legal source of real time is the `Clock` `@Bean` in `EventSourcingConfig`
(`Clock.systemDefaultZone()`). Everything else — controllers, `EventStore`,
`PostgresPersister` — takes `Clock` as a constructor dependency.

Enforced by `NoAmbientClockReadsTest` (plain source scan over `src/main/java`, with
`EventSourcingConfig` exempt). Tests may read the wall clock freely; the rule is about
production code. Note that a `@WebMvcTest` slice has no `Clock` bean of its own — import
`WebTodayTestConfig` (which pins one) when slicing a controller that needs time. Prefer a
`Clock.fixed(...)`; use an advancing clock only when the behaviour under test genuinely
depends on time passing (see `PostgresPersisterTest`, where command ordering does).

### Destructive actions: red, and gated behind a typed word

Colour carries meaning, and it is not decorative (Ted, 2026-08-19):

- **Red = irreversible.** The action cannot be undone from inside the app — truncating the
  database, a migration that rewrites or renames stored rows, deleting data.
- **Amber/orange/yellow = reversible or recoverable.** Work waiting (the pending-commands and
  post-deploy task banners), a restart needed, a schedule problem to look at. If the answer to
  "can Ted put this back?" is yes, it is not red.

A warning *about* a destructive operation follows the operation, not the tone of the sentence: it
is red, never amber.

**Every destructive action takes a typed confirmation** — a short all-caps word in a text input
next to a **red** button, matching the Danger Zone on `/admin/database` (type `DELETE`) and
`/admin/migrate-legacy-events` (type `MIGRATE`). The controller compares the word exactly and
re-renders the page with the error when it does not match, writing nothing; a disabled-looking
button alone is not a gate, because the POST is still reachable. The word goes in the input's
placeholder and in a hint line, so nobody has to guess it.

### Action affordances: never move, and disable rather than hide — but only for *state*

Two standing UI rules for buttons, links, and icons (Ted, 2026-08-19):

1. **They never move.** A reader aims at a remembered position, and a misclick on an action is not
   a free mistake. If one action renders conditionally, its neighbours must stay exactly where they
   were — same position on every row, in every state. Reserve the slot; do **not** re-align the
   container to compensate (flush-right was tried on `/conferences` and rejected: it reads as off,
   and it drags the column header right with it).
2. **An action that cannot be triggered right now is shown disabled, with the reason** — greyed,
   non-interactive text (a `span`, never a disabled `<a>`), carrying a `title` that says why. It is
   not removed. Removing it changes the row's vocabulary between rows and hides that the capability
   exists at all.

**The split that matters — the second rule is about state, never about authorization.** It applies
only where the action *has been or will be* available to this viewer: already confirmed, already
cancelled, no next page. Where a viewer could **never** trigger it — anonymous and family users on
OWNER surfaces — render **nothing at all**. A greyed control is itself a disclosure: it tells a
stranger the surface exists and that Ted has one. Hiding by permission stays hiding; see the
redaction rules above, which win wherever the two appear to disagree.

Worked example, `ConferencesRenderer.confirmSlot`: a `WATCHING` row gets a live `Confirm` link, a
`GOING` row gets greyed `Confirm` text titled "Already confirmed. Changing why you're going arrives
with submission tracking." — a *presentation* limit, honestly stated, since the domain does allow
re-confirming with a different basis. Decline therefore occupies the same slot in both.

**A third rule, about dropdowns (Ted, 2026-08-21): use one only above three choices, or where
space is genuinely constrained — and if you are unsure, ask.** Up to three actions are rendered as
links, side by side or wrapped; a menu holding one item is a door in front of a door, and even
three is faster read than opened. "Space is constrained" means a real constraint, like a band in a
week-grid cell that is one day wide — not a card with a whole column to itself. Where the menu does
survive, it still needs a **visible affordance**: a control that only reveals itself to someone who
already knows to click is a hidden affordance, which this project does not ship. Worked examples:
`ScheduleProblemsRenderer.fixSlot` (links up to three, menu above), and
`ProblemCalendarViewBuilder.renderBandSegment` (one fix makes the whole band a link; several keep
the menu, and either way a `Fix ▾`/named chip on the band's face says the action is there).

**Problem colouring beats problem taxonomy.** On any surface where a problem sits among
non-problems, every problem wears the same warning amber, whatever kind it is; the kind may survive
as a left edge, an icon or the words, never as the fill. Ted missed a run of missing hotels on
`/schedule-problems?view=calendar` because they were blue while travel gaps were amber (2026-08-21).
The first thing a marker has to say is "something here is wrong".

Known violations still open, with the mechanism for each, are listed in `docs/Cleanup_Tasks.md`
("Action affordances that still move").

### A fix link carries its problem with it

Every href `ProblemFix` builds ends in `?problem=<key>&from=<origin>`, appended in one place
(`ProblemFix.explaining`) so a new fix cannot ship without them. On the page it lands on,
`ProblemContextAdvice` resolves the key against `ScheduleGapProjector.problems(now)` and the shared
fragment `fragments/problem-context.html` renders the banner: the problem in the report's own words,
the surrounding `ScheduleContext`, and a Back link to the surface the link was clicked on.

**Why:** the prefill was never the gap — `/book-hotel` already arrived with the right city and the
right nights. What was missing is the sentence that made them the right values, and Ted was opening
a second tab to re-read the report he had just left (2026-08-21). Full reasoning in
`docs/ProblemContextOnFixPagesPlan.md`.

**Adding a new fix target — three things, or the banner silently is not there:**

1. Include the fragment in its template:
   `<div th:replace="~{fragments/problem-context :: problemContext}"></div>`, above the form.
2. Add its path to `TEMPLATE_FOR_PATH` in `ProblemContextFragmentConventionTest`. That test walks
   every href `ProblemFix` can emit, so an unlisted path fails it — deliberately, because the
   wiring is an advice no controller mentions and nothing else would notice.
3. Keep the route **OWNER-only**. The banner prints hotel cities, gathering names and exact arrival
   times — the content of a report `SecurityConfig` gates at OWNER. This is a standing condition on
   all six current targets, not a one-time check.

**The link carries a reference, never the words.** `ProblemRef` derives the key from the problem's
own content in an exhaustive switch (problems have no id — they are recomputed from events every
batch), so a new `ScheduleProblem` variant cannot be added without deciding how it is referenced. A
key matching nothing — hand-edited, bookmarked, or naming a problem since fixed — renders **no
banner** and leaves the form exactly as it was; that is the whole error path. Never put the sentence
in the query string: a URL can be edited, and it goes stale the moment the problem is fixed in
another tab.

**The wording is reused, never rewritten.** The banner reads `ProblemBand` and `ContextBand` — the
problem calendar's own view types — so it cannot disagree with the band that was clicked. A second
copy of the phrasing is a second copy to drift.

### Presentation formatting stays out of the domain

Display strings are presentation, not domain. A domain type (`Address`, `ZonedTimestamp`, an
`Event`) must not carry methods that format how it is *shown* — no `cityCountry()`, no
`asLabel()`, no `formatTime()`. Formatting belongs in the presentation layer: the projectors that
pre-format `CalendarEntry`/view records, and the j2html/Thymeleaf renderers. Domain types expose
their data (`city()`, `country()`); the presentation layer decides that a calendar cell reads
`"London, GB"`.

This codebase hasn't always been strict about it, so you will find counter-examples; do not add
new ones, and prefer moving formatting toward a renderer when you touch it. A shared formatting
concern across two presentation sites goes in a presentation-layer collaborator (e.g. a projector
helper), never pushed down onto the domain type to "share" it — and mind the standing preference
against single-method utility classes when you place it.

## Testing

### Assertions against rendered HTML must name whole elements, not bare words

A renderer test asserts on one long string, so `contains("Calendar")` is satisfied by *any*
occurrence anywhere in the document — the title, a nav link, a CSS class, an entry's text. That is
almost never the claim the test is making, and it fails to fail when the thing under test changes.

Real example (2026-08-19): `CalendarRendererTest.emptyEntriesRendersCalendarPage` asserted
`contains("Calendar")` while the page was titled **"Confirmed Calendar"**. Changing the title to
"Calendar" left the test green — and deleting the `<title>` altogether would *also* have left it
green, because the nav's own Calendar link matches the same bare word. The assertion pinned nothing.

**So:**

- Assert the **whole element**: `contains("<title>Calendar</title>")`, not `contains("Calendar")`.
- Assert whole **attributes** with their value: `contains("href=\"/booked-trains/trip-123\"")`.
- For an absence, be *more* specific still — `doesNotContain("Grand Hotel")` is a real claim;
  `doesNotContain("Hotel")` would break on the word appearing in a heading, and a too-loose
  `doesNotContain` passes for the wrong reason once the markup moves.
- Prefer a distinctive substring of the exact markup over a regex; if a claim genuinely needs
  structure (this element inside that one), that is a sign the assertion belongs in a
  `@WebMvcTest`/security-chain test that can query the DOM.
- Same rule for CSS assertions: `contains("grid-template-columns: repeat(7, minmax(0, 1fr))")`
  paired with `doesNotContain("repeat(7, 1fr)")` — the pair is what makes it precise.

Mutation-verifying (standing practice) catches exactly this class of bug: change the production
string and watch the test go red. If it stays green, the assertion is too loose.

### List views: future/all toggle is a shared, enforced convention

Booked/planned list views (trains, flights, hotels, conferences, gatherings) all share one
FUTURE/ALL filter, defaulting to FUTURE. A new list view opts in by following the trio:
the view record implements `TemporalView.relevantUntil()` (the instant after which the item
is past — the *end* for multi-day items); the projector filters with
`timeView.includes(view, now)` in `views(TimeView, now)`; the controller reads `?filter=` via
`TimeView.fromParam` and passes `now()`; the renderer calls
`TimeFilterToggle.render("/its-path", activeFilter)` (toggle CSS lives in `site.css`).

`TimeFilterToggleConventionTest` enforces the last step: it discovers every static
`render(List, TimeView)` in the `web` package and asserts each emits the shared toggle wired
to the active filter. Forget the toggle on a new list renderer and that test fails.

### JS-behavior tests: tag `js`, browser-only, no server

Tiny inline scripts our renderers embed (e.g. the calendar "Show/Hide past weeks"
toggle) are tested in a dedicated Playwright tier. These tests render HTML directly and
load it with `page.setContent(...)` — **no server, Spring context, DB, or auth** — so only
the JS is under test. Extend `JsBehaviorTest` (`@Tag("js")` is inherited) and run with
`./mvnw test -Pjs-tests`; the default build excludes the `js` group. Full do/don't
guidance: `docs/JS-Behavior-Tests.md`.