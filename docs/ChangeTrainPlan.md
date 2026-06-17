# Plan: Change Train Trip (in-place edit)

## Context

`/booked-trains` today is a read-only j2html list (with the shared FUTURE/ALL toggle) and
no way to correct a booking. We want to let the owner **edit an existing booked train in
place** — same `tripId`, full-snapshot overwrite — modeled on the existing
`docs/slices/change-train_eventlanes.txt` slice and the `ChangeFlight` feature.

This is the *in-place change* model (one `TrainChanged` event overwrites every train
projection), **not** the hotel cancel+replace model. Decisions confirmed with the user:

1. **Model:** in-place — `ChangeTrain` command → single `TrainChanged` full-snapshot event,
   keyed by the unchanged `tripId`. No cancellation, no new id, no replacement linkage.
2. **Action gate:** none on the *original* departure. Validation matches Book Train /
   ChangeFlight: the *new* departure must be in the future and arrival after departure. You
   may edit a trip whose original departure already passed, as long as the new departure is
   in the future.
3. **EventTypes Phase 0:** included as a prerequisite phase (see below) — `TrainChanged` is a
   brand-new event and should be born with a stable logical name decoupled from its FQCN.
4. **Edit UI:** a new Thymeleaf edit/details page reached from a `/booked-trains` row,
   pre-filled from a new `TrainDetailsView` projection (mirrors `FlightDetailsView` +
   `ChangeFlightController`). POST form via Thymeleaf + CSRF per project rule.

The FUTURE/ALL filter on `/booked-trains` already exists (it is *the* reference impl), so
there is no filter work in this plan.

### Key architectural constraints (from CLAUDE.md + memory)
- New application services MUST append events via `CommandExecutor`
  (`application/CommandExecutor.java`) — never `EventStore` directly. **`ChangeFlight` uses the
  legacy direct-`EventStore` path; do NOT copy it.** Follow the `BookTrainCommand` ->
  `TrainBooking` -> `CommandExecutor.execute(...)` shape instead.
- Domain commands implement `DomainCommand<C extends DecisionContext>` and emit events from
  `execute(context)` (see `BookTrainCommand` + `BookTrainContext`). The command record carries
  the new field values; the context carries decision facts (`tripExists`, `now`).
- Optional `String` fields use `""` sentinel normalized in the compact constructor
  (`TrainBooked.serviceId` already does this).
- Every new `ImportableCommand` needs a `register(...)` line in `ImportableCommandTypes` and a
  case in `CommandExportImportRoundTripTest`.
- Projections must be covered by book→change lifecycle scenario tests (not by sealing `Event`).
- Thymeleaf `@Controller` endpoints need `@WebMvcTest`; POSTs need `.with(csrf())` +
  `@WithMockUser`; `@DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")` on every datetime-local
  field.

---

## Phase 0 — Event logical-name registry (own focused commit, before the new event)

**Problem (unchanged from the hotel plan — still not built):** events are persisted in
`event_log` by fully-qualified class name (`PostgresPersister.StoredEventRow.fromStoredEvent`,
`event.type().getName()`) and resolved on read via `Class.forName(type)`. Renaming or moving
any `Event` class breaks replay of every previously stored row. This is the same fragility
`ImportableCommandTypes` fixed for commands.

**Change:** introduce `EventTypes` (next to where events are resolved, `infrastructure/`),
mirroring `ImportableCommandTypes`:
- `register("TrainBooked", TrainBooked.class)` etc. for **every existing `Event`**; `register`
  seeds the logical name AND the current FQCN as a wire id, so existing `event_log` rows (which
  hold FQCNs) keep resolving — no data migration.
- `logicalNameFor(Class)` for writing, `classFor(String wireId)` for reading, with the same
  alias / append-only discipline as the command registry.
- `StoredEventRow.fromStoredEvent` writes `EventTypes.logicalNameFor(event.type())`;
  `toStoredEvent` resolves via `EventTypes.classFor(type)` instead of `Class.forName`.

**Tests** (mirror `ImportableCommandTypesTest`):
- Completeness: fails if any `Event` implementor is missing from the registry (reflection /
  classpath scan).
