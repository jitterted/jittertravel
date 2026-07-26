# JitterTravel — Claude Code Notes

## Architecture Rules

### Event Storage: use CommandExecutor, never EventStore directly

Application services must **never** receive `EventStore` as a constructor dependency.
All event appending from application services must go through `CommandExecutor`:

- `commandExecutor.execute(...)` — for domain commands with a decision context
- `commandExecutor.appendEvents(...)` — for internal actions (clearing conflicts, migrations, etc.)

**Why:** `EventStore.append()` requires the command to already exist in `command_log` (foreign
key constraint). `CommandExecutor` enforces this ordering. Bypassing it causes FK violations
and partial writes (some events land, others don't).

**TODO:** Add an ArchUnit test that verifies no class in the `application` package has a
field of type `EventStore`. See `src/test/java/.../architecture/` for the right home.

### EventStore ordering invariant: persist before notify

`EventStore.append()` persists to the database **before** adding to the in-memory list
and notifying subscribers. This guarantees projectors only ever see events that are
durable. If persistence fails, the exception propagates and subscribers are never called.

Covered by `EventStoreTest.subscribersNotNotifiedWhenPersistenceFails()`.

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