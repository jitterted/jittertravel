# Decision Context Queries — replacing `eventsForDecision()` with a tagged query

**Status:** `unblocked — ready to build (was: blocked on the export/import rethink)`
**Opened:** 2026-08-07 · **Unblocked:** 2026-08-10 · **Owns:** `DecisionsToReview.md` D1 and D2,
and the "ChangeHotel/ChangeFlight still decide from a projector" follow-up in `Backlog.md`

This is a discussion record, captured mid-conversation so it can be picked up later. Nothing here
is built, but the blocker is gone. The design is close to settled; what stopped it was **item 1
under "Concerns"** — every command gaining a real context query forced the export/import question
that `Backlog.md` flagged as needing "a wider decision before more commands need folded context."
**That rethink is now resolved by `archived/EventOrientedBackupRestorePlan.md`**: backup/restore moves to
event-verbatim, so restore no longer re-executes commands and no command has a decision context to
fake on import. This slice can proceed. See Concern §1 for the detail.

---

## Why this exists

Every command that decides against prior state needs a **decision context**. Today those contexts
are built two different ways, and neither is right:

- `ChangeHotel`, `ChangeFlight`, `ChangeGathering` read a **details projector** for an existence
  check. This violates R1 in `EventSourcingRulesHeuristics.md` ("never use a projection to make an
  automated decision"). It happens to work only because every subscriber is synchronous today —
  an implementation detail, not a guarantee.
- `CancelHotel` folds from the raw stream via `CommandExecutor.eventsForDecision()`, which streams
  the **entire** event log and filters in the service.

The intended shape: each command's context is loaded by a **query against the event store**,
filtered by one or more ids ("tags") and event types. Later that same query becomes the
**append condition** for conditional appends (`CommandConsistencyEventStore.md` §3), which is what
makes "the decision state hasn't changed since I read it" enforceable.

### Reference implementation

`~/IdeaProjects/github/jitterted/tdd-game-event-sourced`:

- `application/port/QueryPredicate.java` — `record(Set<Class<? extends Event>> eventTypes, Set<Tag> tags)`
  plus convenience constructors
- `application/port/InMemoryEventStore.java` — `query(QueryPredicate)`: filter by type, then by
  `tags().containsAll(...)`
- `jeslib/Tag.java` — `interface Tag { String asString(); }`

### Prior art in this repo — read this first

`TaggedEventStoreQueryingDesign.md` (**repo root**) already designed the query half, including a
3–4 day cost estimate. Three of its conclusions are stronger than what this doc first proposed:

1. **"Every id in an event is a tag."** Every field whose type is an identity value object
   (`HotelBookingId`, `FlightId`, …) becomes a tag automatically. This removes the per-event
   "should I tag this?" judgment call *and* eliminates the tag-set-evolution problem — old events
   never need backfill for a newly-tag-worthy field, because every id field was a tag from day one.
   **This supersedes the allowlist idea in Concern §4 below.**
2. **Multi-valued tags:** `Map<String, List<String>>`, not `Map<String, String>`. "Cheap to add up
   front and annoying to retrofit later." Phase 3 of `HotelCancelReplacePlan.md`
   (`replacesHotelBookingId`) is a concrete event that will carry two ids of the same kind.
3. **Backfill by running the live code path** — deserialize each stored payload and ask
   `Event.tags()`, so write path and backfill cannot disagree.

One of its proposals is now **obsolete**: `@EventName`, to decouple the `event_log.type`
discriminator from class identity. `infrastructure/EventTypes.java` already does exactly that. Do
not introduce a second discriminator mechanism.

Its stated deferral condition — "until a second concrete caller beyond `ChangeFlightCommand`
demands it" — is the same test `DecisionsToReview.md` D2 poses, and open decision 2 below answers
it with four callers.

---

## What is true in the code today (verified 2026-08-07)

| Fact | Where |
|---|---|
| `Event` is a bare marker interface — no `tags()` | `domain/Event.java` |
| `StoredEvent` has no tags component | `infrastructure/StoredEvent.java` |
| `event_log` has no tags column | `PostgresPersister` |
| `EventStore` exposes only `findAll()` — no filtered query | `infrastructure/EventStore.java` |
| `EventTypes` already maps stable **logical names** ↔ event classes, so classes can move/rename without breaking replay | `infrastructure/EventTypes.java` |
| The arch test forbids an `EventStore` **constructor parameter type** in `application` — not "any read" | `ApplicationServicesUseCommandExecutorTest.java` |
| Export/import round-trips `ImportableCommand` records, **not events** — *being retired by `archived/EventOrientedBackupRestorePlan.md` (event-verbatim restore)* | `web/ImportableCommand.java` |
| `ImportableCommand.events()` gets no stream and no read model, so `CancelHotelRequest` hardcodes `new CancelHotelContext(true, null, IMPORT_BYPASS_INSTANT)` — *the fake disappears with command replay* | `web/CancelHotelRequest.java:36-41` |

**Where the prior art lives:** `TaggedEventStoreQueryingDesign.md` is at the **repo root**, not in
`docs/`. It is the earlier, query-side treatment of this exact problem and it must be read before
implementing — see "Prior art" below. `CommandConsistencyEventStore.md` covers the append-condition
half (§3 the DCB condition, §"Tags as a JSONB column", §5 sketch code).

---

## The proposal

### 1. A read port, separate from `CommandExecutor`

`eventsForDecision()` goes away. The objection in D1 was that it hands out the whole stream with
only a Javadoc stopping you from building a read model from it; a query that demands types and tags
up front makes that misuse awkward by construction — enforcement the Javadoc could not provide.

**Recommendation: a separate port implemented by `EventStore`, not a method on `CommandExecutor`.**

```java
// application package
public interface DecisionStream {
    QueryResult query(EventQuery query);
}

public record QueryResult(List<StoredEvent> events, long asOfSequence) { }
```

Reasons:

- `CommandConsistencyEventStore.md` §5 has `DecisionContext` carry the `AppendCondition`, which
  means the query must be a **value the service holds and passes into the context**, not a call
  hidden inside the executor. It has to be first-class regardless.
- Reading and writing stay distinct collaborators — this is D1's "alternative not taken," and the
  query API is what makes it worth the extra interface and bean.
- The arch test checks for the `EventStore` **type** and is unaffected. The rule it protects (the
  command row must exist in `command_log` before events are appended — an FK ordering constraint on
  the *write* path) is not weakened by a read port.

`asOfSequence` is the future `afterPosition`: the max sequence the query observed. Captured by the
same read that built the context, it costs nothing now and is painful to retrofit later.

### 2. Query API in `Class` terms; logical names only at the storage boundary

`EventTypes` exists so an event class can move or be renamed without breaking replay. So:

- Service-facing `EventQuery` holds `Set<Class<? extends Event>>` — compile-time safe, follows IDE
  renames.
- A future SQL implementation calls `EventTypes.logicalNameFor()` to build `type = ANY(:queryTypes)`.

Do **not** put logical-name strings in the service-facing API.

### 3. Tags as a multi-valued map on the event

Prefer a map form over tdd-game's `Set<Tag>` / `asString()` — they are isomorphic
(`"hotelBooking:<uuid>"` vs. a key/value pair), but the map maps directly onto the Postgres
endgame, `tags @> :queryTags`.

**Multi-valued**, per `TaggedEventStoreQueryingDesign.md`, not the single-valued
`Map<String, String>` in `CommandConsistencyEventStore.md`. The retrofit cost is the deciding
argument, and `replacesHotelBookingId` (Phase 3 of `HotelCancelReplacePlan.md`) is a real event
that carries two `HotelBookingId`s.

```java
public interface Event {
    default Map<String, List<String>> tags() { return Map.of(); }
}

public record HotelChanged(HotelBookingId hotelBookingId, /* ... */) implements Event {
    @Override
    public Map<String, List<String>> tags() {
        return Map.of("hotelBooking", List.of(hotelBookingId.toString()));
    }
}
```

Which fields become tags is not a judgment call: **every field whose type is an identity value
object is a tag** (prior art, rule 1).

**No backup-format risk:** export/import round-trips commands, not events, so adding `Event.tags()`
and (later) an `event_log.tags` column does not touch backup JSON compatibility.

### 4. In-memory filtering now; no JSONB column in this slice

The store is a single in-memory `List<StoredEvent>` replayed at boot. A query can derive tags from
`payload.tags()` at filter time — no migration, no `ALTER TABLE`, no GIN index. The JSONB column
from `CommandConsistencyEventStore.md` §"Tags as a JSONB column" buys nothing until the conditional
append actually has to run in the database.

### 5. Persist the *position* on the command row, never the context values

Asked directly (Ted, 2026-09-02): the command is already written to `command_log` as a write-ahead
record of intent — should the `DecisionContext` be written beside it, and is that what a future
**undo** needs?

**No to both. If anything goes on the command row it is `asOfSequence` — one `BIGINT` — and it is
for concurrency and forensics, not for undo.**

**Undo does not want the old context.** `HotelBookingCancellationUndone` is a *forward* command
producing a new event, and its decision has to be made against the stream **as it stands now** —
`Future_Feature_Slices.md` already says so: "the undo context is the same fold with the answer
inverted." A stored `CancelHotelContext(bookingExists=true)` is a snapshot of a world that has since
moved on (the booking replaced, the conference declined), so deciding from it would be a bug wearing
the costume of provenance. What undo actually needs is *which booking* and *what to reinstate*, and
both are reachable today through `command_log.event_ids → event_log`. That link is the part worth
having, and it already exists.

**The context splits in two, and only one half is un-recomputable.**

| Half | Examples in the code today | Where it belongs |
|---|---|---|
| Folded from events | `bookingExists`, `TalkPipelineContext.speakingStatus`, `.format` | Nowhere — recompute it by replaying to `asOfSequence` |
| From outside the stream | `Instant now` | Already captured twice: `command_log.timestamp`, and per **R11** on the event payload |

That is the rule to carry forward: **the derived half stays derived; the external half goes on the
event, never into a side blob.** If a context ever gains a genuinely external value — a provider's
quoted price, an exchange rate, a third-party response — that value belongs in an event payload,
because a blob hanging off `command_log` is invisible to every read model and every later decision.

**What storing the context would cost.** It becomes a stored contract exactly like an event (**R7**):
schema versioning, an upcaster, an `EventTypes`-style discriminator, a golden deserialization
sample, and a backup-format bump — all for a value nothing reads. Worse, `ChangeHotelContext` and
`ChangeFlightContext` currently take `exists` from a **details projector**, the R1 violation this
whole doc exists to remove; persisting the context would freeze that violation permanently into the
log, where it survives the fix.

**What `asOfSequence` buys instead**, for 8 bytes and no contract:

- an exact "replay what it saw" — fold the log to that sequence and the original decision is
  reproducible, including for a `FAILED_DOMAIN` row where `error` says *that* it refused but not
  *what state* made it refuse;
- the append condition's `afterPosition` when conditional appends land
  (`CommandConsistencyEventStore.md` §3), which is the reason §1 puts it in `QueryResult` already.

**The one honest argument on the other side** is audit, not undo: if the fold logic changes, a
replay years later can reach a different answer than the original decision did, and only a stored
context proves *why* a decision was made at the time. That matters in a regulated system. For a
personal travel log it is over-engineering, and it is not a reason to build this now.

**Build it with the query slice, not before.** `asOfSequence` has no meaning until a decision is
made against a bounded query rather than `findAll()`; adding a column to `command_log` first would
store the max sequence of "everything," which answers nothing.

---

## Concerns

### 1. Import was the blocker — RESOLVED (2026-08-10), retirement shipped (2026-08-11)

`ImportableCommand.events()` had no event stream and no read model, so each command faked its own
context. `CancelHotelRequest` hardcoded `new CancelHotelContext(true, null, IMPORT_BYPASS_INSTANT)`.
Every fake was a silent divergence between live behavior and import behavior, and if *every* command
gained a real context query, every importable command would have needed an answer.

The options weighed at the time — keep faking per command; give the importer a real query over a
virtual not-yet-applied stream; or export events instead of commands — resolved to the last one.

**Resolution: `archived/EventOrientedBackupRestorePlan.md`.** Backup/restore becomes **event-verbatim** —
restore inserts stored events directly and **never re-executes commands**. `ImportableCommand`,
`events()`, and the whole command-replay path are retired. With no command replay on the restore
path, there is **no decision context to fake**: a command whose decision depends on folded event
state simply has its events restored as data. The blocker this Concern described is gone, and this
slice can build a real `DecisionStream` query without an import story to reconcile.

As of 2026-08-11 this is no longer just planned: `ImportableCommand`, `ImportableCommandTypes`, and
`CommandImporter` are deleted and `BackupService` restores events verbatim. The 10 request DTOs lost
`events()`; the 2 internal-action commands kept it (their `events()` is the live source, not an
import fake). So there is nothing left to reconcile here.

### 2. Two traps in the tdd-game reference implementation — do not copy

- In `InMemoryEventStore.query`, empty `tags` is special-cased to match everything, but empty
  `eventTypes` matches **nothing** (`Set.of().contains(x)` is always false). A "no type filter"
  query silently returns zero events. **Fix:** reject an empty type set in the `EventQuery`
  constructor — a decision query that reads every event type is a smell.
- `containsAll` ANDs the tags, with no OR-across-tag-sets. Fine for per-entity decisions; it cannot
  express "events for hotel X *or* flight Y." Same limitation `CommandConsistencyEventStore.md`
  notes, and its open question 4.

### 3. Ordering must be part of the contract

The `CancelHotel` fold is order-dependent (`HotelBooked` → `HotelChanged` → `HotelBookingCancelled`).
`findAll()` returns insertion order by luck. The query must **promise sequence order, with a test**,
or the eventual `ORDER BY sequence` gets forgotten in the SQL implementation.

### 4. A `tags()` default of `Map.of()` needs a completeness guard

A new event type would silently carry no tags → a query misses it → a decision is made on incomplete
state. Same failure mode as missing an event in a projector.

**Largely answered by the "every id is a tag" rule** (prior art, rule 1): if the rule holds, the
override is mechanical and a missing tag is a missing *line*, not a missing *decision*. The residual
risk is someone forgetting the line entirely. A test over the `EventTypes` registry can close it
mechanically — reflect over each registered event record, find components whose type is an identity
value object, and assert each appears in `tags()`. That is stronger than the allowlist this doc
originally proposed, and it needs no allowlist maintenance.

---

## Open decisions

| # | Decision | Recommendation |
|---|---|---|
| 1 | `DecisionStream` port vs. a `query(...)` method on `CommandExecutor` | The port — see §1 |
| 2 | Scope: `CancelHotel` only, or also convert `ChangeHotel` / `ChangeFlight` / `ChangeGathering` off their projectors | All four — it is what makes the query earn its keep, closes the R1 contradiction, and answers D2's "is this the second caller?" with four |
| 3 | Include `asOfSequence` in `QueryResult` now, unused? | Yes — free now, painful later. See also §5, which is what it is later *for* |
| 4 | Tag shape: single-valued `Map<String,String>` (`CommandConsistencyEventStore.md`) vs. multi-valued `Map<String,List<String>>` (`TaggedEventStoreQueryingDesign.md`) vs. tdd-game's `Set<Tag>` | Multi-valued map — see §3. The two in-repo docs currently contradict each other; whichever you pick, fix the loser |
| 5 | Do any conflict-detection paths need **OR-across-tags** (multi-entity), or is every decision single-entity? | Unknown — needs Ted. Decides whether simple containment is enough, and is `CommandConsistencyEventStore.md` open question 4 |
| 6 | Read the tdd-game project more widely (its `Tag` implementations and the services that build contexts from `query(...)`) to see the wiring end-to-end? | Asked; not yet answered |
| 7 | Also write `asOfSequence` to a new `command_log.as_of_sequence` column when the query lands? | Yes, in the same slice — §5. Nullable with no default, exactly as `event_log.schema_version` is: NULL reads as "decided before bounded queries existed." Since `BackupService` writes `command_log` rows verbatim, it is a new `BackupCommand` field and therefore a **format bump to v4** on the v2→v3 precedent — old files still restore (the field arrives null), new files do not open on older code. Do **not** persist the `DecisionContext` itself, now or later |

---

## Consequences for `DecisionsToReview.md`

Both of these are subsumed by this doc rather than settled independently:

- **D1** (`eventsForDecision()` is a read method on the write-path class) — resolved by deleting it
  in favor of the query port.
- **D2** (the fold reads the entire event log) — resolved by the query. The "second concrete caller"
  test D2 was waiting on is met four times over once open decision 2 is answered.

---

## Related docs

- `TaggedEventStoreQueryingDesign.md` (**repo root**) — the earlier query-side design, with a cost
  estimate. See "Prior art" above; three of its conclusions supersede this doc's first draft.
- `CommandConsistencyEventStore.md` — conditional append / DCB. The endgame this design feeds; §3
  and §5 are the parts to read alongside this.
- `EventSourcingRulesHeuristics.md` — R1 (never decide from a projection), R4 (write-path order),
  R7 (an event's shape is contract), R11 (a displayed time is a payload field) — the last two are
  what §5 rests on.
- `Future_Feature_Slices.md` — "Undo Cancel Hotel Booking," the feature §5 answers *no* for.
- `Backlog.md` — the "ChangeHotel/ChangeFlight still decide from a projector" and "export/import
  needs a wider decision" follow-ups, both from the Cancel Hotel slice.
- `DecisionsToReview.md` — D1, D2.
