# Command Consistency in the Event Store

**Status:** Exploration / feasibility for a future multi-instance deployment. Nothing here
is built yet. This is a design record, not a commitment.

## Why this exists

Today JitterTravel runs as a single application instance with (effectively) a single user.
Scaling to **multiple instances and multiple concurrent users** breaks three assumptions
baked into the current `EventStore`, and raises a correctness question the single-instance
design never had to answer: *what stops a command from appending events that were decided
against stale data?*

This doc covers:

1. Why the current in-memory store can't go multi-instance unchanged.
2. The **conditional append** mechanism, and why it doubles as the staleness fix.
3. The **condition-over-decision-context** (Dynamic Consistency Boundary / DCB) model.
4. How Postgres enforces the condition **atomically**, with the gotchas.
5. Sketch code for the decision-context contract and the conditional append.

## 1. What multi-instance breaks

The current `EventStore` (`infrastructure/EventStore.java`) is built on single-JVM
constructs:

| Construct | Today | Problem across instances |
|---|---|---|
| `List<StoredEvent> events` | in-memory, replayed at boot | Instance B never sees A's appends; B's projections silently go stale |
| `AtomicLong nextSequence` | per-JVM, seeded from `getMaxSequence()` | Two instances hand out the **same** sequence → collisions |
| `transactionLock` | one JVM monitor | Serializes nothing across instances; conflict-freedom today is an accident of single-process deployment |

The arbiter has to move **from the JVM into Postgres**. A JVM lock or an `AtomicLong`
cannot enforce anything across instances; only the database can, atomically, at commit.
So `sequence` must become DB-owned (`GENERATED ALWAYS AS IDENTITY` or a sequence), and the
conflict check must be a DB operation.

### Prerequisite: every append must funnel through `CommandExecutor`

The conditional append is enforced in exactly one place. That only works if **all** event
appends already route through `CommandExecutor` — which is the existing architecture rule in
`CLAUDE.md` ("Application services must never receive `EventStore` as a constructor
dependency"). As of this writing the rule is violated in three application services that
still hold `EventStore` directly:

- `application/ChangeFlight.java`
- `application/ConferencePlanning.java`
- `application/FlightBooking.java`

These must be migrated onto `commandExecutor.execute(...)` / `appendEvents(...)` **before**
the conditional-append work, otherwise those paths bypass the consistency guard entirely.
The existing `CLAUDE.md` TODO — an ArchUnit test asserting no `application`-package class has
an `EventStore` field — should land alongside the migration so the rule can't silently
regress again. Tracked in `docs/Cleanup_Tasks.md`.

## 2. Conditional append, and why it also fixes staleness

A **conditional append** refuses to write events unless the state the decision was based on
is still current. The shape:

```
append(events, commandId, appendCondition)   // reject if state moved since the decision
```

The elegant part: this is **also** the fix for stale read models. Consider instance B
deciding from a projection that's missing instance A's latest event:

1. B builds a decision context, recording the log position it read up to — say `P`.
2. A appends a relevant event at `P+1`.
3. B's append carries the condition "nothing matching my decision has appeared after `P`."
   The DB rejects it.
4. B **catches up** (reads `P+1`), **re-runs `command.execute` on the fresh context**, and
   retries.

So B's projections don't have to be perfectly fresh — only *correct when it matters*, and
the conditional append is what makes "when it matters" enforceable. **Staleness becomes a
performance concern (retry frequency), not a correctness one.**

Requirement this places on us: commands must be **re-runnable** (pure `execute` on a
context — we already have this) and **idempotent on retry** (see §5 open questions on
`commandId`).

## 3. The condition: over the decision context, not a version number

Rather than locking a whole aggregate at "expected version N," we assert exactly the facts
the decision depended on. This is the Dynamic Consistency Boundary (DCB) idea. An append
condition is two things:

- **A query** — the events the decision read, by **type** and by **tag** (entity identity),
  e.g. `{ types: [HotelBooked, HotelChanged], tags: { hotelBooking: <id> } }`.
- **An `afterPosition`** — the max sequence that query observed when the context was built.

The append is allowed iff **no event matching the query exists with `sequence > afterPosition`**.

Payoff over expected-version locking: **no false conflicts.** Two changes to *different*
hotels don't collide; two changes to the *same* hotel field do. You get exactly the
consistency boundary the decision actually needed, not an aggregate boundary drawn in
advance.

### Tags as a JSONB column

Add to `event_log`:

```sql
ALTER TABLE event_log ADD COLUMN tags jsonb NOT NULL DEFAULT '{}';
CREATE INDEX idx_event_log_tags ON event_log USING gin (tags);
```

Tags are **extracted from the event at persist time** (in `StoredEventRow.fromStoredEvent`).
The ids are already in the payload; `tags` just surfaces the identity in a queryable place —
it's a projection of the payload, not new data. Source the map either from the event itself:

```java
public interface Event {
    // ... existing ...
    default Map<String, String> tags() { return Map.of(); }
}

public record HotelChanged(UUID hotelBookingId, /* ... */) implements Event {
    @Override
    public Map<String, String> tags() {
        return Map.of("hotelBooking", hotelBookingId.toString());
    }
}
```

…or from a separate `Tagger` keyed by event type if we want event records to stay
tag-agnostic. Leaning toward the event exposing its own identity.

A DCB query then matches a row when:

```sql
type = ANY(:queryTypes) AND tags @> :queryTags::jsonb
```

Start with the single JSONB column. Only reach for a normalized `event_tags(sequence, key,
value)` table if we later need OR-across-tag-sets queries or per-tag unique constraints.

## 4. Postgres enforcement — and the trap

### The conditional insert

```sql
INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload, tags)
SELECT :sequence, :eventId, :commandId, :timestamp, :type,
       CAST(:payloadJson AS jsonb), CAST(:tagsJson AS jsonb)
WHERE NOT EXISTS (
    SELECT 1 FROM event_log
    WHERE sequence > :afterPosition
      AND type = ANY(:queryTypes)
      AND tags @> CAST(:queryTags AS jsonb)
);
```

`update()` returns 0 rows → condition violated → conflict signal.

### The trap: this is NOT safe under READ COMMITTED

Under READ COMMITTED (our default), each statement reads a snapshot from statement start.
Two concurrent transactions both run the `NOT EXISTS` subquery, **neither sees the other's
uncommitted insert**, both pass, both commit. We've appended two conflicting events. **No
unique constraint catches this** — we're guarding against a *phantom* (the absence of a
matching row across a predicate range), not a duplicate of a specific row.

