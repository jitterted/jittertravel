# Legacy Event Eager Migration — bake the zone in, get the resolver out of replay

**Status:** `built 2026-08-16` — shipped as an OWNER-only admin action plus a per-event schema-version
stamp and a boot-replay preflight (see "What shipped" below). Drafted after the 2026-08-16 production
incident (see "Why this exists"). **Related:** `UtcDatetimeStoragePlan.md` (introduced read-time zone
resolution), `EventOrientedBackupRestorePlan.md` (verbatim backup writes the raw payload, so it never
heals a legacy row), `LocationZoneResolver`, `EventPayloadUpcaster`.

## What shipped (2026-08-16)

Built together in one session; full suite green at 880.

- **Per-event schema-version stamp.** New `event_log.schema_version` column (nullable, no default —
  legacy rows are a per-type mix, so no single backfill value is right). Versions are **per type**:
  `EventTypes` now carries each type's `currentSchemaVersion` (the nine `ZonedTimestamp` types = 2,
  everything else = 1) via an overloaded `register`. The append path stamps every new row; restore
  carries the stamp verbatim. Decided with Ted: **column, not a payload key** (keeps domain payloads
  pure, so golden samples are untouched), at the cost of a **backup-format bump to version 3**.
  This is the *stamp* half of event-schema versioning; the *framework* half (a per-type upcaster
  chain driven by the stamp) was deliberately deferred until a second real migration shaped it
  (no-abstraction-before-second-user). **That framework is now built** (2026-08-18): the second
  migration — `ConferencePlanned` v2→v3 adding `format` — is what shaped it. See
  `EventPayloadUpcasterDesign.md` for the version-ladder design; the summary is that
  `EventPayloadUpcaster` became a composite that climbs a payload from its stored `schema_version`
  to current by applying one small `EventUpcaster` rung per version step.
- **Backup format v3.** `BackupService` writes v3 (events carry `schemaVersion`) and **restores both
  v2 and v3** — a v2 event simply has no stamp (null), exactly like a pre-migration row — so Ted's
  existing backups are never orphaned. `BackupRestoreRoundTripTest` covers the v2 backward-compat path.
- **The migration itself:** `LegacyEventMigration` (application) + `PostgresPersister.migrateEventPayloads`
  (transactional `UPDATE payload, schema_version WHERE sequence`, identity columns untouched). A row is
  rewritten iff its payload changes under upcast **or** its stamp is missing/wrong; already-current,
  correctly-stamped rows are skipped, so the whole thing is idempotent. Validate-then-apply: one bad
  row (unresolvable zone / unbindable) is reported and aborts the whole run with zero writes. Refuses
  in read-only mode (checked up front, like restore — it appends no new events, so it does not route
  through `CommandExecutor`).
- **Trigger:** OWNER-only `/admin/migrate-legacy-events` (GET preview with counts + POST to run),
  under the existing `/admin/**` matcher, with its own `AuthorizationMatrixTest` row and a nav card
  on the admin home. The page warns to take a backup first and, on success, to take a fresh one and
  restart to re-verify a clean eager replay.
- **Boot-replay preflight** (its own Cleanup-Tasks item, built here): `BootReplayPreflightTest`, a
  `@Tag("replay-preflight")` tier excluded from the default build. Point it at a production backup
  (`./mvnw test -Preplay-preflight -Dpreflight.dump=…`); it restores into a scratch Testcontainer DB
  — whose validate pass runs the exact upcast→classFor→bind boot uses — and then drives
  `loadAllEvents()` over the loaded rows, failing (with the offending row named) on anything that
  would abort boot. No dump ⇒ it skips.

**Decisions resolved with Ted (the "Open decisions" below are now settled):** (1) trigger = in-place
admin UPDATE, not a backup-file transform; (2) `type` FQCN→logical normalization = **deferred** (kept
out of this pass — orthogonal cleanup the stamp does not need); (3)/(4) retirement schedule unchanged
— the upcaster's legacy timezone rungs (the `*TimeZoneUpcaster` classes), FQCN mapping and
Antwerp-style resolver hacks stay until no pre-migration backup can be restored, each retirement
gated on the new preflight. Plus the
schema-version stamp above, which the original plan did not include.

