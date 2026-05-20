# Event Sourcing — Rules & Heuristics

A living list of rules ("never violate") and heuristics ("prefer, here's why")
we follow in this codebase. New ones are appended as we discover them.

---

## Rules (do not violate)

### R1. Never use a projection (read model) to make an automated decision.

Projections are potentially **stale** and are intended to serve views, not the
write path. Even when subscribers are synchronous today (as in this codebase),
that is an implementation detail — replays, retries, async transports, or
multi-node deployments can introduce staleness. Read-side state must never be
the source of truth for "should this command be allowed / what events should
I emit?". To decide, fold from the authoritative event stream.

### R2. Events are immutable, in the domain, and implement `Event`.

Domain events live in `dev.ted.jittertravel.domain` as Java `record`s that
implement the `Event` marker interface. Once written, an event is never edited
or deleted — corrections happen by emitting a new compensating event.

### R3. Commands are pure domain functions.

Command classes live in `dev.ted.jittertravel.domain` and expose an
`execute(...)` method that returns `Stream<? extends Event>` (or throws a
domain exception). They take only value inputs (DTO, current time, any folded
state needed for decisions). They have no infrastructure dependencies.

### R4. Application services orchestrate the write path in a fixed order.

1. Reject if the event store is in read-only mode.
2. Persist the command to the write-ahead log (idempotency boundary).
3. Fold any state the command needs from the authoritative event stream.
4. Run the pure domain command to compute new events.
5. Append events to the `EventStore`.

### R5. Read-only fallback on persistence failure.

If the durable event store cannot be loaded at startup, or cannot accept an
append at runtime, the application enters a read-only mode and rejects new
commands. Failures are surfaced, never silently swallowed.

### R6. Do not change an existing event's structure without a migration plan.

Once an event type has been persisted, its shape is contract. Removing,
renaming, or retyping a field — or changing the meaning of an existing
field — requires a deliberate migration: at minimum the deserializer must
keep reading old rows correctly, and any consumer (projection, fold) that
read the field must continue producing the right state on replay.

**What is safe without migration:** adding a new optional/nullable field
that no existing fold or projection consumed. Old persisted rows
deserialize with the new field set to its default (e.g., `null`), and no
existing replay path observes a difference.

**What requires a migration:** removing a field, renaming a field,
changing a field's type, narrowing a previously-nullable field,
re-interpreting an existing field's meaning, or splitting one event type
into several. Migration may take the form of in-place upcasting at
deserialization, a one-off conversion that rewrites stored rows, or
introducing a new event type and leaving the old one in place.

### Enforcement of R6

We pair the rule with mechanical checks. Adopted now (cheapest), with
heavier options recorded for later.

**Adopted: golden-payload deserialization tests.** For every persisted
event type we commit one canonical JSON sample under
`src/test/resources/event-samples/`. A test deserializes each sample with
a `JsonMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES = true`
(stricter than production) so that:

- *Adding* an optional nullable field still passes (old JSON has no such
  field; deserialization populates `null`).
- *Removing* a field fails (the old JSON still carries it, and unknown
  properties are now errors).
- *Renaming* a field fails for the same reason.
- *Changing* a field's type generally fails at parse time.

This is a CI-time gate, not a runtime gate. It catches structural
regressions; it does not catch semantic re-interpretation of an existing
field (you can change what a field *means* without changing its name or
type — that always requires human judgment).

**Deferred options, to reconsider as the system grows:**

- *Reflection snapshot test.* Reflect over every `Event` record and emit
  a stable signature (component name + type + nullability); compare
  against a committed `event-schema.txt`. Catches pure renames and
  type-narrowing more sharply than the golden-payload test, at the cost
  of more cross-cutting test machinery.

- *`@EventSchema(version = N)` + upcaster chain.* Each event carries an
  explicit schema version. The persister stores `(eventName, version)`;
  the deserializer routes pre-current-version rows through registered
  upcasters. This is the right destination if/when we adopt the
  `@EventName` discriminator from `TaggedEventStoreQueryingDesign.md`;
  it gives a runtime guarantee, not just a CI guarantee.

- *PR template checklist.* A line in `PULL_REQUEST_TEMPLATE.md`: *"If
  this PR changes an existing event record, the migration plan is in the
  description."* Cheap to add alongside any mechanical check.

---

## Heuristics (prefer, with reason)

### H1. Prefer full-snapshot events over deltas when feasible.

A "FlightChanged" carrying the complete new field set lets every consumer
overwrite the row keyed by the aggregate id — no merge logic in projections,
no order-sensitivity, easier to reason about replay.

### H2. One projector per web view.

Don't share a projector across views. Each projector shapes / pre-formats data
for exactly one view, so the web layer stays presentation-only and projectors
can evolve independently.

### H3. DTOs in `web`, view records in `application`.

Request DTOs (e.g., `BookFlightRequest`) live in `dev.ted.jittertravel.web`.
Read-model / view records (`BookedFlightView`, `CalendarEntry`) live in
`dev.ted.jittertravel.application`. View records carry pre-formatted strings
ready for the template — no formatting logic in templates or controllers.

### H4. Use the aggregate id as the command id.

The aggregate id (e.g., `flightId`, `conferenceId`) is also the command id
recorded in the WAL. This gives natural idempotency on re-submission and ties
the command back to the entity it acts on.

### H5. One code path runs at startup replay and at runtime append.

The same `subscribe(projector); projector.handle(eventStore.findAll())`
sequence in `EventSourcingConfig` builds projector state at boot, and the
same `notifySynchronousSubscribers(...)` path runs on every append. Replay
is just a special case of "events happened".

### H6. Tests share the in-memory EventStore across integration test classes.

Only the DB is truncated between tests; the in-process `EventStore` is reused.
Write integration-test assertions to be **specific** (filter by a unique
flight number, conference name, etc.) rather than relying on global counts.
