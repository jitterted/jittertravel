# Normalizing the `event_log.type` column

> **Status: proposed, nothing built (2026-08-19).** Written to capture the design and — the reason
> this doc exists — the **rollback consequences**, which turn out to be the deciding factor. See
> `docs/Backlog.md` for the status of everything else, and `EventPayloadUpcasterDesign.md` for the
> read path this plugs into.

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

## What normalization would do

Rewrite `event_log.type` to the current logical name for every row carrying a retired wire id, as a
fourth thing the **existing** eager migration does. That pass already rewrites stored rows in place —
it upcasts the payload and re-stamps `schema_version` — so this is the same operation on one more
column, not a new mechanism.

Touch points, all small:

1. `PostgresPersister.MigratedEventRow` — add `String type`.
2. `PostgresPersister.migrateEventPayloads` — add `type = :type` to the `UPDATE`.
3. `LegacyEventMigration.plan()` — add a `typeChanged` condition
   (`!row.type().equals(EventTypes.logicalNameFor(EventTypes.classFor(row.type())))`) alongside the
   existing `payloadChanged` / `stampChanged`, so a row whose *only* staleness is its name is still
   selected.
4. `MigrationResult` / `MigrationReport` + `admin-migrate-legacy-events` — a third counter
   ("renamed") next to payload rewrites and stamps.

It stays all-or-nothing, idempotent, and resumable, exactly like the pass it joins: plan the whole
table, refuse on any error, write nothing unless every row is good.

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

## Recommendation

Do it — but **not yet**, and never without step 1 above. The mechanism is small and rides an existing
pass; the only real cost is the rollback window, and that cost is entirely manageable with a
pre-migration backup. My earlier "before slice 2" framing was too eager: slice 2 does not care which
spelling the column holds, so there is no reason to spend the rollback option while the rename is
still fresh.

**Trigger to revisit:** the renamed build has been in production for a week or more, *and* either the
two spellings have actually cost debugging time or a second logical rename is being contemplated
(two renames' worth of divergence is where this stops being cosmetic).

## Test plan when it ships

- `LegacyEventMigrationTest`: a row whose *only* staleness is a retired wire id is selected, rewritten
  to the current logical name, and its payload and stamp are left alone.
- Idempotence: a second pass over normalized rows selects nothing.
- All-or-nothing: an unresolvable row anywhere aborts the pass with zero writes (existing behaviour,
  extended to the new column).
- `PostgresPersisterTest`: the `UPDATE` actually writes `type`, matched by sequence.
- A round-trip: normalize → back up → restore → the restored rows carry the new name and replay.
- Mutation-verify each, per standing practice.