## Why this exists

Legacy events — written before datetimes carried a zone — store a **bare wall-clock scalar**
(`"checkIn": "2026-06-07T15:00:00"`). `EventPayloadUpcaster` rewrites them into the current
`ZonedTimestamp` shape (`{"utc": ..., "zone": ...}`) **lazily, at read time**, re-resolving the
zone from `LocationZoneResolver` on **every boot**. Three costs follow directly, all seen on
2026-08-16:

1. **Replay is permanently hostage to the resolver's coverage.** A single legacy row whose location
   the curated tables don't know (`Casablanca / Morocco`) aborts the whole boot replay and drops the
   app to read-only. The resolver must know *every* legacy location *forever*.
2. **Every legacy data-entry error becomes a permanent resolver entry.** A hotel stored with a city
   in its country field (`Antwerp / "Brussels"`) resolves nowhere, so a targeted hack was added
   (`Europe/Brussels → "antwerp"` in the city table). It **cannot be removed**: fixing the hotel in
   the UI appends a *new* corrected event but never rewrites the bad original, which keeps replaying.
3. **The resolver runs on data it should never touch.** By the time an event is stored, its zone is
   known; re-deriving it on every boot is wasted work and a growing coupling surface.

New events do **not** have this problem — the write path resolves (or the operator explicitly picks)
a zone at entry and stores the `{utc, zone}` object, so replay never re-resolves them, and an unknown
new location is a non-fatal form re-prompt (`BookHotelController` catches `ZoneResolutionException`
and asks for a `CommonZone`). The problem is a **closed set** of already-stored legacy rows.

**The fix:** migrate those legacy rows **once** — read each, resolve its zone a single time, and
rewrite the stored `event_log.payload` to the current shape. Afterwards no bare-scalar rows remain,
the resolver leaves the replay path, and the per-error hacks can be deleted.

This is the standard event-sourcing move from **lazy upcasting** (transform on every read) to
**eager upcasting** (transform the store once). It is a *shape* migration that **materializes a
value already being derived** — the event's meaning is unchanged.

## Prerequisite: replay must already succeed

The migration reads events through the **same upcast + resolve path** as boot. It therefore needs
the resolver to still know every legacy location *at migration time*. So the order is:

1. Make replay succeed first (add the missing resolver entries — done 2026-08-16: Morocco, Antwerp).
2. Run this migration to bake the zones in.
3. **Then** the resolver entries added in step 1 are only needed to restore *old* backups (below),
   not to boot — and can be retired once those backups are gone.

This migration is **not** a fix for a crashing boot; it is how you *remove the resolver dependency*
once boot works.

## What counts as "legacy" (precise detection)

A row needs migrating iff running its payload through
`EventPayloadUpcaster.upcast(type, payload, storedVersion)` **changes the payload**. That is exactly
the set with a bare-scalar datetime field, per type:

| Logical type | Legacy datetime fields | New shape |
|---|---|---|
| `HotelBooked`, `HotelChanged` | `checkIn`, `checkOut` (+ optional `cancelBy`) | `{utc, zone}` objects |
| `TrainBooked`, `TrainChanged` | `departureDateTime`, `arrivalDateTime` | `{utc, zone}` objects |
| `FlightBooked`, `FlightChanged` | `departureDateTime`, `arrivalDateTime` | `{utc, zone}` objects |
| `ConferencePlanned` | `startDate`, `endDate` | `{utc, zone}` objects |
| `GatheringPlanned`, `GatheringChanged` | `date` + `startTime` + `endTime` | `startsAt` + `endsAt` (field **set** changes) |

