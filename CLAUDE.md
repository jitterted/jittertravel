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