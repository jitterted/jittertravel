# Plan: Change Gathering (in-place edit)

> **Status: DONE (2026-07-25, commit `f28cb49`).** `ChangeGatheringController`,
> `ChangeGatheringRequest` and `change-gathering.html` shipped. The out-of-scope UTC/zone
> migration noted below landed separately (2026-07-27, commit `ddf4ba8`). Cancelling a gathering
> is still open — see `docs/Backlog.md`.

## Context

`/planned-gatherings` is a read-only j2html list (with the shared FUTURE/ALL toggle) and there is
no way to correct a gathering once planned. This slice lets the owner **edit an existing planned
gathering in place** — same `GatheringId`, full-snapshot overwrite — modeled directly on the
`ChangeTrain` slice (`docs/ChangeTrainPlan.md`), which is the most recent and most
convention-compliant in-place change feature.

Branch note: this work is based on **`2800b83` (production)**, deliberately excluding the
in-progress UTC/ZonedTimestamp migration on `main`. Gatherings therefore stay on
`LocalDate`/`LocalTime` here; the UTC treatment is applied later when this branch is merged and
the TZ stack is rebased on top.

### Decisions

1. **Model:** in-place — `ChangeGathering` command → single `GatheringChanged` full-snapshot
   event, keyed by the unchanged `GatheringId`. No cancellation, no new id.
2. **Validation:** mirrors `PlanGatheringCommand`, plus existence. The *new* date must be in the
   future and the end time after the start time. A gathering whose original date has passed may
   still be edited, as long as the new date is in the future (same rule as Change Train).
3. **Edit UI:** new Thymeleaf page at `/planned-gatherings/{gatheringId}`, pre-filled from a new
   `GatheringDetailsView` projection, reached from an Edit link on each `/planned-gatherings`
   card. POST form via Thymeleaf + CSRF per project rule.
4. **Event identity:** `EventTypes` (the logical-name registry) already exists on this base, so
   `GatheringChanged` is born with a stable logical name — no Phase 0 prerequisite.

### Key constraints (CLAUDE.md + established conventions)

- Append via `CommandExecutor`, never `EventStore` directly.
- `now`/`today`/`commandId` captured at the controller boundary and passed inward.
- Optional `String` fields use `""` sentinel, normalized in the compact constructor.
- New `ImportableCommand` needs a `register(...)` line in `ImportableCommandTypes` **and** a case
  in `CommandExportImportRoundTripTest`.
- Projection coverage guarded by plan→change lifecycle scenario tests, not by sealing `Event`.
- Thymeleaf `@Controller` endpoints need `@WebMvcTest`; POSTs need `.with(csrf())` +
  `@WithMockUser`.

---

## Phase 1 — Domain

### `GatheringChanged` (`domain/`)
Full snapshot, identical shape to `GatheringPlanned`:

```
GatheringChanged(GatheringId gatheringId, String title, String venueName, Address location,
                 LocalDate date, LocalTime startTime, LocalTime endTime,
                 boolean speaking, String infoUrl) implements Event
```
- Compact constructor normalizes `venueName` and `infoUrl` null→"" (copied from `GatheringPlanned`).
- `register("GatheringChanged", GatheringChanged.class)` in `EventTypes`.

### `GatheringNotFound` (`domain/`)
New runtime exception, mirrors `TrainNotFound`.

### `ChangeGatheringCommand implements DomainCommand<ChangeGatheringContext>` (`domain/`)
- `ChangeGatheringContext(boolean gatheringExists, LocalDate today) implements DecisionContext`.
- Rules, in order (existence first, matching Change Train):
  - `!context.gatheringExists()` → `GatheringNotFound`
  - `date == null || !date.isAfter(context.today())` → `GatheringDateNotInFuture` (existing)
  - `endTime == null || !endTime.isAfter(startTime)` → `InvalidGatheringTimeRange` (existing)
  - else `Stream.of(new GatheringChanged(...full snapshot...))`

---

## Phase 2 — Application + projections