The upcaster is already **idempotent** (a new-shape payload is returned unchanged), so the migration
does not need its own legacy/new discriminator — "did `upcast` change the JSON?" *is* the discriminator.

## Design

### Detect-and-rewrite (per row)

For every `event_log` row, in `sequence` order:

1. Read `type` (wire id — logical **or** legacy FQCN), the raw `payload` JSON, and the row's stored
   `schema_version` (null for a pre-stamp legacy row ⇒ the composite climbs from version 1).
2. `upcast = EventPayloadUpcaster.upcast(type, rawPayload, storedVersion)`.
3. If `upcast` is **byte-identical** to `rawPayload` → already new shape, **skip** (idempotent).
4. Otherwise → **validate** it binds: `treeToValue(upcast, EventTypes.classFor(type))` must succeed.
5. `UPDATE event_log SET payload = :upcast WHERE sequence = :sequence`. **Only `payload` changes** —
   `sequence`, `event_id`, `command_id`, and `timestamp` are untouched, preserving verbatim identity.

Validate-then-apply, mirroring restore: **pass one** upcasts + binds *every* row writing nothing and
reports all failures together; **pass two** applies the rewrites in **one transaction**. A single
unresolvable/​unbindable row aborts the whole migration with zero writes.

### Normalize `type` at the same time? (optional, recommended)

The legacy rows also store the **FQCN** in `type` (`dev.ted...HotelBooked`). The migration could also
`UPDATE ... SET type = EventTypes.logicalNameFor(EventTypes.classFor(type))`, collapsing the store to
logical names only. Benefit: once no FQCN rows remain (and no old backup will be restored), the
FQCN→logical mapping in `EventTypes` (`mapWireId(type.getName(), ...)`) and the wire-id normalization
just added to `EventPayloadUpcaster` become removable. Keep it **optional and behind the same
transaction**; it is pure cleanup, not required for correctness.

### Where it lives, and the guards it must honor

- **An OWNER-only admin action**, matching the existing pattern
  (`/admin/migrate-conferences`, `/admin/database/truncate`): a GET page describing what will change
  (count of rows to rewrite) and a POST that runs it. Add the routes to `SecurityConfig` **and**
  `AuthorizationMatrixTest`'s `policy()` in the same change (deny-by-default rule, CLAUDE.md).
- **Not on the boot path.** Silent data mutation on startup is exactly the kind of surprise this app
  avoids; an operator triggers it deliberately, once, at the console — same posture as restore/truncate.
- **Read-only mode must block it.** It mutates `event_log`. It cannot go through `CommandExecutor`
  (no new command/events — it rewrites existing rows), so, like restore, it lives in
  `PostgresPersister` and the orchestrating service checks `isReadOnly()` up front and refuses before
  any write (CLAUDE.md's CommandExecutor rule explicitly scopes to *appending new events from
  commands*, which this is not).
- **Take a `pg_dump` first** (`scripts/backup-db.sh`) — it rewrites real data. The migration's own
  idempotency and single-transaction-all-or-nothing are the in-app safety net; the dump is the
  out-of-band one.

### Rebuild after? No.

Restore/truncate need a restart because they change *which* events exist. This migration changes only
the **stored shape** of events whose deserialized value is **identical** after upcast — the same
`HotelBooked` object folds out either way. So projections are unaffected and **no rebuild/restart is
required** for correctness. (A restart is still fine, and re-verifies a clean replay from the now-eager
store — a good post-migration check.)

## Interaction with the backup format — do not retire too early

`findAllEventsForBackup()` writes the **raw** stored payload. So:

- **After** the migration, new backups contain new-shape payloads (good — they no longer depend on the
  resolver to restore).
- **Old** backups taken *before* the migration still hold bare-scalar payloads. Restoring one into
  current code still needs `EventPayloadUpcaster` + the resolver entries (Morocco/Antwerp) at read time.

Therefore the retirements this migration *enables* are gated on **old backups being out of rotation**,
not on the migration alone:

