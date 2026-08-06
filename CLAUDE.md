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

### Import is validate-then-apply, and resumable

`CommandImporter.importJson` runs two passes: pass one deserializes every entry and recomputes
its events **writing nothing**, pass two applies them (via `CommandExecutor`, per the rule above).
Any validation error means zero writes, and *all* bad entries are reported together.

**Why:** import failures are usually data problems in a few entries (an address whose zone
doesn't resolve). Applying entries as they are read leaves a half-populated database that has to
be wiped. Pass two also skips commands whose id is already in `command_log`, so re-running a
fixed file resumes instead of colliding on the primary key.

Keep new work in `events()` — it runs during validation, where throwing is safe and cheap.
Covered by `CommandImportSafetyTest`.

### Redaction: anonymous viewers are a first-class threat model

The calendar at `/calendar` is the one page anonymous visitors can see, and
`CalendarEntryRedactor` is the only thing standing between them and Ted's travel details.
Treat it as security code, not formatting code.

**Private, never render for an anonymous viewer:**

- hotel names and street addresses
- any URL that resolves to a place Ted sleeps or a booking — hotel `mapsUrl`, `editPath`
- travel times of day — departure/arrival/check-in/check-out. Anonymous viewers get *day*
  granularity only (the grid column), never a clock time, for flights, trains, and hotels.
- carrier/service identifiers — flight numbers, train `serviceId`, booking references
- links into owner/family surfaces (`/itinerary`, `/booked-*`, `/planned-*`)
- the whole `/schedule-problems` report (conflict/gap times, names, internal ids) — OWNER-only

**Public by decision** (do not "fix" these without asking Ted): the fact that travel is
happening on a given day, airport codes and city names for flights/trains/hotels, and
**conferences and gatherings in full** — name, venue, city, `infoUrl`, and start/end times.
Both are public events Ted speaks at or attends publicly.

**Known gap:** there is no entry kind for a *private* social event (a dinner with friends).
Such an event modelled as a gathering today would be fully public. Adding a private social
event kind is a planned feature (`docs/Cleanup_Tasks.md`); until it exists, any new
"private-ish" entry kind must get its own redacting branch, never reuse GATHERING.

**Rules for writing the code:**

1. **Redaction is deny-by-default.** In `CalendarEntryRedactor`, never write a branch that
   returns `entry` unchanged, and never copy a field through "because it's null today."
   Every branch constructs a new `CalendarEntry` naming each field explicitly, so adding a
   field to `CalendarEntry` breaks compilation in the redactor rather than silently
   publishing it. A pass-through branch means *every future field* leaks.
2. **On travel entries (FLIGHT, TRAIN, LODGING) a `SubtitleLine` carrying a
   `ZonedTimestamp` must never survive redaction.** `ZonedTimeTag` emits
   `datetime="<UTC instant>"` into the markup, so a time leaks in the attribute even when
   the visible text looks harmless. Redacted travel subtitles are `SubtitleLine.Text` or
   nothing. (Conference and gathering times are public — see above.)
3. **Every new route is deny-by-default too.** `SecurityConfig` ends in
   `.anyRequest().permitAll()`, so a new `@GetMapping` is public unless you add a matcher.
   Hiding the nav card in `index.html` (`th:if="${showDataEntryNav}"`) is *not* access
   control — the URL is still open. Add the route to `SecurityConfig` **and** to the
   `policy()` matrix in `AuthorizationMatrixTest` in the same change.
4. **Redaction is chosen at the boundary and applied inward.** The controller derives
   `isPublicUser` from `request.getRemoteUser()`; renderers must never re-derive viewer
   identity or reach for `SecurityContextHolder`.
5. **Every redaction change needs both tiers of test**: a unit test in
   `CalendarEntryRedactorTest` asserting the field is gone, and a
   `CalendarRedactionSecurityTest` case asserting the rendered anonymous body
   `doesNotContain` the secret through the real security chain. Assert on *absence* of the
   private value, not just presence of the placeholder.
6. **When in doubt, redact and ask.** A missing detail on a public calendar is a papercut;
   a leaked one is unrecoverable.

## Testing

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