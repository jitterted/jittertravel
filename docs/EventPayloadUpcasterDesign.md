# Event Payload Upcaster — the read-time version ladder

**Status:** `built 2026-08-18`. Reference doc for the general-purpose event-payload migration
mechanism. **Related:** `LegacyEventEagerMigrationPlan.md` (the per-event `schema_version` stamp and
the admin action that permanently rewrites stored rows), `UtcDatetimeStoragePlan.md` (the first
migration — datetimes → `ZonedTimestamp`), `ConferenceSubmissionTrackingPlan.md` (the second
migration — conference `format` — which shaped this framework out of the original single class),
`EventOrientedBackupRestorePlan.md` (verbatim backup, so a stored row is never healed by writing).

## What it is

`EventPayloadUpcaster` brings a stored event payload **up to its current schema shape before it
binds** to the record class. Every read path runs a payload through it: boot replay
(`PostgresPersister.toStoredEvent`), backup restore (`BackupService`), and the eager migration
(`LegacyEventMigration`). It is the single place a *breaking* change to an event's JSON is absorbed,
so old rows keep replaying after the record changes shape.

It is **general-purpose**: not "the datetime migrator". Two kinds of change have ridden it so far — a
field's *type* changing (bare wall-clock scalar → `{utc, zone}` object) and a *new field with a
default* (`format`) — and a field-*set* change (gathering's `date`+`startTime`+`endTime` →
`startsAt`+`endsAt`). Any future breaking payload change is one more rung on the same ladder.

## The model: a per-type version ladder

Event schema versions are **per type**, tracked in `EventTypes` (the third `register(...)` argument;
an absent argument means version 1). A type's version counts *its own* schema changes:

| Type | Current version | Rungs it climbs |
|---|---|---|
| `HotelBooked`, `HotelChanged` | 2 | v1→v2 datetime |
| `TrainBooked`, `TrainChanged` | 2 | v1→v2 datetime |
| `FlightBooked`, `FlightChanged` | 2 | v1→v2 datetime |
| `GatheringPlanned`, `GatheringChanged` | 2 | v1→v2 datetime (field-set change) |
| `ConferenceTentativelyPlanned` | 3 | v1→v2 datetime, **then** v2→v3 `format` |
| everything else | 1 | — (no rungs) |

A stored row also carries its `schema_version` (the `event_log.schema_version` column; **null** on a
pre-stamp legacy row). The upcaster **climbs from that stored version up to the type's current
version**, applying exactly one rung per version step:

```
version = storedVersion ?? 1            // null (unstamped legacy row) ⇒ oldest
while (version < currentSchemaVersion(type)) {
    theOneRungFor(type, version).upcast(payload)   // mutates in place
    version++
}
```

`ConferenceTentativelyPlanned` is the type that makes the ladder visible: a v1 row climbs **two**
rungs (datetime, then format); a row already stamped v2 climbs **only** the format rung; a v3 row
does no work.

### Why version-driven (not shape-sniffing)

