# Migration Lessons Learned

**Status:** `retrospective 2026-08-19`. Written after the event-shape migrations of June–August 2026
(UTC/`ZonedTimestamp`, logical type names, gathering field-set collapse, per-event `schema_version`,
event-oriented backup/restore, the eager migration, `ConferenceFormat`, the `ConferencePlanned`
rename, `event_log.type` normalization). It answers one question: *what should have existed on day
one so all of that would have been cheaper and safer?*

Reference docs for the mechanisms themselves: `EventPayloadUpcasterDesign.md`,
`LegacyEventEagerMigrationPlan.md`, `EventTypeColumnNormalizationPlan.md`,
`EventOrientedBackupRestorePlan.md`, `UtcDatetimeStoragePlan.md`, `EventSourcingRulesHeuristics.md`
(R6/R7).

---

## What it actually cost

| Change | What it cost, because of a day-one gap |
|---|---|
| `LocalDateTime` → `ZonedTimestamp` (5 event families, 9 types) | Read-time zone resolution on **every boot**, forever; five upcaster rungs; a curated resolver that can never shrink. |
| Class FQCN in `event_log.type` | **Production outage 2026-08-16.** Legacy FQCN rows fell through the upcaster's `default` and boot replay died → app up, read-only, every view empty. |
| Missing schema version | The ladder has to start from "1" on unstamped rows and needs shape-sniffing guards as suspenders; retirement of a rung stayed unsafe until a preflight was built. |
| Gathering `date`+`startTime`+`endTime` → `startsAt`+`endsAt` | Field-**set** change: the rung merges *and deletes* keys, and golden samples of the old shape had to be invented — there were none. |
| Command-replay backup | Restored events got new ids/sequences/timestamps; every command faked a decision context on import; backup format coupled to live domain logic. Replaced wholesale (clean break, old files unreadable). |
| `ConferenceTentativelyPlanned` → `ConferencePlanned` | Three wire ids for one type; normalizing the column is a **one-way door** for rollback. |

Everything above was recoverable. The common shape of the pain: **something derived at read time, or
something identified by a name that was never meant to be durable.**

---

## Build these on day one (cheap forever, expensive to retrofit)

These are *format* decisions, not abstractions — they don't violate "no abstraction before the second
user", because they are irreversible-ish choices about bytes on disk, not speculative code.

1. **A stable logical type name from the very first append.** Never write a class FQCN into
   `type`. One registry (`EventTypes`) with an append-only alias log. This is the single highest-value
   day-one item: it is what the 2026-08-16 outage was.
2. **A per-event `schema_version` stamp on the row from the first append.** A column, not a payload
   key (payloads stay pure domain shape, golden samples untouched). Even when every type is at
   version 1 and nothing reads it. Retrofitting it means a permanent `null` = "unknown" tier.
3. **Normalize the wire id at exactly one point, before any dispatch.** Every read path
   (boot replay, restore, migration, preflight) resolves the stored id → logical name in the *same*
   call, so a new caller can't reintroduce the outage.
4. **Verbatim event backup/restore, never command replay.** Backup writes `event_log` rows as-is and
   restores them with their original ids, sequences and timestamps. Commands ride along as opaque
   history. Stamp a format version in the file and always keep reading N-1.
5. **A boot-replay preflight against real production data, runnable before deploy.**
   `BootReplayPreflightTest` was written *after* the outage it would have prevented — from the
   throwaway script used to diagnose it. Formalize the rehearsal the first time you touch a payload.
6. **Fail loud, everywhere, with no `default:` branch.** No pass-through case, no silent
   default value, no "it's null today so copy it through". The FQCN outage was a `default:` that
   returned the payload unchanged. (Same deny-by-default posture the redactor already has.)
7. **Golden samples are frozen, and every shape change adds one — of the OLD shape.** Hotels and
   trains *edited* their samples to the new shape, so the repo carried **zero** legacy-shape coverage
   until the gathering slice noticed. A golden file is a fossil; never edit one, only add.

---

## How to run a migration (the shape that worked, adopt it first not third)

