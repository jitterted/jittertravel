# Restore Compatibility Floor — refusing a backup that predates a retired rung

**Status:** `design 2026-08-19, nothing built`. The trigger to build it is the **first rung
retirement**; before that every floor is 1 and the check is dead code. **Related:**
`EventPayloadUpcasterDesign.md` ("How to retire a rung"), `archived/LegacyEventEagerMigrationPlan.md`
(retirement is gated on old backups leaving rotation), `MigrationLessonsLearned.md` (follow-up 3, the
undrawn retirement date), `archived/EventOrientedBackupRestorePlan.md` (restore is validate-then-apply).

## The question

*After the `*TimeZoneUpcaster` rungs (and the retired `EventTypes` aliases) are deleted, can restore
stop an incompatible backup from being restored?*

**Yes — and the hard half already works today.** What is missing is not the refusal; it is the
*diagnosis*.

---

## What already happens (verified in the code, nothing to build)

`BackupService.validate` runs `validateEventPayloadBinds` for **every** event in the file:

```java
Class<? extends Event> eventClass = EventTypes.classFor(event.type());
JsonNode upcasted = upcaster.upcast(event.type(), event.payload().deepCopy(), event.schemaVersion());
jsonMapper.treeToValue(upcasted, eventClass);
```

After a retirement, one of the first two lines throws:

| What was retired | Where it throws | Message |
|---|---|---|
| An `EventUpcaster` rung | `EventPayloadUpcaster.rungFor` | `No upcaster advances HotelBooked from schema version 1 — was a rung retired before its rows were migrated?` |
| An `EventTypes` alias | `EventTypes.classFor` | `Unknown event type: dev.ted.jittertravel.domain.HotelBooked` |

`validateEventPayloadBinds` catches it and appends
`Event 17 (HotelBooked) payload cannot be restored: …`. Then `restoreJson` returns
`RestoreResult.failed(errors)` **before** `apply(...)` is ever called.

So the guarantees are already the right ones:

- **Zero writes.** The refusal happens in pass one, which writes nothing by construction.
- **All-or-nothing.** One below-floor event rejects the whole file; there is no partial restore.
- **Every problem reported at once**, not just the first.
- **A dry run exists.** `/admin/restore/validate` → `validateJson` runs the identical pass and writes
  nothing even for a good file, so a file can be tested before it is trusted.
- **It cannot be bypassed.** The climb is the same code path boot replay uses; there is no
  "restore raw" door.

This is deliberate design, not luck — it is the safety net `EventPayloadUpcasterDesign.md` promises
under "How to retire a rung". **If nothing is built, the system is still safe.**

---

## What is actually weak

Not the refusal — the story it tells an operator holding the wrong file at the wrong moment.

1. **The verdict is per event and cryptic.** A 900-event pre-migration backup produces ~200 lines of
   `Event N (HotelBooked) payload cannot be restored: No upcaster advances HotelBooked from schema
   version 1 …`. Nothing says *"this file is simply too old"*. A wrong-file mistake reads like data
   corruption, which invites exactly the wrong response.
2. **Nothing states the floor positively.** After retirement, the knowledge "pre-2026-XX backups are
   not restorable" lives only in the *absence* of a deleted class. There is no line in the app, the
   admin page, or the file format that says so.
3. **Detection is late and incidental.** The only up-front gate is the *format* version (`{2, 3}`),
   which describes the envelope, not the age of the payloads. Everything else is discovered one event
   at a time after the whole file has been parsed and referentially checked.
4. **Partly-legacy and wholly-legacy files are indistinguishable in the report.** "3 of 900 events are
   below the floor" is a completely different situation from "all 900 are" — the first is recoverable
   by migrating the source database and re-taking the backup, the second is not — and the current
   output does not distinguish them.
5. **The dry run is opt-in.** Restore does not require a prior validate, so the first time an operator
   learns the file is too old may be the moment they meant to restore it.

---

## Proposal: an explicit compatibility floor

Add a **floor check in pass one, before per-event binding**, and aggregate its findings into one
verdict.

### Mechanism