- Round-trip: an event written under a logical name resolves back to the right class.
- Legacy: a `type` column holding an old FQCN still resolves (alias path).

Ships independently; gives `TrainChanged` a stable identity from birth.

> If Ted would rather not block Change Train on Phase 0, this phase can be skipped and
> `TrainChanged` persists by FQCN like every other event today (the original "defer" option).
> Recommendation stands: include it.

---

## Phase 1 — Domain: `TrainChanged` event + `ChangeTrain` command

### `TrainChanged` (`domain/`)
Full snapshot, identical shape to `TrainBooked`:

```
TrainChanged(TrainTripId tripId,
             TrainStationAddress departureStation, LocalDateTime departureDateTime,
             TrainStationAddress arrivalStation,   LocalDateTime arrivalDateTime,
             String serviceId)  implements Event
```
- Compact constructor normalizes `serviceId` null→"" (copy from `TrainBooked`).
- Register in the Phase 0 `EventTypes` registry: `register("TrainChanged", TrainChanged.class)`.

### `ChangeTrainCommand implements DomainCommand<ChangeTrainContext>` (`domain/`)
- `ChangeTrainContext(boolean tripExists, LocalDateTime now) implements DecisionContext`.
- The command record carries the same six fields as `BookTrainCommand` (the new values).
- `execute(ChangeTrainContext context)` rules (reuse existing exceptions):
  - `if (!context.tripExists())` → throw `TrainNotFound("No train exists with that tripId")`
    (new exception in `domain/`; mirror `FlightNotFound`).
  - `if (departureDateTime == null || !departureDateTime.isAfter(context.now()))` →
    `DepartureNotInFuture` (existing).
  - `if (arrivalDateTime == null || !arrivalDateTime.isAfter(departureDateTime))` →
    `InvalidDateRange` (existing).
  - else `Stream.of(new TrainChanged(... full snapshot ...))`.
- **Tests:** `ChangeTrainCommandTest` covering happy path + each of the three error rules
  (the GWT cases already written in `change-train_eventlanes.txt`).

---

## Phase 2 — Application + projections + edit page

### 2a. Application service `ChangeTrain` (`application/`)
- Constructor deps: `CommandExecutor` + a way to fold the stream for existence (mirror
  `TrainBooking`; do NOT take `EventStore` directly per the ArchUnit intent).
- `changeTrain(ChangeTrainRequest request)`:
  - guard read-only.
  - fresh `commandId = UUID.randomUUID()` (a trip may be changed many times — the commandId is
    NOT the tripId, which is the aggregate id; same reasoning as `ChangeFlight`).
  - build `ChangeTrainContext(tripExists, LocalDateTime.now())` by folding the event stream:
    match `TrainBooked` **and** `TrainChanged` on `tripId` (read-only fold only).
  - `commandExecutor.execute(commandId, request, context, command)` — the append goes through
    `CommandExecutor`, never `EventStore`.
- A `ChangeTrainHandler` (mirror `BookTrainHandler`) maps `ChangeTrainRequest` →
  `ChangeTrainCommand`.

### 2b. Projector updates — handle `TrainChanged` everywhere `TrainBooked` is consumed
Four existing consumers, each gets a `TrainChanged` branch that overwrites/re-evaluates by
`tripId` exactly as the `TrainBooked` branch does (these are full snapshots):
- `BookedTrainsProjector`
- `ItineraryProjector`
- `TrainCalendarProjector`
- `ScheduleGapProjector`

Plus the new projector in 2c. **Tests:** a book→change lifecycle scenario test per projector
(guards the "silently missed event" risk) — the `[GWT: All train projections reflect the
change]` case in the slice file. No sealing of `Event`.

### 2c. `TrainDetailsView` + `TrainDetailsViewProjector` (`application/`)
- Model on `FlightDetailsView` / `FlightDetailsViewProjector`: handle `TrainBooked` and
  `TrainChanged` as upserts keyed by `TrainTripId` (both full snapshots → `put`). Expose
  `findById(TrainTripId)`.
- **Test:** `TrainDetailsViewProjectorTest` (book then change → reflects new snapshot).