- **Validate-then-apply, in two passes.** Pass one upcasts and bind-checks every row and writes
  **nothing**, reporting *all* failures together; pass two writes in one transaction. Restore, the
  eager migration and the type normalization all converged on this independently — it should have
  been the template from the first one.
- **Idempotent and resumable.** Re-running is a no-op; a partial run resumes rather than colliding.
  "Did the transform change the bytes?" is a better discriminator than a legacy/new flag.
- **All-or-nothing beats best-effort.** One bad row aborts with zero writes. A half-migrated store
  costs more than a failed migration.
- **Preview before writing, and report the number of rows that will actually be written** — not a sum
  of categories. Overlapping counters made `/admin/migrate-legacy-events` read "Nothing to migrate"
  while stale rows sat there.
- **Operator-triggered, never on boot.** Silent mutation at startup is unrecoverable surprise.
- **Read-only mode must block it**, checked before any write.
- **Take a backup immediately before, and a fresh one immediately after.** The before-file is the
  rollback artifact; the after-file is the new floor and shortens the window in which the old rungs
  matter.
- **Lazy read-time upcast to survive; eager one-shot rewrite to be done with it.** Lazy-only means
  the old shape lives forever and every boot pays for it. Plan the eager pass at the same time as the
  rung, not two months later.

---

## Backward / forward compatibility — what is actually achievable

**Backward (new build reads old data): fully achievable, and we have it.** Version ladder + aliases +
idempotent rungs. Rule: a rung is retired only when no stored row *and* no restorable backup sits
below it, gated by the preflight; a row read below a deleted rung must fail loud, not bind stale.

**Forward (old build reads new data): only partly achievable, and we chose not to have most of it.**

| New-data change | Old build? | Why |
|---|---|---|
| Added payload field | **OK** — production Jackson ignores unknown properties (the strict setting is test-only) | tolerant reader |
| Rewritten payload shape | fails | the old build has no rung and no reason to look for one |
| **New logical type name** (rename + normalize) | **fails at boot, totally** | an alias teaches today's build yesterday's names, never the reverse |

Consequences worth stating once:

- **Renaming an event class is free. Renaming its stored logical name costs an alias per retired wire
  id. Rewriting the column afterwards costs your rollback.** Keep the pre-rewrite backup — it restores
  into *either* build, and it is the whole safety story.
- **Additive-with-a-default is the cheapest possible migration** (`ConferenceFormat` v2→v3: one tiny
  rung, no collaborators). Retyping a field is medium. Changing the field *set* is the most expensive
  — prefer to add a new field and leave the old one, if the meaning permits.
- **Keeping the *input* (command/request) wire shape stable and additive** — scalar fields kept, an
  optional `zone` added — is what let every pre-migration backup keep importing with no command-side
  upcaster at all. Don't reshape inputs to match reshaped events.
- If forward compatibility ever needs to be *real* (blue/green, multiple instances), the price is a
  two-step protocol: deploy a build that **reads** the new name/shape, wait, then deploy the one that
  **writes** it. Nothing else buys rollback across a rename.

---

## Traps found the hard way

- **An upcaster must be a pure function of (payload, constants).** Ours resolved zones from a curated
  table at read time, so: one unknown city (Casablanca) killed boot; one data-entry error
  (Antwerp filed under country "Brussels") became a permanent resolver hack that *cannot* be removed,
  because fixing the hotel in the UI appends a new event and never rewrites the bad original. If a
  transform needs a lookup, do the lookup once, eagerly, and bake the result into the row.
- **A backup taken before the values are baked in does not pin them.** The same file can restore to
  different instants after a resolver change. That was the deliberate trade for wire stability, and it
  cuts both ways — it also rescued an unimportable file once.
- **The failure posture matters as much as the failure.** A failed boot replay leaves the app *up*,
  read-only, with empty read models — indistinguishable from data loss to anyone not reading the log.
  The red banner (`EventStore.isReadOnly()`) exists because of this; decide the posture before the
  first migration, not after the first incident.
