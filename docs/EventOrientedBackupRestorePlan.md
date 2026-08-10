# Event-Oriented Backup / Restore

**Status:** `open — design settled with Ted 2026-08-10, not started`
**Supersedes:** the command-replay export/import (`CommandImporter`, `ImportableCommand`,
`ImportableCommandTypes`) in its entirety.
**Unblocks:** `DecisionContextQueryDesign.md` (its Concern §1 dissolves — see "Ripple effects").

## Why this exists

Commands were stored to support a future **undo** feature — they are the record of *intent*.
The **events** are, and always will be, the source of truth. Today's backup gets this backwards:
it exports the command log and *re-executes* every command on import to regenerate events. That
is a misuse of the command log, and it has three concrete costs:

1. **Restored events are not the originals.** `EventStore.append` mints a fresh `event_id`
   (`UUID.randomUUID()`), a fresh `sequence` (`nextSequence`), and a fresh append `timestamp`
   (`Instant.now()`) for every event. A round-tripped database is behaviourally equivalent but
   not *identical* — event ids and ordering timestamps all change.
2. **Every command must fake its decision context on import.** `ImportableCommand.events()` gets
   no event stream and no read model, so `CancelHotelRequest` hardcodes
   `new CancelHotelContext(true, null, IMPORT_BYPASS_INSTANT)`. Each fake is an untested
   behavioural fork between live and import. This is the wall `DecisionContextQueryDesign.md` hit.
3. **Re-execution couples the backup format to live domain logic.** A backup can only be restored
   by code that can still re-run the exact command logic that produced it.

Restoring events **verbatim** removes all three. Commands stay in the backup as opaque history,
for the undo feature, but are never re-executed.

## Decisions taken (with Ted, 2026-08-10)

| # | Decision | Choice |
|---|---|---|
| 1 | Backward compatibility with existing command-format backup files | **Clean break.** No legacy import path. Take one fresh event-format backup from current prod; old command-only files stop being restorable. Fits the wipe-then-restore-once workflow. |
| 2 | Keep the ability to rebuild events by re-executing commands? | **Retire it entirely.** Restore inserts events verbatim and never runs command logic. `ImportableCommand`, `events()`, and the round-trip convention go away. |
| 3 | File layout | **One combined file** — a single JSON document with `commands` and `events` sections (format `version: 2`). |
| 4 | Which `command_log` rows to include | **All statuses** — `SUCCEEDED`, `PENDING`, `FAILED_DOMAIN`, `FAILED_PERSIST`, `ABANDONED`. Full `command_log` fidelity. |

**Terminology:** the feature is **backup / restore**, not export / import. Rename accordingly
(routes, classes, templates — see "Rename & retire inventory").

**Deliberate compatibility break acknowledged.** The
[export/import file-compat guard](../.claude memory `feedback_export_import_compat`) says warn
before breaking backup files. This *is* that break, chosen knowingly: format bumps to `version: 2`,
old files are not read.

## What the schema gives us (verified 2026-08-10)

```
command_log(command_id PK, timestamp, type, payload jsonb, event_ids uuid[], status, error)
event_log(sequence PK, event_id, command_id → command_log(command_id), timestamp, type, payload jsonb)
```

Two facts drive the restore algorithm:

- **`event_log.command_id` is an FK to `command_log`.** Restore must write **commands first**,
  then events, or the event insert violates the constraint.
- **`sequence` is the event PK.** Verbatim restore reuses stored sequences, so the target table
  must be empty for those ids (the wipe-then-restore workflow) — or the insert skips ids already
  present (idempotent resume). Both are supported; see "Restore algorithm".

## Target design

### Backup file format (`version: 2`)

A single JSON object. `commands` precedes `events` for readability and to mirror restore order;
restore does not rely on array order for events (it sorts by `sequence`).

```json
{
  "version": 2,
  "commands": [
    {
      "commandId": "…-uuid",
      "timestamp": "2026-07-01T14:00:00Z",
      "type": "dev.ted.jittertravel.web.BookHotelRequest",
      "payload": { "…": "…" },
      "eventIds": ["…-uuid"],
      "status": "SUCCEEDED",
      "error": null
    }
  ],
  "events": [
    {
      "sequence": 1,
      "eventId": "…-uuid",
      "commandId": "…-uuid",
      "timestamp": "2026-07-01T14:00:00.123Z",
      "type": "HotelBooked",
      "payload": { "…": "…" }
    }
  ]
}
```

Field-by-field:

