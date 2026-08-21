# Normalizing the `event_log.type` column

> **Status: DONE — built 2026-08-19, run against production data 2026-08-21** (task completed
> `15:27:49Z`), and its `/admin/tasks` declaration retired the same day. `event_log.type` now holds
> one spelling per type in production.
>
> The aliases in `EventTypes` **stay** — they are append-only, and a pre-normalization backup still
> restores the old names, so today's build must keep resolving them. The maintenance rule this doc
> asked for ("normalizing afterwards costs the ability to roll back") is already stated on
> `EventTypes`.
>
> **Confirm the tail of the runbook if it wasn't done at the time:** step 6, take a fresh backup —
> it is the new floor and the first backup file whose `type` column is clean — and step 7, re-open
> `/admin/migrate-legacy-events` and see "Nothing to migrate", which is the idempotence check
> against real data. Tracked in `../Cleanup_Tasks.md` until Ted confirms.
>
> See `../Backlog.md` for the status of everything else, and `../EventPayloadUpcasterDesign.md` for
> the read path this plugs into.

## Problem

Renaming an event's **logical** name (`ConferenceTentativelyPlanned` → `ConferencePlanned`,
2026-08-19) leaves `event_log` holding both spellings: rows written before the rename keep the old
name, rows written after carry the new one. `EventTypes` aliases the retired ids so everything
*resolves*, but the column itself now has two names for one fact — and you read that column exactly
when something is already wrong.

Three wire ids point at this one type today:

| Wire id in `event_log.type` | Written by | Resolves via |
|---|---|---|
| `dev.ted.jittertravel.domain.ConferenceTentativelyPlanned` | builds before logical names existed | `alias` |
| `ConferenceTentativelyPlanned` | builds between logical names and the rename | `alias` |
| `ConferencePlanned` | builds after 2026-08-19 | `register` |

## What normalization does (as built)

Rewrite `event_log.type` to the current logical name for every row carrying a retired wire id, as a
third thing the **existing** eager migration does. That pass already rewrites stored rows in place —
it upcasts the payload and re-stamps `schema_version` — so this is the same operation on one more
column, not a new mechanism.

Touch points, all small:

1. `PostgresPersister.MigratedEventRow` — gains `String type`.
2. `PostgresPersister.migrateEventPayloads` — `UPDATE` now sets `type = :type`. Still one
   transaction, still matched by `sequence`; `sequence`, `event_id`, `command_id` and `timestamp`
   remain untouched, so identity is verbatim.
3. `LegacyEventMigration.plan()` — a `typeChanged` condition
   (`!row.type().equals(EventTypes.logicalNameFor(EventTypes.classFor(row.type())))`) alongside the
   existing `payloadChanged` / `stampChanged`, so a row whose *only* staleness is its name is still
   selected.
4. `MigrationResult` / `MigrationReport` + `admin-migrate-legacy-events` — a `renamed` counter next
   to payload rewrites and stamps, plus the one-way-door warning on the page.

It stays all-or-nothing, idempotent, and resumable, exactly like the pass it joins: plan the whole
table, refuse on any error, write nothing unless every row is good.

### Counting: the three counters overlap, so the row count is the only total

This is the one place the original sketch was wrong. `toRewrite` and `toStamp` were mutually
exclusive and `totalToMigrate()` was their **sum** — which drives the "Run migration" button. A
rename-only row (current payload, correct stamp, retired name — the *common* case after the
2026-08-19 rename, because the eager migration had already stamped those rows) increments neither,
so the sum would read 0 and the button would say "Nothing to migrate" while stale rows sat there.

As built: `toRename` counts every row whose name changes, **overlapping** the other two (one row can
be rewritten, stamped and renamed in a single `UPDATE`), and the report carries an explicit
`toMigrate` — the number of rows that will actually be written — which `totalToMigrate()` returns.
The admin table shows all four, with "Types to rename" labelled as overlapping.

### Scope: FQCN rows are normalized too

The condition catches *any* retired wire id, not just the conference rename — a row written before
logical names existed (`dev.ted.jittertravel.domain.HotelBooked`) is normalized to `HotelBooked` in
the same pass. That is the intent, and it widens the rollback statement below by exactly one class of
build: any build older than logical names (pre-2026-08-16) also stops being able to read the store.

### What was verified, not assumed

- **The upcaster is not confused by the change.** `EventPayloadUpcaster.upcast` resolves the wire id
  to a class and then to the logical name *before* choosing rungs, so a rung keyed on
  `ConferencePlanned` already fires for a row stored as `ConferenceTentativelyPlanned`. Normalizing
  the column changes which branch of that lookup is taken, never the outcome.