- **Audits sweep only what is already imported.** `/admin/zone-audit` cannot pre-check a backup file,
  and it silently missed `GatheringChanged` (the one `*Changed` event it didn't sweep) — an edited
  venue would have passed the audit and killed replay. An audit's *coverage* needs its own test.
- **Persisted read models would change all of this.** Today every projector is rebuilt in memory at
  boot, which is the only reason rung changes are safe. The day one is persisted, a new rung, a
  restore, or a projector change each require truncate-and-rebuild. Decide the invalidation protocol
  *before* persisting the first one.
- **A rename ripples further than the class.** `EventUpcaster.canHandle` keys on the logical name, so
  a logical rename must move every rung in the same commit or those rows silently stop climbing.

---

## Day-one checklist for the next event-sourced system

- [ ] `type` = stable logical name via a registry; FQCN never written.
- [ ] `schema_version` column stamped on every append, from append #1.
- [ ] Wire id normalized in one place, before any dispatch.
- [ ] Upcaster registry: one rung per `(type, version)`, one assembly point, missing rung fails loud,
      no `default:` pass-through.
- [ ] Rungs are pure: payload in, payload out, no external lookups.
- [ ] Verbatim event backup/restore with a versioned file format that reads N-1.
- [ ] Golden sample per event, frozen; a new one per shape change, of the shape being retired.
- [ ] Preflight that replays a production backup in CI/on demand.
- [ ] Read-only failure posture + a visible banner.
- [ ] Migration template: preview → validate-all → single transaction → idempotent → operator-triggered
      → backup before and after.

---

## Follow-ups this retrospective suggested

1. `done 2026-08-19` — **lessons promoted into `EventSourcingRulesHeuristics.md`** as *Corollaries of
   R7*: **R7a** (a rung is a pure function of payload + constants), **R7b** (a golden sample is a
   fossil: add, never edit), **R7c** (renaming a logical name spends the rollback).
2. `done 2026-08-19` — **two stale claims corrected in that doc's R7 enforcement section:** golden
   samples are inline text blocks in `GoldenEventDeserializationTest`, not files under
   `src/test/resources/event-samples/` (that directory does not exist); and the
   `@EventSchema(version = N)` + upcaster chain is no longer a *deferred* option — both halves
   (`EventTypes` logical names, `event_log.schema_version` + `EventPayloadUpcaster`) are built.
3. `open` — **Retirement schedule.** The `*TimeZoneUpcaster` rungs, the FQCN wire-id mapping and the
   Antwerp/Morocco resolver entries all wait on "no pre-migration backup in rotation" — a date nobody
   has drawn. Drawing one turns three open-ended obligations into scheduled deletions. See below.

### 3, stated plainly

**The need.** Three pieces of code exist only to read old data:

- the five `*TimeZoneUpcaster` rungs (bare-scalar datetime → `ZonedTimestamp`),
- the FQCN wire-id mapping and the retired-name aliases in `EventTypes`,
- the Antwerp and Morocco entries in `LocationZoneResolver`.

They can be deleted only when no data that needs them can be read again. The stored database stops
needing them as soon as the eager migration runs. But an old **backup file** still needs them,
because restore copies rows verbatim. No person has said when the old backup files stop being usable.
Thus the three pieces stay in the code for an unknown time.

**The solution.** Ted must select one date. After that date, no backup file that is older will be
restored. Then do these steps in this sequence:

1. Run `/admin/migrate-legacy-events` on production. Keep the backup made immediately before it.
2. Make a new backup. This file is the new floor.
3. Delete all backup files that are older than the floor, or move them to cold storage and mark them
   as not restorable.
4. Run the boot-replay preflight (`./mvnw test -Preplay-preflight -Dpreflight.dump=…`) against the
   floor file. The preflight must pass with the rungs still present.
5. Delete the three pieces of code, one commit for each. Run the preflight again after each commit.
   If a rung is still necessary, the climb fails loud and names the row.

**The cost of not deciding.** The code is small and safe. But it keeps `LocationZoneResolver` in the
replay path, it keeps two hacks that look like real data rules, and it makes every new rung more
difficult to reason about. The decision is one date. The work after it is mechanical.

**A backup that is too old cannot be restored by accident.** After a rung is deleted, pass one of the
restore fails loud on every event that is below the rung. The restore writes nothing. This protection
is already present. Only the error message is bad. See `RestoreCompatibilityFloorPlan.md` for the
proposed improvement to the message, which must be built with the first retirement.