### 2a. `GatheringDetailsView` + `GatheringDetailsViewProjector` (`application/`)
Mirrors `TrainDetailsViewProjector`: `GatheringPlanned` and `GatheringChanged` are both full
snapshots, so each is an upsert keyed by `GatheringId`. Raw (unformatted) values so the form
binding can populate inputs directly. Exposes `findById(GatheringId)`.

### 2b. `ChangeGatheringHandler` + `ChangeGathering` (`application/`)
- Handler maps `ChangeGatheringRequest` → `ChangeGatheringCommand` (mirrors `PlanGatheringHandler`).
- Service takes `CommandExecutor` + `GatheringDetailsViewProjector`; reads existence from the
  read model, builds the context, and calls `commandExecutor.execute(...)`.
- `commandId` is a fresh UUID (not the gatheringId — a gathering may be changed many times),
  passed in from the controller alongside `today`.

### 2c. Projector updates — every consumer of `GatheringPlanned` gets a `GatheringChanged` branch
Four existing consumers, each overwriting by `GatheringId` exactly as the planned branch does:
- `PlannedGatheringsProjector`
- `GatheringCalendarProjector`
- `ItineraryProjector`
- `ScheduleGapProjector`

Plus the new details projector in 2a. Each gets a plan→change lifecycle test.

> Note: `DifferentCityConflictCleared` keys on `(gatheringId, conferenceId)`, so a cleared
> conflict stays cleared after an edit — even if the edit moves the gathering to a different
> city. Out of scope here; recorded in Cleanup_Tasks if it proves wrong in practice.

---

## Phase 3 — Web

- `ChangeGatheringRequest implements ImportableCommand` (`web/`): same fields as
  `PlanGatheringRequest`; `commandId()` returns a **random** UUID (each change is a distinct
  command); `events()` executes with `ChangeGatheringContext(true, IMPORT_BYPASS_DATE)`.
  Register `register("ChangeGathering", ChangeGatheringRequest.class)`.
- `ChangeGatheringController`:
  - `GET /planned-gatherings/{gatheringId}` → pre-fill from the details projector, render
    `change-gathering`; unknown/malformed id → flash + `redirect:/planned-gatherings`.
  - `POST /planned-gatherings/{gatheringId}` → path is the source of truth for the id;
    `GatheringNotFound` → flash+redirect; `GatheringDateNotInFuture` →
    `rejectValue("date", ...)`; `InvalidGatheringTimeRange` → `rejectValue("endTime", ...)`;
    success → `redirect:/planned-gatherings`.
- `change-gathering.html`: copy `plan-gathering.html` (including the address-parse helper), retitled,
  posting to the per-gathering URL with a hidden `gatheringId`.
- `PlannedGatheringsRenderer`: add an Edit link to each card.
- **Security:** add `/planned-gatherings/*` to the OWNER matchers, ordered *before* the
  `/planned-gatherings` list matcher (same ordering rule as `/booked-trains/*`).
- **Navigation:** the card Edit link is the entry point; `index.html` needs no new card (same as
  change-train/change-flight).

---

## Verification

- **Unit/slice:** `ChangeGatheringCommandTest`, `GatheringDetailsViewProjectorTest`, lifecycle
  tests in the four existing projector tests, `PlannedGatheringsRendererTest` (Edit link),
  `ChangeGatheringWebIntegrationTest` (`@WebMvcTest`, GET render + POST paths), a
  `/planned-gatherings/abc` row in `AuthorizationMatrixTest`, and a `ChangeGathering` case in
  `CommandExportImportRoundTripTest`.
- **Full suite:** `./mvnw test`.
- **End-to-end (manual):** plan a gathering → edit it from the list → confirm the list, calendar,
  itinerary and schedule-gaps all reflect the new details under the same id; past date and
  end-before-start both show form errors; export/import round-trips a changed gathering.

## Out of scope
- Cancelling/deleting a gathering.
- Re-evaluating cleared different-city conflicts after an edit (see note above).
- UTC/zone migration of gathering datetimes (lands with the TZ stack after merge).