Three ways out:

**Option 1 — `SERIALIZABLE` + retry on `40001` (the faithful DCB choice, recommended default).**
Run the conditional insert in a serializable transaction. Postgres SSI takes predicate locks
on the range the `NOT EXISTS` scanned; a concurrent insert into that range creates a
read-write dependency and one transaction aborts with a serialization failure (`40001`). We
catch it and retry. Does **not** require holding the user's think-time read inside the
transaction — the condition is re-evaluated atomically at commit.
*Cost:* every append needs a retry loop; SSI has predicate-lock overhead and can
occasionally abort non-conflicts. For a low-contention personal-travel app, negligible.
*Requirement:* keep the predicate **index-backed** (GIN on `tags`, btree on `sequence`) so
predicate locks stay fine-grained and SSI doesn't over-abort.

**Option 2 — per-tag transactional advisory locks.**
`pg_advisory_xact_lock(hashtext(tag))` for every tag in the condition before the check, at
READ COMMITTED. Serializes only overlapping-tag appends; no SSI overhead. *Downsides:* must
lock all tags in a consistent order to avoid deadlock; hash collisions cause false
contention.

**Option 3 — per-tag version rows + optimistic update.**
A `tag_version(tag, version)` row bumped via `UPDATE ... WHERE version = :expected`; zero
rows updated = conflict. Robust at any isolation level, but reintroduces an explicit per-tag
"stream version," and a multi-tag event must bump several rows (deadlock ordering again).
Essentially classic aggregate optimistic locking in DCB clothing — simplest if conflicts are
always single-entity.

### Sequence ownership gotcha

Moving `sequence` to a DB sequence introduces **out-of-commit-order assignment and gaps**:
txn A grabs seq 5, B grabs 6, B commits first. A reader taking `MAX(sequence)` as a
high-water mark sees 6, advances past it, then **misses 5** when it commits.

- For the **append condition itself**: SERIALIZABLE saves us — the late-committing seq 5
  that should have conflicted triggers the SSI abort regardless of sequence ordering.
  Correctness of the guard does not depend on monotonic visibility.
- For **projection catch-up across instances** (how B learns of A's events and picks an
  `afterPosition`): the gap problem is real. Polling `WHERE sequence > checkpoint` can skip a
  not-yet-committed lower sequence. Standard fixes: a contiguous checkpoint that only
  advances past gap-free runs; a separate commit-ordered column; or `LISTEN/NOTIFY` to wake
  projectors and re-scan. **This determines whether `afterPosition` is trustworthy** — to be
  resolved before relying on it.

## 5. Sketch code

> Illustrative, not final. Names/signatures TBD.

### Decision context records what it read

Today `DecisionContext` is a bare marker. It grows the ability to describe the query and
position it was built from:

```java
public interface DecisionContext {
    AppendCondition appendCondition();
}

/** What the decision read (query) and how far it had seen (afterPosition). */
public record AppendCondition(EventQuery query, long afterPosition) {
    public static final AppendCondition UNCONDITIONAL =
            new AppendCondition(EventQuery.NONE, Long.MAX_VALUE); // opt-out, behaves like today
}

public record EventQuery(Set<String> types, Map<String, String> tags) {
    public static final EventQuery NONE = new EventQuery(Set.of(), Map.of());
}
```

### Building the context at the boundary (controller/service)

The read that builds the context also returns the position it observed — captured at the
boundary, like `now`/UUIDs already are:

```java
public void changeHotel(UUID commandId, ChangeHotelRequest request, Instant now) {
    ChangeHotelCommand command = new ChangeHotelHandler(zoneResolver).handle(request);
    UUID bookingId = command.hotelBookingId();

    // Read model returns BOTH the state and the log position it reflects.
    ReadResult<Boolean> exists = detailsProjector.existsAsOf(bookingId);

    EventQuery query = new EventQuery(
            Set.of("HotelBooked", "HotelChanged"),
            Map.of("hotelBooking", bookingId.toString()));

    ChangeHotelContext context = new ChangeHotelContext(
            exists.value(),
            now,
            new AppendCondition(query, exists.position()));  // afterPosition = what we read

    commandExecutor.execute(commandId, request, context, command);
}
```

### CommandExecutor enforces the condition and retries

```java
public <C extends DecisionContext> void execute(
        UUID commandId, Object request, C context, DomainCommand<C> command) {

    persister.saveCommand(commandId, request);          // write-ahead, PENDING

    List<? extends Event> events;
    try {
        events = command.execute(context).toList();
    } catch (RuntimeException domainException) {
        persister.markCommandFailed(commandId, "FAILED_DOMAIN", domainException.getMessage());
        throw domainException;
    }

    try {
        // append now takes the condition; throws AppendConditionFailed if violated
        eventStore.append(events.stream(), commandId, context.appendCondition());
    } catch (AppendConditionFailed conflict) {
        persister.markCommandFailed(commandId, "FAILED_CONFLICT", conflict.getMessage());
        throw conflict;   // caller (or a retry wrapper) rebuilds context and re-runs
    }
    // ... existing FAILED_PERSIST handling unchanged ...
}
```

> **Open design point (retry placement):** `CommandExecutor` can't transparently retry today
> because it's handed a *finished* context — it can't rebuild it. Either (a) the caller
> rebuilds + re-submits on `FAILED_CONFLICT`, or (b) `execute` accepts a
> `Supplier<C> contextFactory` instead of a `C`, so it can rebuild and loop internally.
> Option (b) keeps callers simple but changes the signature for every service.

### EventStore.append gains the condition

```java
public void append(Stream<? extends Event> eventStream, UUID commandId,
                   AppendCondition condition) {
    // sequence assignment moves to the DB; in-memory list + AtomicLong are removed
    // for the multi-instance design (replaced by read-through / LISTEN-NOTIFY catch-up).
    List<StoredEvent> storedEvents = /* map payloads, extract tags */;

    int rowsAppended = persister.appendConditionally(storedEvents, commandId, condition);
    if (rowsAppended < storedEvents.size()) {
        throw new AppendConditionFailed(condition);
    }
}
```

### PostgresPersister conditional append

```java
@Transactional  // isolation = SERIALIZABLE for the conditional path; retry 40001 above
public int appendConditionally(List<StoredEvent> events, UUID commandId,
                               AppendCondition condition) {
    int total = 0;
    for (StoredEvent event : events) {
        total += jdbcClient.sql("""
                INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload, tags)
                SELECT nextval('event_seq'), :eventId, :commandId, :timestamp, :type,
                       CAST(:payloadJson AS jsonb), CAST(:tagsJson AS jsonb)
                WHERE NOT EXISTS (
                    SELECT 1 FROM event_log
                    WHERE sequence > :afterPosition
                      AND type = ANY(:queryTypes)
                      AND tags @> CAST(:queryTags AS jsonb)
                )
                """)
                .param("eventId", event.eventId())
                // ... remaining params, condition.query() → queryTypes/queryTags,
                //     condition.afterPosition() → afterPosition ...
                .update();
    }
    linkCommandToEvents(commandId, events.stream().map(StoredEvent::eventId).toList());
    return total;
}
```

## Open questions

1. **DecisionContext contract change** is the most invasive app-side cost — every service
   that builds a context must thread an `afterPosition` and query through. Acceptable, or do
   we want a lighter opt-in (`UNCONDITIONAL` default so existing services compile unchanged
   and migrate one at a time)?
2. **Retry placement:** caller re-submits vs. `execute(Supplier<C>)` internal loop (§5).
3. **`commandId` on retry:** reuse the same id (idempotent, but a real retry and a duplicate
   submission look identical) vs. new id per attempt (cleaner audit; multiple PENDING rows
   per logical intent — fits the current `command_log` status model better).
4. **Conflict granularity:** is there a real **multi-tag cross-entity** invariant in our
   commands, or is every conflict single-entity? If single-entity in practice, Option 3
   (per-tag version) is dramatically simpler and may remove the need for SSI entirely.
5. **Projection catch-up across instances:** poll-with-contiguous-checkpoint vs.
   `LISTEN/NOTIFY` vs. read-through. Determines `afterPosition` trustworthiness (§4 gotcha).
6. **Read model "as-of position":** projectors must return *both* state and the log position
   that state reflects (`ReadResult<T>` above). How does that interact with the existing
   in-memory projector design?