- **Commands are opaque.** `type` is stored **verbatim** as it appears in `command_log.type`
  (today an FQCN). Restore never resolves it to a class — commands are historical rows, not
  something to deserialize or run. This is what lets us delete `ImportableCommandTypes` and the
  logical-name registry for commands. `eventIds`, `status`, `error` round-trip as-is so the
  command↔event linkage the undo feature needs survives.
- **Events use the stable logical `type`.** Backup writes `EventTypes.logicalNameFor(...)` and
  restore reads `EventTypes.classFor(...)`, exactly as `event_log` already stores it. This is the
  one place a class rename must stay decoupled from the wire, because event payloads *are*
  deserialized (validation pass + boot replay) and evolved by `EventPayloadUpcaster`.
- **`sequence`, `eventId`, `timestamp` are preserved exactly.** That is the whole point:
  verbatim events.

> **Note — commands reference events they don't contain, and vice-versa.** With "all statuses",
> some commands (`FAILED_*`, `PENDING`, `ABANDONED`) have no events; that's fine, nothing references
> them. Every event's `commandId`, however, must appear in `commands`. Validation checks this
> (below) so the FK can never fail mid-restore.

### Backup (write path)

`BackupService.backupJson()` (renamed from `CommandImporter.exportJson`):

1. `persister.findAllCommandsForBackup()` — **all** rows now, ordered by `timestamp, command_id`.
   Drop the `WHERE status = 'SUCCEEDED'` filter currently in `findAllCommandsForExport`.
2. `persister.findAllEventsForBackup()` — every `event_log` row, ordered by `sequence`, carrying
   the raw payload JSON (no deserialize needed to write a backup).
3. Serialize the combined `{version, commands, events}` document, pretty-printed.

### Restore (read path) — validate-then-apply, preserved

The existing two-pass safety property is kept, re-pointed at events:

**Pass one — validate, write nothing.** Collect *all* problems in one report (unchanged
philosophy; the dry-run `validateJson` endpoint stays):

- Parse the document; reject a missing/again-unknown `version`.
- **Every event payload deserializes** via `EventPayloadUpcaster` + `EventTypes.classFor` +
  `treeToValue`. This replaces "recompute the command's events" as the thing that catches a
  corrupt or schema-incompatible backup *before* any write. (A valid event that once resolved
  its zone cannot fail that way again — but a payload that no longer binds to its event class
  will, and we want that reported, not thrown mid-write.)
- **Referential integrity:** every `events[].commandId` is present in `commands`; `sequence` and
  `eventId` are unique within the file; `commandId` is unique within `commands`.
- Report every failure together (one editing round to fix the file).

**Pass two — apply, in one transaction, commands before events.**

- Skip commands whose `command_id` is already in `command_log`, and events whose `sequence` is
  already in `event_log` (idempotent resume; keys change from today's command-only skip to the
  event `sequence`).
- Insert command rows **verbatim** (all columns, `payload` as `jsonb`, `status`/`error`/`event_ids`
  as stored) — no `saveCommand`/`PENDING`→`SUCCEEDED` dance, that was for live execution.
- Insert event rows **verbatim** — reusing `sequence`, `event_id`, `command_id`, `timestamp`.

This path **does not go through `CommandExecutor`**. That is intentional and is *not* a violation
of the "use CommandExecutor, never EventStore" rule (CLAUDE.md): that rule governs **application
services appending new events from commands**. Restore is a bulk infrastructure load of
already-durable events, so it lives in `PostgresPersister` (a `@Repository`) and the orchestrating
`BackupService` holds the persister, not `EventStore`. Two guarantees still have to be re-honoured
explicitly, because they normally live in `CommandExecutor`:

- **Read-only mode must still block a restore.** `CommandExecutor.refuseWhenReadOnly` won't run.
  `BackupService` checks read-only up front and refuses before writing anything. (Cheapest wiring:
  keep a reference to the existing read-only signal — `CommandExecutor.isReadOnly()` delegates to
  `EventStore` — without taking `EventStore` as a field.)
- **Persist-before-notify** is satisfied by construction: the command+event insert transaction
  commits *first*, and only then does `BackupService` trigger `EventStore.rebuildFromPersistence()`
  (see next). Projectors never see an event that isn't already durable.

### Rebuilding read models after a restore — live rebuild (no restart)

Restored events must reach the projectors, **without restarting the app**. Today the only way a
projector gets built is at boot: `EventSourcingConfig` constructs each of the ~19 projectors empty
and folds it with `projector.handle(eventStore.findAll())`. There is no reset primitive, so we add
one and re-run that same replay on demand.

**Three pieces:**