The composite decides *which rung runs* from the stored version, not by inspecting the payload's
shape. That makes each rung self-describing (it advertises the `(type, version)` it consumes) and
makes retirement fail loud (below). But the stored version is trusted **with a safety net**: each
timezone rung also keeps a one-line idempotency guard (`isLegacyScalar` — "is this field still a bare
scalar?"). So a never-stamped legacy row (version ⇒ 1) whose payload is *already* in a later shape —
possible in an old, unstamped backup — climbs from version 1 through the intervening rungs as
**no-ops** rather than being double-applied. Belt (version) **and** suspenders (shape). This is why
the ladder can start from 1 on any unstamped row and always land correctly.

The stored stamp is never *behind* the payload shape through any legitimate path: the write path
(`fromStoredEvent`) stamps `currentSchemaVersion` atomically with a current-shape payload, and a
backup carries both verbatim. So a stamped row is always exactly at the shape its stamp names, and
the climb from that stamp is correct.

## The classes

- **`EventUpcaster`** (interface) — one rung. `boolean canHandle(String logicalType, int version)`
  advertises the single `(type, version)` step it consumes; `void upcast(ObjectNode payload, String
  logicalType)` advances the payload one version, in place. Deliberately narrow: it knows one type
  (or a family that shares a shape) and one step, nothing about its neighbours.
- **`EventPayloadUpcaster`** (composite) — owns no migration logic; holds the registered rungs and
  drives the climb. `standard(locationZoneResolver, airportZoneResolver, jsonMapper)` assembles the
  production ladder (the one place the rung list lives — config and tests both call it). Normalizes
  the wire id (logical name **or** legacy FQCN) to the logical name before looking up rungs. Enforces
  **at most one rung per `(type, version)`** and **fails loud** when a needed rung is missing.
- **`WallClockZoning`** — the injected collaborator shared by the timezone rungs: `toZoned(...)`
  (renders a `ZonedTimestamp` to a tree via the `JsonMapper`), `isLegacyScalar`, `nestedText`. It
  carries no event-type knowledge, so it is a *collaborator*, not a base class — each rung owns which
  of *its* fields resolve from which location.
- **The rungs** — `HotelTimeZoneUpcaster`, `TrainTimeZoneUpcaster`, `FlightTimeZoneUpcaster`,
  `GatheringTimeZoneUpcaster`, `ConferenceTimeZoneUpcaster` (all v1→v2, datetime), and
  `ConferenceFormatUpcaster` (v2→v3, `format`). Each is one small class. The flight rung is the one
  wired to `AirportZoneResolver` rather than `LocationZoneResolver` — the split that had made a single
  all-events class incohesive. The format rung takes *no* collaborators at all — exactly why it is its
  own rung and not a branch inside a datetime class.

### Why it was split out of one class

The original `EventPayloadUpcaster` was one class with a five-branch `switch` and **both** zone
resolvers as fields. That coupled unrelated concerns: the flight branch's `AirportZoneResolver` sat in
a class that also did hotels, and the `format` change (which needs no resolver at all) would have been
a sixth branch wedged in beside datetime logic. Splitting into per-`(type × step)` rungs makes each
one cohesive and independently testable, and turns "retire the datetime migration" into "delete these
classes" rather than "carefully unpick branches".

## How to add a new migration (a new rung)

When an event's stored JSON changes shape in a breaking way:

1. **Bump the type's `currentSchemaVersion`** in `EventTypes` (the third `register(...)` argument).
   Say it goes from *N* to *N+1*.
2. **Write an `EventUpcaster`** whose `canHandle` returns true for `(thatLogicalType, N)` and whose
   `upcast` advances the *N*→*N+1* shape, mutating the `ObjectNode` in place. Keep it **idempotent**
   (an absence/shape check) so a payload already in the new shape passes through untouched. Reuse
   `WallClockZoning` if it's a datetime change; take no collaborators if it isn't.
3. **Register it** in `EventPayloadUpcaster.standard(...)`.
4. **Make the record bind loud on the missing field** (a fail-loud compact constructor), so a payload
   that reaches binding without being upcast is caught rather than silently defaulted — production
   always upcasts before binding, so the non-null field is safe. (See `ConferenceTentativelyPlanned`.)
5. **Add a golden sample** for the new shape in `GoldenEventDeserializationTest`, and (if the change
   is not backward-shape-compatible) a legacy sample proving the old shape still upcasts — per the
   standing "golden sample per new event/shape" rule.
6. **Test the rung in isolation** (a `*UpcasterTest`) — its `canHandle` gating and its `upcast`
   mechanics, including the idempotent passthrough — and let the composite test
   (`EventPayloadUpcasterTest`) prove the ladder now climbs the extra step. Mutation-verify every new
   test.

That's the whole framework the earlier plan deferred: adding a migration is one `register` bump + one
small class + its test.

## How to retire a rung

A rung exists only to read rows older than it. Once **every stored row and every restorable backup**
is at or above version *N+1* (i.e. the eager migration has permanently rewritten the store *and* no
pre-migration backup remains in rotation — see `LegacyEventEagerMigrationPlan.md`), the *N*→*N+1* rung
is dead code. Retire it by **deleting the class and dropping it from `standard(...)`**.

The safety net is structural: if a row is ever read that still sits *below* a deleted rung, the climb
cannot reach the current version and the composite **fails loud** ("No upcaster advances … from schema
version …") rather than binding a stale shape. Gate each retirement with the boot-replay preflight
(`BootReplayPreflightTest`), which certifies nothing in the current store needs it.

## Testing convention

- **Per-rung tests** (`HotelTimeZoneUpcasterTest`, …, `ConferenceFormatUpcasterTest`): construct the
  one rung with real collaborators, feed an `ObjectNode` directly, assert the mutation and the
  `canHandle` gating (right type + right version only). Migration mechanics live here.
- **Composite test** (`EventPayloadUpcasterTest`): asserts *composition only* — wire-id normalization
  (legacy FQCN reaches the same rungs as the logical name), the version-driven climb (starts from the
  stored version, skips already-passed rungs, climbs multiple steps from version 1), and the failure
  modes (unknown type, missing rung, duplicate rung).
- **Golden contract** (`GoldenEventDeserializationTest`): binds a real sample per event, including a
  legacy sample proving the old shape still upcasts.

## Invariants (don't break these)

- **Rungs are idempotent.** A payload already at the new shape must pass through a rung untouched —
  the safety net that lets any unstamped row climb from version 1.
- **Exactly one rung per `(type, version)`.** The composite throws on a duplicate; two rungs claiming
  the same step is a wiring bug.
- **Every version bump has a rung.** If `currentSchemaVersion(type)` is *V*, a rung must exist for
  every step from 1 to *V* (until those rungs are legitimately retired with all rows above them).
  A gap makes the climb fail loud.
- **`standard(...)` is the one assembly point.** Add or remove a rung there, not in a second place.