- **`EventTypes` gains a per-type `minimumRestorableSchemaVersion`** (a fourth `register(...)`
  argument, default 1). It is the exact mirror of `currentSchemaVersion`: *current* says "climb up to
  here", *minimum* says "we can no longer climb from below here". **It is bumped in the same commit
  that deletes a rung** — that pairing is the whole discipline, and is cheap to enforce with a test
  (below).
- **Pass one, per event:** `version = schemaVersion ?? 1`; if
  `version < minimumRestorableSchemaVersion(type)`, count it and record `(sequence, type, version,
  minimum)` — do **not** attempt the climb, and do not emit a per-event error line.
- **One aggregated verdict** replacing that noise:

  > This backup predates the restore floor. 3 of 900 events are below the minimum schema version for
  > their type (e.g. sequence 17, `HotelBooked` v1 < v2). Support for reading those payloads was
  > removed when the datetime upcaster rungs were retired. Remedy: restore this file into a build
  > from before that retirement, or migrate the source database (`/admin/migrate-legacy-events`) and
  > take a fresh backup.

  Plus the first few offending rows as examples. The existing per-event bind check stays as the
  backstop for every *other* failure (corrupt payload, removed field, genuinely unknown type).
- **Retired aliases get the same treatment.** `EventTypes.classFor` failing on a *known-retired* wire
  id is the same class of event with a different message; aggregate it into the same verdict and name
  the retired id.
- **Say the floor even when the file is fine.** The restore page states the current floor
  ("this build restores events at schema version ≥ 2 for `HotelBooked`…" or, more usefully, the
  retirement date), so the constraint is visible before the mistake rather than after.

### Alternatives considered

- **Bump the backup format v3 → v4 and refuse v3.** **Rejected** — the *format* did not change. A v3
  file taken after the migration is perfectly restorable; refusing it would orphan good backups. The
  envelope version and the payload age are different facts.
- **One global minimum schema version.** **Rejected** — versions are per type and explicitly not
  comparable across types (`EventTypes` javadoc). `HotelBooked` v2 and
  `ConferenceAttendanceDeclined` v1 are both current.
- **A date-based floor** ("refuse files created before 2026-09-01"). **Rejected** — `metadata.createdAt`
  is informational, and a file can be re-taken at any time from an un-migrated source. The real
  predicate is the per-event stamp, which is exactly the thing that breaks.
- **Auto-upgrade the old file on the way in.** **Rejected outright** — that is re-implementing the rung
  that was just deleted, which defeats the purpose of retiring it.
- **Do nothing extra.** Honest and defensible: the guarantee is already correct and the only cost is a
  bad error message on a rare day. Choose this if a pre-floor file will never realistically be in
  hand — the work below buys diagnosis, not safety.

### Out of scope

- No change to `SecurityConfig` or the restore route — this is data validation, not authorization.
- No warning on a **v2 (unstamped) file whose payloads are already current-shape**; that restores
  correctly and must keep doing so. The climb decides, not the stamp's absence.
- No new "force restore" override. An operator who genuinely needs an old file uses an old build.

---

## Test plan (when built)

- `RestoreSafetyTest`: a file with an event below its type's floor → **zero writes**, one aggregated
  verdict naming type, sequence and both versions; a file with a mix of below-floor and genuinely
  corrupt events reports both categories.
- `BackupServiceTest` / `validateJson`: the dry run produces the identical verdict and writes nothing.
- `EventTypesTest`: `minimumRestorableSchemaVersion ≤ currentSchemaVersion` for every registered type,
  and the default is 1.
- **Retirement simulation, buildable today:** `EventPayloadUpcaster`'s constructor takes the rung list,
  so a test can assemble a ladder with a rung *omitted* and assert the fail-loud message — no actual
  retirement required. Worth adding now regardless of this plan, as it pins the safety net that makes
  retirement safe.
- Mutation-verify each, per standing practice.

---

## When to build it

**At the first rung retirement, in the same commit.** Until then every floor is 1, the check can never
fire, and it would be inert code guarding an event that cannot happen — see the standing rule against
abstraction before a second user. The prerequisite is still the undrawn date in
`MigrationLessonsLearned.md` follow-up 3: pick the day after which no older backup will be restored,
migrate, re-backup, retire, and bump the floor in that same change.
