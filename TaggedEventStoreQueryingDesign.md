# Tagged Event Store Querying — Design Notes

A design sketch and cost estimate for evolving `EventStore` to support
filtered queries by event-type name and by tag (e.g., "all events tagged
`flightId=...`"), with the filter pushed down to Postgres via JSONB +
GIN indexing.

**Status:** Not implemented. We are deferring this until a second concrete
caller (beyond `ChangeFlightCommand`) demands it, or until concurrency
pressure requires fenced appends. For slice 2 we use inline
`eventStore.findAll().filter(...)` in the application service.

This document is intentionally non-prescriptive: it captures the shape and
the cost so the decision is informed when we revisit it.

---

## Direction

Move toward Dynamic Consistency Boundaries (DCB) rather than rigid aggregate
roots / repositories. The first half of DCB is an efficient
**query-then-fold** API on the event store. The second half (position-fenced
append) is independently valuable and can land later.

This document covers the first half only.

---

## Tag model

### Core rule: every ID in an event is a tag

When a new event is introduced, **every field whose type is an identity
value object** (`FlightId`, `ConferenceId`, future `LodgingId`, `TripId`,
etc.) is exposed as a tag automatically. This deliberately removes the
"should I tag this field?" judgment call and avoids the tag-set evolution
problem — old events never need backfill for newly-deemed-tag-worthy fields,
because by construction every id field was already a tag from day one.

Non-id fields (`airline`, `city`, free-text names) are not tags by default.
If a query ever needs to filter on one of those, we either (a) accept the
backfill cost at that moment or (b) treat it as evidence the field should
have been promoted to a typed id in the domain.

### Tag shape

`Tag(String name, String value)`, multi-valued per name within an event:

```
{ "flightId": ["..."], "tripId": ["..."] }
```

Multi-value support is cheap to add up front and annoying to retrofit later.
It covers events that naturally relate to multiple ids of the same kind
(e.g., a hypothetical `FlightsRebooked` covering two flights).

### Event-type name vs Java class

Today `event_log.type` stores the Java FQN. Once queries depend on it,
renaming an event class becomes a breaking change. Introduce an
`@EventName("FlightBooked")` annotation (or a tiny registry) so the
discriminator is decoupled from class identity *before* the first query
ever runs against `type`. Cheap, and a precondition for safely renaming
event records.

---

## Schema changes

Append to `schema.sql` (all idempotent):

```sql
ALTER TABLE event_log
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS event_log_tags_gin
    ON event_log USING GIN (tags jsonb_path_ops);

-- Optional, only if event-name filtering becomes hot:
CREATE INDEX IF NOT EXISTS event_log_type_idx ON event_log (type);
```

`jsonb_path_ops` is smaller and faster than the default GIN op class, and
supports the `@>` containment operator, which is what we want for
"events whose tags contain `{flightId: X}`."

### Backfill

A one-off boot job that, for each existing row, deserializes the payload,
asks the in-process `Event.tags()` method for the tag set, and updates
`event_log.tags`. Running the same code path that the live write path uses
guarantees they agree.

---

## Domain changes

- New `Tag` record: `Tag(String name, String value)`.
- New method on `Event`:
  ```java
  default Map<String, List<String>> tags() { return Map.of(); }
  ```
  Each event overrides to return its id fields. Following the core rule
  above, the override is mechanical — one line per id field.
- New `@EventName("FlightBooked")` annotation on each event record (or a
  registry mapping name ↔ class). Read by the persister on write and resolved
  on read.

---

## EventStore API

A small `Query` value type, AND-of-equality semantics to start:

```java
record Query(Set<String> eventTypeNames, List<Tag> tagsAll) {}
```

`Set` is intentionally avoided for richer predicates later; for now flat
AND is enough. OR-of-AND can be added when a real caller needs it.

```java
public interface EventStore {
    Stream<StoredEvent> findAll();                       // existing
    Stream<StoredEvent> findAllMatching(Query query);    // new
    // append(...) unchanged for now; fenced append is the second half of DCB.
}
```

Two implementations:
- **In-memory:** scan + filter over the existing `List<StoredEvent>`.
  Identical cost to `findAll().filter(...)`; useful for tests and
  small-scale.
- **Postgres-backed:** SQL with parameterized filters:
  ```sql
  SELECT sequence, event_id, command_id, timestamp, type, payload::text
  FROM event_log
  WHERE type = ANY(:names)
    AND tags @> :tagsJson::jsonb
  ORDER BY sequence
  ```
  The GIN index covers the `@>` predicate; `type = ANY(...)` uses the
  optional B-tree index.

---

## Cost estimate

Rough effort for one developer who knows the codebase, end-to-end with tests:

| Area                                                | Effort |
|-----------------------------------------------------|--------|
| `Tag`, `Event.tags()` default, per-event overrides  | ½ day  |
| `@EventName` discriminator + persister wiring       | ½ day  |
| Schema additions + GIN index + boot-time backfill   | ½ day  |
| `Query` type + in-memory `findAllMatching`          | ½ day  |
| Postgres `findAllMatching` (SQL translator)         | ½ day  |
| Testcontainers tests, incl. EXPLAIN to verify index | ½ day  |
| Doc updates, rules-file additions                   | ½ day  |

**Total: roughly 3–4 focused days.** No surprises expected: payload is
already JSONB, `type` is already a discriminator column, and there is no
migration tool to integrate with — `schema.sql` is appended in place.

---

## Risk surface

- **Backfill correctness.** Mitigated by running the live `Event.tags()`
  code path during backfill so the same logic governs both.
- **Event renames.** Solved by `@EventName` *before* any query depends on
  `type`. Doing this in the same change keeps it cheap.
- **Tag-name typos.** A `TagName` value type with a private constant set
  (or per-event constants) is enough.
- **Tag set evolution.** Largely eliminated by the "every id is a tag"
  rule. Remaining risk only applies if a non-id field is promoted to a
  tag later.

---

## What this enables (and what it does not)

**Enables now:** efficient, indexed read-side queries for write-side folds
("give me all events tagged `flightId=X` of type `FlightBooked` or
`FlightChanged`"). Replaces ad-hoc `findAll().filter(...)` patterns once
they multiply.

**Does not enable on its own:** safe concurrent appends. Without
position-fenced `append(events, query, expectedHeadAtRead)`, two
concurrent change commands can both read the same state, both decide to
emit, and both succeed — last-write-wins on the projection. Fenced
append is the second half of DCB and is intentionally out of scope for
this document.