- **Nothing else compares a stored type string.** No SQL filters `event_log.type`; `/admin/eventlog`
  displays it (through `simpleTypeName`), the timeline displays it, and `BackupService` copies it.
  So the write is total and there is no half-normalized read path.
- **The write is transactional.** `migrateEventPayloads` is `@Transactional`, so an abort mid-list
  leaves no partial rename.
- **A persisted read model would not need invalidating for this.** Read models are rebuilt in memory
  at boot today, so the question is hypothetical — but normalization changes only a column, never a
  decoded event, so a checkpointed read model would stay correct. The caveat lands on the projector
  instead: dispatch on the decoded event **class**, and never filter by the raw type string in SQL.
  Upcaster changes and restores are the things that *would* force a rebuild — see
  *Persisted read models* in `../EventPayloadUpcasterDesign.md`.

## What it does **not** fix

**Restore is verbatim.** `BackupService` reinserts each event row's stored `type` unchanged, so
restoring a pre-normalization backup puts the old names straight back. You re-run the migration
afterwards — which is already true of `schema_version`, so it is consistent rather than a new wart.
Given the standing wipe-then-import workflow, the terminal state is: migrate once → take a fresh
backup → that file is clean from then on.

---

# Backward compatibility and rollback

**The asymmetry that matters: `EventTypes` aliases are forward-compatible only.** An alias teaches
*today's* build to read *yesterday's* names. Nothing can teach yesterday's build to read a name
invented after it shipped — its `EventTypes` is compiled in. So the moment stored data carries a name
that only the new build knows, **rolling the code back stops being a safe operation**.

Right now (rename shipped, column untouched) that has cost nothing: every row still says
`ConferenceTentativelyPlanned`, which *both* builds understand. Normalizing is precisely the step
that spends this.

## Compatibility matrix

| Reader | Un-normalized data (today) | Normalized data |
|---|---|---|
| **New build** (post-rename) | reads fine — retired ids are aliased | reads fine |
| **Old build** (pre-rename) | **reads fine** — this is the rollback safety we have today | **fails**: `Unknown event type: ConferencePlanned` |
| **New build reading an old backup file** | fine | fine (aliases apply to file rows too) |
| **Old build reading a post-normalization backup file** | n/a | **fails**, loudly, writing nothing |

## Exact failure modes if a rollback happens after normalizing

**Boot replay (the serious one).** `EventStore`'s constructor replays the whole log; an unknown type
throws inside `persister.loadAllEvents()`, which is caught and puts the app into **read-only mode**.
Because the exception escapes before `events.addAll(...)`, the in-memory list is left **empty** — so
the symptom is not an error page but a **fully-booted, read-only app with an empty calendar,
itinerary and every list view**, plus `Failed to Load and Process ALL Events from persistent store.
Entering read-only mode.` in the log. No data is lost or altered; the database is intact and the new
build reads it correctly again. But an operator who does not check the log will read the blank site
as data loss.

**Restore into an old build.** Safer, because restore is validate-then-apply: pass one bind-checks
every event and reports *all* failures, writing nothing. A post-normalization backup fed to a
pre-rename build fails cleanly with one error per conference event, and the database is untouched.

**Ordinary running.** Nothing else in the app resolves a type string, so there is no partial or
delayed failure — it is boot-time, total, and loud in the log.

## Mitigations, in the order they matter

1. **Take a backup immediately before running the migration, and keep it.** That file is the rollback
   artifact: it restores into *either* build, because every row in it still carries the old names.
   Without it, rolling back means hand-editing SQL. This is the whole safety story in one line.
2. **Let the rename settle first.** Do not normalize in the same window as the rename deploy. The
   rename is the change most likely to need a rollback; normalizing on top of it removes that option
   at exactly the wrong moment. A week of the renamed build running in production is enough.
3. **Escape hatch, if a rollback is ever needed after normalizing:** patch the old build with a
   reverse alias (`alias("ConferencePlanned", "ConferenceTentativelyPlanned")`) and deploy that. It
   works, but it is a build-and-deploy, not a fast path — treat it as the plan for "we must roll back
   and the pre-migration backup is gone", not as routine cover.
4. **Announce it in the admin UI.** The migrate page should say what the "renamed" count means and
   that it makes the data new-build-only, so the consequence is visible at the moment of the click
   rather than in this document.

## The rule this generalizes to

> Renaming an event **class** is free. Renaming its **stored logical name** is a one-way door for
> already-written rows unless you keep a backup taken before the rewrite.

Worth stating in `EventTypes`' maintenance rules if this ships: the existing rule says renaming a
logical name "costs an alias per retired wire id" — it should also say that *normalizing the column
afterwards* costs the ability to roll back.

---

## Alternatives considered