### 2d. Web — edit page (Thymeleaf, mirrors `ChangeFlightController`)
- `ChangeTrainRequest implements ImportableCommand` (`web/`): the six datetime/string fields
  with `@DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")` on both `LocalDateTime`s.
  - `commandId()` returns `UUID.randomUUID()` (each change is a distinct command — copy the
    `ChangeFlightRequest` rationale).
  - `events()` → `new ChangeTrainHandler().handle(this).execute(new ChangeTrainContext(true,
    IMPORT_BYPASS_NOW))` (on import the trip is assumed already booked).
  - Register `register("ChangeTrain", ChangeTrainRequest.class)` in `ImportableCommandTypes`.
- `ChangeTrainController` (`@Controller`):
  - `GET /booked-trains/{tripId}` → pre-fill from `TrainDetailsViewProjector.findById`; render
    `change-train` template. Not found → flash + `redirect:/booked-trains`. Read-only →
    `redirect:/read-only`. (No flight-style external lookup endpoint — trains have no
    AeroDataBox equivalent.)
  - `POST /booked-trains/{tripId}` → path is source of truth for `tripId`; call `ChangeTrain`;
    map `TrainNotFound` → flash+redirect, `DepartureNotInFuture`/`InvalidDateRange` →
    `bindingResult.rejectValue(...)`, `ReadOnlyModeException` → `redirect:/read-only`; success →
    `redirect:/booked-trains`.
- `change-train.html` template: copy `change-flight.html` structure minus the lookup form;
  fields match `book-train.html`.
- `BookedTrainsRenderer.renderRow` — link each row to `/booked-trains/{tripId}` (the primary
  entry point to the edit page).
- **Navigation:** the row link is the entry point; per the new-page rule, confirm whether
  `index.html` also needs anything (likely not — it's reached from the list, like change-flight).
- **Security:** add `/booked-trains/*` (and `/booked-trains/*/**` if needed) to the
  `hasRole("OWNER")` matchers in `SecurityConfig`, mirroring the existing `/booked-flights/*`
  entries. Add rows to `AuthorizationMatrixTest` for `/booked-trains/{id}` GET + POST.

---

## Backward-compat / contract tests

`TrainChanged` is a new event (no old rows to read), so the read-side risk is low, but follow
the established infra:
- **Round-trip (required):** add a `ChangeTrain` case to `CommandExportImportRoundTripTest`
  (export a ChangeTrain command, re-import, assert the `TrainChanged` event reconstructs).
- **Golden (optional but cheap):** a `TrainChanged` case in `GoldenEventDeserializationTest`
  alongside the existing `TrainBooked` cases — inline the sample JSON as a text block (<30
  lines).
- **Write-side (Strictland):** out of scope — adoption is paused; do not add snapshot contract
  tests for this plan.

---

## Verification

- **Unit/slice:** `./mvnw test` — `ChangeTrainCommandTest`, the four projector lifecycle tests,
  `TrainDetailsViewProjectorTest`, renderer test (row link), `@WebMvcTest`
  `ChangeTrainControllerTest` (GET render + POST change, `@WithMockUser`, `.with(csrf())`),
  round-trip + golden tests, `AuthorizationMatrixTest`.
- **Phase 0:** `EventTypes` completeness/round-trip/legacy tests; boot against an existing DB to
  confirm replay of pre-change `event_log` rows still works.
- **End-to-end (run app):**
  1. Book a train; open its edit page from a `/booked-trains` row (pre-filled).
  2. Change departure/arrival/stations → `/booked-trains`, calendar, itinerary, schedule-gaps
     all reflect the new details by the same `tripId`.
  3. Try a past/invalid new departure → `DepartureNotInFuture` shown on the form.
  4. Try arrival not after departure → `InvalidDateRange` shown.
  5. Edit a trip whose original departure already passed but with a future new departure →
     allowed (no original-departure gate).
  6. Export then import a backup containing a ChangeTrain → trip shows the changed details.

## Out of scope
- Cancel/replace semantics for trains (this is in-place edit only).
- Migrating `ChangeFlight` off its legacy direct-`EventStore` path (separate cleanup).