# Event-Oriented Backup / Restore

**Status:** `implemented 2026-08-11` — persister backup/restore, `BackupService` (backup +
validate + verbatim restore + read-only guard), retirement of the command-replay machinery, and the
backup/restore rename all landed. Rebuild-on-restart (decision 5) is the boot replay; no live
rebuild was added.
**Supersedes:** the command-replay export/import (`CommandImporter`, `ImportableCommand`,
`ImportableCommandTypes`) in its entirety.
**Unblocks:** `../DecisionContextQueryDesign.md` (its Concern §1 dissolves — see "Ripple effects").

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
   behavioural fork between live and import. This is the wall `../DecisionContextQueryDesign.md` hit.
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
| 5 | Rebuild read models after a restore/truncate live, or restart? | **Restart.** No live rebuild. After a restore or a truncate, restart the app; the existing boot replay rebuilds every projector from persistence. A live `reset()`+refold was designed and then rejected — see "Why not a live rebuild" below. Decided with Ted 2026-08-10. |

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

> **Superseded 2026-08-16:** the current format is **`version: 3`** — each event additionally carries
> a `schemaVersion` stamp (see `LegacyEventEagerMigrationPlan.md`). Restore reads **both v2 and v3**
> (a v2 event just has no stamp), so this section still describes the v2 shape faithfully; only the
> `version` number and the per-event `schemaVersion` field changed.

A single JSON object. `commands` precedes `events` for readability and to mirror restore order;
restore does not rely on array order for events (it sorts by `sequence`).

