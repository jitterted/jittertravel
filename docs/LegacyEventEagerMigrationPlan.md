# Legacy Event Eager Migration — bake the zone in, get the resolver out of replay

**Status:** `planned 2026-08-16` — nothing built. Drafted after the 2026-08-16 production
incident (see "Why this exists"). Decisions below are **proposals for Ted**, not settled.
**Related:** `UtcDatetimeStoragePlan.md` (introduced read-time zone resolution),
`EventOrientedBackupRestorePlan.md` (verbatim backup writes the raw payload, so it never heals a
legacy row), `LocationZoneResolver`, `EventPayloadUpcaster`.

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

A row needs migrating iff running its payload through `EventPayloadUpcaster.upcast(type, payload)`
**changes the payload**. That is exactly the set with a bare-scalar datetime field, per type:

| Logical type | Legacy datetime fields | New shape |
|---|---|---|
| `HotelBooked`, `HotelChanged` | `checkIn`, `checkOut` (+ optional `cancelBy`) | `{utc, zone}` objects |
| `TrainBooked`, `TrainChanged` | `departureDateTime`, `arrivalDateTime` | `{utc, zone}` objects |
| `FlightBooked`, `FlightChanged` | `departureDateTime`, `arrivalDateTime` | `{utc, zone}` objects |
| `ConferenceTentativelyPlanned` | `startDate`, `endDate` | `{utc, zone}` objects |
| `GatheringPlanned`, `GatheringChanged` | `date` + `startTime` + `endTime` | `startsAt` + `endsAt` (field **set** changes) |

The upcaster is already **idempotent** (a new-shape payload is returned unchanged), so the migration
does not need its own legacy/new discriminator — "did `upcast` change the JSON?" *is* the discriminator.

## Design

### Detect-and-rewrite (per row)

For every `event_log` row, in `sequence` order:

1. Read `type` (wire id — logical **or** legacy FQCN) and the raw `payload` JSON.
2. `upcast = EventPayloadUpcaster.upcast(type, rawPayload)`.
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
2. Keep the upcaster's legacy branches, the FQCN wire-id mapping, and the per-error resolver entries
   **until no pre-migration backup can be restored**.
3. Then retire, in a later change: the upcaster's legacy `upcast*` branches, the Antwerp-style hacks,
   and (if `type` was normalized) the FQCN mapping. Guard each retirement with the boot-replay
   preflight (its own cleanup item) proving nothing in the current store needs it.

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
- **`EventPayloadUpcaster`** keeps its wire-id normalization and legacy branches until old backups are
  gone, then sheds the legacy branches (the class survives for any *future* shape migration).
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