1. **`reset()` on `EventStreamConsumer`.** Each projector clears its own state field(s) — one line
   for most, five `Map.clear()` calls for `ItineraryProjector`. The interface gains:
   ```java
   public interface EventStreamConsumer {
       void handle(Stream<StoredEvent> eventStream);
       void reset();            // clear all projected state
   }
   ```
2. **`EventStore.rebuildFromPersistence()`.** Under the existing `transactionLock`: clear the
   in-memory `events` list, reload via `persister.loadAllEvents()`, reset
   `nextSequence = maxSeq+1`, then for **each** subscriber `reset()` then `handle(findAll())`.
   ~15 lines — it is the boot replay, re-run.
3. **Restore calls it** after the transaction that wrote commands + events commits.

**The correctness trap that makes `reset()` mandatory:** the folds are **not idempotent**, so you
cannot re-`handle()` on top of live state. `BookedFlightsProjector.apply()` *appends* to a
per-flight change-history list; re-running the stream without clearing would double every history.
`reset()` (clear-then-refold) is the only safe rebuild. A **completeness guard test** protects it:
reflect over each projector's fields and assert every `Collection`/`Map` is empty immediately after
`reset()`, so a forgotten `.clear()` fails a test instead of silently leaking stale rows into a view.

**Bonus — this also fixes a latent truncate bug.** `/admin/database/truncate` today empties the DB
but leaves the in-memory projectors and the `EventStore` list populated until the next restart.
Route truncate through the same `rebuildFromPersistence()` and it rebuilds against the now-empty DB,
so the views go empty immediately. Same primitive, one existing rough edge closed.

**Wiring note (arch rule).** `rebuildFromPersistence()` lives on `EventStore` and iterates the
subscribers it already holds — no application service gains an `EventStore` field, so
`ApplicationServicesUseCommandExecutorTest` is unaffected. `BackupService` triggers the rebuild
through the same non-`EventStore` seam it uses for the read-only check.

## Rename & retire inventory

### Retire (delete)

- `web/ImportableCommand.java` — the interface, `events()`, `IMPORT_BYPASS_INSTANT`.
- `web/ImportableCommandTypes.java` — command logical-name registry (events keep `EventTypes`).
- `application/CommandImporter.java` — replaced by `BackupService` (below).
- Tests: `CommandExportImportRoundTripTest`, `CommandImportSafetyTest` (rewritten against the new
  path, see "Tests"), `ImportableCommandTypesTest`.
- **The `events()` override on all 12 request/command types**: `BookFlightRequest`,
  `ChangeFlightRequest`, `BookHotelRequest`, `ChangeHotelRequest`, `CancelHotelRequest`,
  `BookTrainRequest`, `ChangeTrainRequest`, `PlanTentativeConferenceRequest`,
  `PlanGatheringRequest`, `ChangeGatheringRequest`, `MigrateConferenceToGathering`,
  `ClearDifferentCityConflict`.
  **Verify first (one grep per type):** confirm each `events()` is called *only* from the importer,
  not from the live controller path, before deleting it. The interface Javadoc says events are
  "recomputed deterministically from its payload" for replay — import-only by design — but confirm,
  because the live path builds events through `DomainCommand.execute(context)`, a different method.
  This also lets `CancelHotelRequest` drop its hardcoded `CancelHotelContext` fake.

### Rename (backup/restore terminology)

- `CommandImporter` → `BackupService` (`application`), methods `exportJson→backupJson`,
  `importJson→restoreJson`, `validateJson` kept (dry run).
- `AdminController` routes: `/admin/export→/admin/backup`, `/admin/import→/admin/restore`,
  `/admin/import/validate→/admin/restore/validate`. Update `SecurityConfig` matchers **and**
  `AuthorizationMatrixTest`'s `policy()` matrix in the same change (deny-by-default rule).
- Templates: `admin-import.html→admin-restore.html`, `admin-import-success.html→
  admin-restore-success.html`; nav/labels on `admin-home.html`, `index.html`.
- Downloaded filename: `commands.json` → `jittertravel-backup.json`.

### New / changed persister methods

- `findAllCommandsForBackup()` — like `findAllCommandsForExport` but **all statuses** and all
  columns (`event_ids`, `status`, `error`, `timestamp`).
- `findAllEventsForBackup()` — all `event_log` rows with raw payload JSON, ordered by `sequence`.
- `restoreCommandsAndEvents(commands, events)` — one `@Transactional` method, commands then events,
  verbatim inserts, idempotent on `command_id` / `sequence`.

### New / changed for live rebuild