```json
{
  "version": 2,
  "metadata": {
    "createdAt": "2026-08-11T14:30:00Z",
    "source": "production"
  },
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

- **`metadata` is an informational header** (added 2026-08-11). `createdAt` is the backup instant in
  UTC; `source` is `"production"` when the app runs on Railway (env var `RAILWAY_ENVIRONMENT_NAME`
  is present) or `"local"` otherwise, resolved once at the config boundary by `BackupSource`. It is
  **written on backup and ignored on restore** — it never touches `command_log`/`event_log`, and a
  `version: 2` file without it still restores. Both `createdAt` and the filename stamp are
  normalized to UTC. Purely additive, so it did not bump the format version.
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

`BackupService.createBackup(createdAt, source)` — returns a `Backup(filename, json)` pair; the
controller captures `createdAt` (via the `Clock` bean) and `source` (via `BackupSource`) at the
boundary and names the download from them. `backupJson(createdAt, source)` builds the document
(renamed from `CommandImporter.exportJson`):

1. `persister.findAllCommandsForBackup()` — **all** rows now, ordered by `timestamp, command_id`.
   Drop the `WHERE status = 'SUCCEEDED'` filter currently in `findAllCommandsForExport`.
2. `persister.findAllEventsForBackup()` — every `event_log` row, ordered by `sequence`, carrying
   the raw payload JSON (no deserialize needed to write a backup).
3. Serialize the combined `{version, metadata, commands, events}` document, pretty-printed.

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
- **Persist-before-notify** is satisfied by construction, and by restart: the command+event insert
  transaction commits *first*, and the projectors are only rebuilt on the **next boot** replay (see
  below). Projectors never see an event that isn't already durable.

### Rebuilding read models after a restore/truncate — RESTART, not a live rebuild

**Decision (with Ted, 2026-08-10): restore and truncate require an app restart to take effect in
the read models. There is no live rebuild.** After writing the backup verbatim (restore) or
emptying the tables (truncate), restart the app; `EventSourcingConfig`'s boot replay already
constructs each of the ~19 projectors empty and folds `projector.handle(eventStore.findAll())`
against the current database. That is the whole rebuild — no new code. This fits the established
wipe-then-restore-once workflow, where an operator is already at the console.

Concretely, this means:

- Restore does **not** call back into `EventStore` after committing. `BackupService` needs no
  `EventStore` seam at all — only the read-only check (which it can read without holding
  `EventStore`).
- `/admin/database/truncate` keeps its **known, pre-existing** staleness: it empties the DB but the
  in-memory `EventStore` list and every projector stay populated until the next restart. This plan
  does **not** fix that; the restart that a restore needs also clears a truncate. If the staleness
  ever needs closing without the danger below, it is a separate, deliberate piece of work.

#### Why not a live rebuild (the rejected `reset()` design)

A live "no-restart" rebuild was designed and **built, then reverted**. It added `reset()` to
`EventStreamConsumer` (each projector clears its own state fields), an
`EventStore.rebuildFromPersistence()` that reloads the events and, for each subscriber, calls
`reset()` then `handle(findAll())`, and routed truncate through it. It worked and was fully tested.
It was rejected because putting `reset()` on the shared consumer interface bakes in a hazard:

1. **It is unsafe for any future *side-effecting* subscriber.** `rebuildFromPersistence()`
   re-delivers the **entire** event stream to **every** subscriber. That is safe *only* because
   every subscriber today is a pure projection. The day someone subscribes a consumer with side
   effects — the motivating example is an **email sender** — a rebuild would **re-fire those side
   effects** (re-send every email), and `reset()` cannot undo them. The interface would still
   *force* that consumer to implement `reset()`, so the danger is not even visible at the call site;
   it would ship as a silent no-op that re-emails the world on the next restore. We would then have
   to refactor the seam (split a `RebuildableProjection` from a side-effecting `EventListener`)
   precisely to remove what we had just added. Better not to add it.
2. **Transient empty-window for concurrent readers.** Projector reads (`views()`, `entries()`) do
   not hold `transactionLock`, so between `reset()` and the end of the refold a concurrent request
   (e.g. `/calendar`) can render an **empty or half-built** projection. Acceptable for a single-user
   manual action, but a real sharp edge a restart simply doesn't have.
3. **The correctness trap that made `reset()` mandatory in the first place.** The folds are **not**
   idempotent — `BookedFlightsProjector` *appends* to a per-flight change-history list, so
   re-`handle()`-ing on top of live state doubles every history. `reset()` (clear-then-refold) was
   the only safe in-place rebuild, which is exactly why every projector was *obligated* to implement
   it correctly, and why a completeness guard test was needed. A restart sidesteps the whole trap:
   boot always folds into freshly-constructed, empty projectors.

**If a live rebuild is ever revisited**, do it without a shared `reset()`: either a shadow-state
swap (`AtomicReference<State>` built fresh then swapped atomically, which also closes the
empty-window), or a distinct marker interface so only pure, rebuildable projections are ever
replayed. And note this whole calculus changes again for **DB-backed projections** — see
"Future: DB-backed projections" below.

#### Future: DB-backed projections

If a projection ever moves from an in-memory `Map` to its own database table, the restart-based
rebuild still works (boot re-folds into the table), but a *live* rebuild would need more than a
`reset()`: the `DELETE`/`TRUNCATE` + full re-insert must be **one transaction** (else a crash
mid-rebuild leaves a durably partial view), a full re-fold on every restore/truncate is likely too
expensive (DB projectors want **incremental** catch-up via a last-applied `sequence`, with full
rebuild as the rare path), the field-reflection completeness guard cannot check "am I empty?"
(needs a `COUNT(*)`), and atomicity pushes toward a **shadow-table swap**. In short: `reset()`
does not generalise to DB projections; treat the rebuild contract as an open question to reopen
when the first DB-backed projection lands.

## Rename & retire inventory

### Retire (delete)

- `web/ImportableCommand.java` — the interface, `events()`, `IMPORT_BYPASS_INSTANT`.
- `web/ImportableCommandTypes.java` — command logical-name registry (events keep `EventTypes`).
- `application/CommandImporter.java` — replaced by `BackupService` (below).
- Tests: `CommandExportImportRoundTripTest`, `CommandImportSafetyTest` (rewritten against the new
  path, see "Tests"), `ImportableCommandTypesTest`.
- **The `events()` override — on 10 of the 12, not all 12.** The verify-first check (below) found
  that **2 are called on the live path** and must keep `events()`:
  - **Deleted `events()` (importer-only, 10):** `BookFlightRequest`, `ChangeFlightRequest`,
    `BookHotelRequest`, `ChangeHotelRequest`, `CancelHotelRequest`, `BookTrainRequest`,
    `ChangeTrainRequest`, `PlanConferenceRequest`, `PlanGatheringRequest`,
    `ChangeGatheringRequest`. Each also shed `implements ImportableCommand`, `commandId()`, and its
    now-unused handler/context imports. `CancelHotelRequest` dropped its hardcoded
    `CancelHotelContext` fake.
  - **Kept `events()` (2):** `MigrateConferenceToGathering` and `ClearDifferentCityConflict` are
    internal-action commands whose `events()` **is** the live source, applied via
    `CommandExecutor.appendEvents(commandId, command, command.events())` in
    `ConferenceMigrationService` / `GatheringPlanning`. They only dropped `implements
    ImportableCommand` and `commandId()` (both import-only); `events()` stays.
  **Verify first (one grep per type):** confirm each `events()` is called *only* from the importer,
  not the live path, before deleting it — exactly the check that surfaced the 2 above. The live
  booking path builds events through `DomainCommand.execute(context)`, a different method; the two
  internal commands instead expose `events()` directly to `appendEvents`.

### Rename (backup/restore terminology)

- `CommandImporter` → `BackupService` (`application`), methods `exportJson→backupJson`,
  `importJson→restoreJson`, `validateJson` kept (dry run).
- `AdminController` routes: `/admin/export→/admin/backup`, `/admin/import→/admin/restore`,
  `/admin/import/validate→/admin/restore/validate`. Update `SecurityConfig` matchers **and**
  `AuthorizationMatrixTest`'s `policy()` matrix in the same change (deny-by-default rule).
- Templates: `admin-import.html→admin-restore.html`, `admin-import-success.html→
  admin-restore-success.html`; nav/labels on `admin-home.html`, `index.html`.
- Downloaded filename: `commands.json` → `jittertravel-backup-<source>-<utc-timestamp>.json`
  (e.g. `jittertravel-backup-production-2026-08-11T143000Z.json`; added 2026-08-11 — was
  `jittertravel-backup.json`).

### New / changed persister methods

- `findAllCommandsForBackup()` — like `findAllCommandsForExport` but **all statuses** and all
  columns (`event_ids`, `status`, `error`, `timestamp`).
- `findAllEventsForBackup()` — all `event_log` rows with raw payload JSON, ordered by `sequence`.
- `restoreCommandsAndEvents(commands, events)` — one `@Transactional` method, commands then events,
  verbatim inserts, idempotent on `command_id` / `sequence`.

### New / changed for rebuild

**None.** Per decision 5, rebuild is the existing boot replay — restore and truncate take effect on
the next app restart. No `EventStreamConsumer.reset()`, no `EventStore.rebuildFromPersistence()`, no
truncate rewiring. (An earlier draft added all three; it was reverted — see "Why not a live
rebuild".)

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
- *(No rebuild/reset/truncate-staleness tests — rebuild is the boot replay, already covered by the
  app starting. See decision 5.)*
- **`@WebMvcTest` for the renamed admin endpoints** (Thymeleaf render + mapping/status), with
  `@WithMockUser` and `.with(csrf())` on the POSTs.
- **`AuthorizationMatrixTest`** updated for the renamed routes.

## Ripple effects

- **`../DecisionContextQueryDesign.md` is unblocked.** Its Concern §1 (the import blocker) disappears:
  restore no longer calls `command.events()`, so a command whose decision depends on folded event
  state has **nothing to fake** — its events are restored directly. Update that doc's status from
  `paused — blocked on the export/import rethink` and revise Concern §1 to "resolved by
  `EventOrientedBackupRestorePlan.md`". *(Ask Ted before editing that doc.)*
- **`../Backlog.md`** — the "Export/import needs a wider decision" loose follow-up is now *owned* by
  this doc; add a row and flip the DecisionContextQueryDesign entry off "blocked". *(Ask first.)*
- **`ApplicationServicesUseCommandExecutorTest`** still passes: `BackupService` does not take
  `EventStore` as a constructor parameter; verbatim inserts live in `PostgresPersister`.
- **`TaggedEventStoreQueryingDesign.md` / `Event.tags()`** — unaffected. Adding a `tags` column
  later is orthogonal; a verbatim event backup would simply carry it.

## Suggested implementation order

1. **Persister**: `findAllCommandsForBackup`, `findAllEventsForBackup`,
   `restoreCommandsAndEvents` (+ their tests). Pure data, no wiring churn. (The former step 1, a
   live rebuild, is dropped — decision 5: rebuild is the boot replay, restore/truncate need a
   restart.)
2. **`BackupService`**: backup + validate + restore two-pass, read-only guard. After a successful
   restore, tell the operator to **restart** so the boot replay rebuilds the projectors. Round-trip
   and safety tests green here, before any UI rename.
3. **Retire** `ImportableCommand`, `ImportableCommandTypes`, the 12 `events()` overrides,
   old tests (verify no live `events()` callers first).
4. **Rename** routes/templates/labels; update `SecurityConfig` + `AuthorizationMatrixTest`
   together; new `@WebMvcTest`s.
5. Run the **All Tests** run configuration.

## Open decisions for Ted

1. **Restore into a non-empty database:** always wipe-first (verbatim sequences assume an empty
   target; idempotent skip is the only concession), or is merging two datasets ever wanted? The
   plan assumes wipe-first, matching current workflow.