**Do nothing (status quo).** The aliases carry it; the column keeps two spellings forever; new
backups stay readable by old builds. Zero risk, permanent low-grade confusion — and the confusion is
bounded, because `EventTypes` is a single file that documents exactly which names are retired.

**Normalize at display instead of in storage.** Render `EventTypes.classFor(type)`'s simple name in
`/admin/eventlog` and the command timeline, leaving the column alone. Cheap, no writes, no
compatibility cost, and it addresses the pain where it actually lands (the admin UI, not raw SQL).
**Rejected:** it makes the one screen whose job is to show you what is in the database show you
something else instead. If the column and the screen disagree, the screen should not be the liar.

---

# Runbook: how to run it

Seven steps. Do them in order; **do not skip step 2.**

1. **Deploy the build that contains this change.** The code is inert until someone clicks the button:
   a deploy alone changes no data.
2. **Take a backup and keep the file.** `/admin/backup` (or `scripts/backup-db.sh`). Name it so you
   can find it — this is the rollback artifact, and it is the *only* fast one. Every row in it still
   carries the old names, so it restores into **either** build.
3. **Open `/admin/migrate-legacy-events` and read the preview.** It writes nothing. Check:
   - **Types to rename** — how many rows carry a retired wire id.
   - **Rows to write** — the real total (renames may overlap rewrites and stamps).
   - **errors** — if any row is listed as unmigratable, **stop**: fix the data first, because the run
     will refuse anyway and write nothing.
4. **Click "Run migration".** One transaction. All-or-nothing: any bad row aborts the whole pass with
   zero writes. The page then reports `N payloads rewritten, N stamps added, N types renamed`.
5. **Restart the app and confirm a clean replay.** Look for the *absence* of `Failed to Load and
   Process ALL Events from persistent store. Entering read-only mode.` in the log, and check that
   `/calendar` and `/itinerary` still show entries. An empty site plus that log line means the store
   holds a name this build cannot resolve — go to the rollback steps.
6. **Take a fresh backup.** It is your new floor, and the first backup file whose `type` column is
   clean.
7. **Re-open `/admin/migrate-legacy-events`.** It must now read "Nothing to migrate" — that is the
   idempotence check, done against real data.

## Rollback, if you need it

- **Preferred:** deploy the old build, then restore the step-2 backup into it (wipe first, per the
  standing import workflow). Old names go back in, the old build replays them.
- **If the step-2 backup is gone:** patch the old build with a reverse alias
  (`alias("ConferencePlanned", "ConferenceTentativelyPlanned")`) and deploy that. It works, but it is
  a build-and-deploy, not a fast path.
- **Symptom to recognize:** a fully-booted, read-only app with an empty calendar, itinerary and every
  list view. That is *not* data loss — the database is intact and the new build reads it correctly
  again.

## Recommendation on timing

The mechanism is small and rides an existing pass; the only real cost is the rollback window, and a
pre-migration backup covers it. The conservative order is: let the renamed build run in production
for about a week (a rename is the change most likely to need a rollback, and normalizing on top of it
spends that option), then run the runbook. Running it sooner is fine **if** step 2 is done — the
backup is what makes the timing a preference rather than a risk.

## Test plan (implemented)

- `LegacyEventMigrationTest.renamesARetiredWireIdToTheCurrentLogicalNameLeavingPayloadAndStampAlone`:
  a row whose *only* staleness is a retired wire id is selected (preview counts it in both
  `toRename` and `totalToMigrate`), renamed to the current logical name with payload, stamp and
  identity untouched — and a second run renames nothing.
- `LegacyEventMigrationTest.normalizesALegacyFqcnTypeInTheSameWriteAsThePayloadAndStamp`: an FQCN row
  is normalized in the same single write as its payload rewrite and stamp.
- `LegacyEventMigrationTest.oneUnbindableRowAbortsTheWholeMigrationLeavingEveryRowUntouched`: extended
  with a rename-stale row, which keeps its retired wire id when the pass aborts.
- `AdminControllerTest`: the preview renders "Types to rename" / "Rows to write" and the one-way-door
  warning; both warnings disappear when nothing is renamed; the applied run reports the renamed count.
- The `UPDATE` itself is covered end-to-end by `LegacyEventMigrationTest` against a real Postgres
  (Testcontainers), which is why no separate `PostgresPersisterTest` case was added.
- Mutation-verified: dropping `type = :type` from the `UPDATE`, dropping `typeChanged` from the skip
  condition, and reverting `totalToMigrate()` to `toRewrite + toStamp` each turned exactly the
  expected test red.

**Not covered by a test:** a normalize → back up → restore round-trip. Restore copies `type` verbatim
in both directions (`BackupService` maps the column straight through, unchanged by this work), and
`BackupRestoreRoundTripTest` already exercises that path; the runbook covers the rest operationally at
steps 6–7.