- `EventStreamConsumer.reset()` — new interface method; implemented by **every** projector
  (~19: `LocationAuditProjector`, `TentativeConferenceProjector`, the five `*CalendarProjector`s,
  `Booked*`/`Tentative*`/`*DetailsViewProjector`s, `ItineraryProjector`, `ScheduleGapProjector`,
  `PlannedGatheringsProjector`, …). Each clears its own `Map`/collection fields.
- `EventStore.rebuildFromPersistence()` — clear in-memory events, reload from persister, reset
  `nextSequence`, then `reset()`+`handle(findAll())` each subscriber, under `transactionLock`.
- `AdminController` truncate path routes through `rebuildFromPersistence()` (fixes the stale
  in-memory state after `/admin/database/truncate`).

## Tests

- **`BackupRestoreRoundTripTest`** (replaces `CommandExportImportRoundTripTest`): seed events via
  the live path, back up, wipe, restore, and assert the `event_log` is **byte-for-byte identical** —
  same `sequence`, `event_id`, `timestamp`, payload. This is the property the old test could not
  assert (old import changed all three). Include a command of **each non-SUCCEEDED status** to prove
  "all statuses" round-trips.
- **`RestoreSafetyTest`** (replaces `CommandImportSafetyTest`): a backup with one un-bindable event
  payload writes **nothing** and reports every bad entry; an event whose `commandId` is absent from
  `commands` is rejected in pass one (never an FK error in pass two); re-running a partially-applied
  file resumes by skipping present `sequence`s.
- **Read-only guard test:** restore in read-only mode writes nothing.
- **Live-rebuild test:** after a restore (no restart), a projector-backed view reflects the
  restored data; and a restore over pre-existing in-memory state does **not** double-count
  (guards the non-idempotent fold — e.g. `BookedFlightsProjector` change-history length).
- **Reset-completeness guard:** reflect over each projector's fields and assert every
  `Collection`/`Map` is empty right after `reset()`. Fails when a new field is added without a
  matching `.clear()`.
- **Truncate test:** `/admin/database/truncate` leaves every projector-backed view empty
  *without a restart*.
- **`@WebMvcTest` for the renamed admin endpoints** (Thymeleaf render + mapping/status), with
  `@WithMockUser` and `.with(csrf())` on the POSTs.
- **`AuthorizationMatrixTest`** updated for the renamed routes.

## Ripple effects

- **`DecisionContextQueryDesign.md` is unblocked.** Its Concern §1 (the import blocker) disappears:
  restore no longer calls `command.events()`, so a command whose decision depends on folded event
  state has **nothing to fake** — its events are restored directly. Update that doc's status from
  `paused — blocked on the export/import rethink` and revise Concern §1 to "resolved by
  `EventOrientedBackupRestorePlan.md`". *(Ask Ted before editing that doc.)*
- **`Backlog.md`** — the "Export/import needs a wider decision" loose follow-up is now *owned* by
  this doc; add a row and flip the DecisionContextQueryDesign entry off "blocked". *(Ask first.)*
- **`ApplicationServicesUseCommandExecutorTest`** still passes: `BackupService` does not take
  `EventStore` as a constructor parameter; verbatim inserts live in `PostgresPersister`.
- **`TaggedEventStoreQueryingDesign.md` / `Event.tags()`** — unaffected. Adding a `tags` column
  later is orthogonal; a verbatim event backup would simply carry it.

## Suggested implementation order

1. **Live rebuild first, on its own.** `EventStreamConsumer.reset()` + the ~19 projector
   implementations + the reset-completeness guard test; `EventStore.rebuildFromPersistence()`;
   route `/admin/database/truncate` through it. This slice stands alone (it fixes the truncate
   staleness bug) and de-risks the rest — restore just calls a method proven here.
2. **Persister**: `findAllCommandsForBackup`, `findAllEventsForBackup`,
   `restoreCommandsAndEvents` (+ their tests). Pure data, no wiring churn.
3. **`BackupService`**: backup + validate + restore two-pass, read-only guard, triggers the
   rebuild from step 1. Round-trip and safety tests green here, before any UI rename.
4. **Retire** `ImportableCommand`, `ImportableCommandTypes`, the 12 `events()` overrides,
   old tests (verify no live `events()` callers first).
5. **Rename** routes/templates/labels; update `SecurityConfig` + `AuthorizationMatrixTest`
   together; new `@WebMvcTest`s.
6. Run the **All Tests** run configuration.

## Open decisions for Ted

1. **Restore into a non-empty database:** always wipe-first (verbatim sequences assume an empty
   target; idempotent skip is the only concession), or is merging two datasets ever wanted? The
   plan assumes wipe-first, matching current workflow.