1. Run the migration → `event_log` is all new-shape.
2. Keep the upcaster's legacy timezone rungs (the `*TimeZoneUpcaster` classes), the FQCN wire-id
   mapping, and the per-error resolver entries **until no pre-migration backup can be restored**.
3. Then retire, in a later change: delete the `*TimeZoneUpcaster` rung classes (and drop them from
   `EventPayloadUpcaster.standard(...)`), the Antwerp-style hacks, and (if `type` was normalized) the
   FQCN mapping. Guard each retirement with the boot-replay preflight (its own cleanup item) proving
   nothing in the current store still needs to climb from below that rung — a row read below a
   deleted rung fails loud (the composite cannot reach the current version) rather than binding a
   stale shape.

Take **one fresh backup immediately after** the migration and treat it as the new floor, so the
window in which an old backup matters is short.

## Tests

- **`LegacyEventMigrationTest`** (service/persister): seed a legacy bare-scalar row (FQCN `type`),
  migrate, assert `event_log.payload` is now `{utc, zone}` **and** `sequence`/`event_id`/
  `command_id`/`timestamp` are unchanged. Include the gathering `date`+`startTime`+`endTime` →
  `startsAt`+`endsAt` field-set change.
- **Idempotency:** running the migration twice rewrites nothing the second time (pass-one "changed?"
  is false for every row); a store that is already all new-shape is a no-op.
- **Fold-equivalence:** the event object produced by loading a row *before* migration equals the one
  produced *after* — the property that lets us skip a rebuild. Assert on a representative projector's
  output over the same stream pre/post.
- **Validate-then-apply:** an un-bindable row makes the whole migration write nothing and report it
  (no partial rewrite).
- **Read-only guard:** migration in read-only mode writes nothing.
- **`@WebMvcTest`** for the admin routes (`@WithMockUser`, `.with(csrf())`), and an
  `AuthorizationMatrixTest` row (OWNER-only).
- All new tests **mutation-verified** (standard practice).

## Ripple effects

- **`LocationZoneResolver`** leaves the boot-replay path entirely once old backups are retired; its
  per-error entries (Antwerp) and any future ones can be deleted. It stays for the *live entry* path,
  which is where a curated resolver belongs.
- **`EventPayloadUpcaster`** keeps its wire-id normalization and the legacy timezone rungs until old
  backups are gone, then sheds those rungs by deleting the `*TimeZoneUpcaster` classes. The composite
  itself survives for any *future* shape migration (it is now a general version-ladder — see
  `EventPayloadUpcasterDesign.md`).
- **`EventTypes`** can drop the FQCN wire-id mapping only if `type` normalization is included **and**
  old backups are retired.
- **Boot-replay preflight** (separate cleanup item) is the tool that certifies each retirement above.
- **Backlog.md** gets a row (this doc) under "Open cleanups" / a new plan row.

## Open decisions for Ted

1. **Trigger:** OWNER-only admin page + POST (recommended, matches truncate/migrate-conferences), or
   a one-off you'd rather run as a script/CLI against a dump?
2. **Normalize `type` (FQCN → logical) in the same pass?** Recommended — it's what unlocks deleting
   the FQCN mapping later — but it widens the rewrite. In or out?
3. **How aggressively to retire afterward.** Do you want a definite "no backup older than DATE will
   ever be restored" line drawn, so the upcaster/resolver cleanups can be scheduled? Or keep the
   read-time upcaster indefinitely as a belt-and-suspenders restore path and treat only the *resolver
   coupling* as the thing this migration removes?
4. **Given your wipe-then-restore-once workflow** — is an even simpler path acceptable: take the
   current backup, transform *the backup file* to new-shape offline, wipe, and restore the transformed
   file? That avoids an in-place `UPDATE` entirely, at the cost of a one-time bespoke transformer and a
   full wipe/restore. (In-place migration is the recommendation because it needs no wipe and reuses the
   exact upcast path already tested, but this fits your established habit.)
