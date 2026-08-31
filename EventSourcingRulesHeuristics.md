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
I emit?". To decide, fold from the authoritative event stream using a
"strongly-consistent decision query".

### R2. Events are immutable, in the domain, and implement `Event`.

Events live in `dev.ted.jittertravel.domain` as Java `record`s that
implement the `Event` marker interface. Once written, an event is never edited
or deleted — corrections happen by emitting a new compensating event.
Note that these are not *Domain Events* in the sense of Domain-Driven Design,
but are private to this Bounded Context and are not to be accessed from,
nor published to, any external module or process.

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

### R6. The event log is append-only; stored events may never be modified or deleted.

`UPDATE` and `DELETE` against the event store are prohibited. An event, once
appended, is permanent. Corrections to domain state happen by appending new
compensating or superseding events — never by mutating or removing existing
rows.

This rules out SQL-level rewrites as a migration strategy for field renames
(see R7). The permitted migration paths are deserialization-time transforms
(`@JsonAlias`, custom `JsonDeserializer`, or upcaster chains) and adding new
optional fields.

---

### R7. Do not change an existing event's structure without a migration plan.

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

### Enforcement of R7

We pair the rule with mechanical checks. Adopted now (cheapest), with
heavier options recorded for later.

**Adopted: golden-payload deserialization tests.** For every persisted
event type we keep one canonical JSON sample as an inline text block in
`GoldenEventDeserializationTest` (samples are well under the 30-line
threshold for a separate file; there is no `event-samples/` resource
directory). Legacy-shape samples live in the same test and are routed
through `EventPayloadUpcaster` before binding, since the removed keys
would otherwise trip the strict mapper. That mapper is a
`JsonMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES = true`
(stricter than production, which ignores unknown properties) so that:

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

- *PR template checklist.* A line in `PULL_REQUEST_TEMPLATE.md`: *"If
  this PR changes an existing event record, the migration plan is in the
  description."* Cheap to add alongside any mechanical check.

**No longer deferred — the versioned upcaster chain is built** (was listed
here as `@EventSchema(version = N)` + upcaster chain). Both halves shipped:

- *Stable name:* `infrastructure/EventTypes.java`, a logical-name registry
  with an append-only alias log, decouples the `event_log.type`
  discriminator from Java class identity. (`TaggedEventStoreQueryingDesign.md`
  proposed an `@EventName` annotation for this; the registry does the same
  job, so that proposal is obsolete — do not add a second discriminator
  mechanism.)
- *Version:* a per-row `event_log.schema_version` column (a column, not a
  payload key — see `docs/archived/LegacyEventEagerMigrationPlan.md`), with the current
  version per type hanging off `EventTypes.register(...)`, and
  `EventPayloadUpcaster` climbing one `EventUpcaster` rung per version step
  on every read path. This is the **runtime** guarantee this entry wanted.
  Mechanism doc: `docs/EventPayloadUpcasterDesign.md`.

### Corollaries of R7 (learned the hard way, 2026-06 → 2026-08)

Three rules the migrations of that period paid for. Full retrospective:
`docs/MigrationLessonsLearned.md`.

**R7a. An upcaster rung is a pure function of (payload, constants).** No
external lookup, no injected resolver, no I/O. The datetime rungs violate
this — they re-derive a zone from `LocationZoneResolver` on every read — and
the bill came due three ways: one unknown city (Casablanca) killed boot
replay; one data-entry error (Antwerp filed under country "Brussels") became
a resolver hack that cannot be removed, because fixing the hotel appends a
new event and never rewrites the bad original; and the resolver's coverage
became a permanent replay dependency. If a transform needs a lookup, do the
lookup **once**, eagerly, and bake the result into the row
(`/admin/migrate-legacy-events`). Judge a new rung by whether deleting its
collaborators would change any decoded value.

**R7b. A golden sample is a fossil: add, never edit.** A shape change adds a
new sample of the shape being **retired**, alongside the current one. Hotels
and trains rewrote their samples in place, so the repo carried zero coverage
of any legacy shape until the gathering slice needed it. A legacy sample
binds through `EventPayloadUpcaster`; a current sample binds directly.

**R7c. Renaming an event's logical name spends the rollback.** Renaming the
Java class is free (R7 / `EventTypes`). Renaming the *logical* name costs an
`alias` for every wire id the type was ever stored under, and moves every
`EventUpcaster.canHandle` for that type in the same commit or those rows
silently stop climbing. Rewriting `event_log.type` afterwards is a **one-way
door**: an alias teaches today's build yesterday's names and never the
reverse, so an older build then fails its boot replay and lands read-only
with empty read models. Take a backup immediately before that rewrite and
keep it — that file restores into either build, and it is the entire safety
story. See `docs/archived/EventTypeColumnNormalizationPlan.md`.

---

### R8. A projector that derives a field must derive it from the events — it may not ignore relevant data the events carry.

A projection's only job is to reproduce state from the events. If a projector
puts a field on its view, that field's value must come from the folded events.
Hardcoding a literal for a field the matched event actually carries — or dropping
a field the event provides — is prohibited: the view then reports a constant
instead of the recorded truth, and nothing breaks at compile time.

Concretely: `HotelBooked`/`HotelChanged` carry a `bookingIntent`
(`TENTATIVE`/`FINAL`), but `BookedHotelsProjector` had been building its view row
with a hardcoded `BookingIntent.TENTATIVE`, so `/booked-hotels` showed "Tentative"
even for a stay that was booked or later changed to `FINAL`. The fix threads
`e.bookingIntent()` from the event through the fold into the view.

This is the field-level twin of the event-exhaustiveness hazard (a projector can
silently miss an *event* it should handle): a projector can just as silently
ignore a *field* on an event it does handle. The failure mode is invisible
because the literal is a valid value of the right type.

**Enforcement.** Guard each derived field with a fold scenario test that
records the *non-default* value (book/change an entity to `FINAL`, not just the
default) and asserts the view reflects it. When you add a field to an existing
event, walk every projector that folds that event and thread the new field
through rather than defaulting it.

---

### R9. A projector computes its read model while handling events; reads return that maintained state.

A projection's read model is built up in the event-handling path and served
as-is. A read method returns the state maintained during `handle(...)` — it must
**never re-derive that state from the event-shaped fold on each call**. Anything
derived purely from the events is computed *as the events are handled* (once per
handled batch — a single pass at startup replay, once per append at runtime) and
stored; the read method just hands it back.

The one thing a read method may apply is an input that is **not an event**: the
current time, or the viewer's identity. So `views(TimeView, now)` is compliant —
it applies `now` (a runtime input, not an event) to rows already folded in
`handle(...)`. `ScheduleGapProjector.problems()` originally *violated* this: it
re-ran the entire gap / conflict / missing-hotel detection over its folded maps
on every call. It now recomputes once at the end of `handle(...)` into a cached
list and returns that.

**Why:** it is what "projection / read model" means — the write path maintains
the model, the read path serves it. Re-deriving on read blurs that line, silently
moves real work onto every request, and (for anything O(n²) like conflict
detection) turns a cheap read into a scaling hazard. Consistent with R1
(projections serve views, not the write path) and H5 (one code path at replay and
append).

**Enforcement.** A scenario test that handles one batch, reads, handles a
*resolving* event, then reads again: the second read must reflect the change —
proving the model refreshed on the later batch rather than being computed once or
lazily on read. (`ScheduleGapProjectorTest.readModelRefreshesAfterEachHandledBatchNotJustTheFirst`.)

---

### R10. Every field a read model displays must come from an event.

A projection reproduces state from events and from nothing else. If a view puts a
value on the screen, that value's source must be a field on an event the projector
folded. A displayed value with **no event behind it** is data arriving from
somewhere the model does not record — most often the event-store envelope, an
ambient read, or a literal.

**This is the mirror of R8, and the two are the same defect from opposite sides:**

| | Direction | Failure mode |
|---|---|---|
| **R8** | event → view | the event carries the field; the projector ignores it or hardcodes a literal |
| **R10** | view → event | the view displays a field **no event provides** |

Both are invisible at compile time and produce a plausible-looking value of the
right type, which is why they need a rule rather than a review.

Concretely: `BookedFlightsProjector` built its inline change history —
`"Booked on 2026-08-14 3:12PM"` — from `storedEvent.timestamp()`, the event-store
envelope. Nothing on `FlightBooked` or `FlightChanged` carried that time, so the
history reported infrastructure metadata as a domain fact. Found 2026-08-27 while
extracting the Book Flight event-model chapter; fix owned by
`docs/EventOccurrenceTimestampsPlan.md`, and the specific case is R11 below.

**The modelling twin — where an *event's* fields come from.** Doing this check the
other way (every field on an event traces to a declared source: a command
parameter, the trigger event of an automation, an inbound external event, a
consumed read model, a value **captured** at the boundary, or a value **derived**
from a curated table) is a design-time exercise rather than a separate rule,
because in this codebase its two hard cases are already covered: values captured
at the boundary by the injected-`Clock` rule and "external inputs from the
boundary" in `CLAUDE.md`, and values derived from a curated table by the
`domain`-purity rule. Naming the source is still worth doing when drawing a
chapter — see `~/.claude/event-model-diagram-layout.md` for the notation — but the
enforceable half is R10.

**Enforcement.** Guard each displayed field with a fold scenario test that records
a *distinctive* value through real events and asserts the view shows it. The
question to ask of any view field is "which event field is this?" — if the answer
is "the envelope", "the clock", or "a constant", it is an R10 violation.

---

### R11. A time a user reads is an event field, never the store's envelope.

The event-store envelope's `recorded_at` is **metadata**: it exists so rows can be
ordered, and nothing else. Any time that reaches a **view** is a domain fact and
must be a field on the event payload, named for its purpose in the domain
(`recordedAt`, `bookedAt`, `cancelledAt`), captured by the producer at the
boundary from the injected `Clock`. Never defaulted to a `now()` at write time
deep in infrastructure.

Two reasons, and the second is the one that bites:

1. **Testability.** A payload field captured at the boundary can be pinned with a
   `Clock.fixed(...)`. An envelope stamp is set inside `PostgresPersister`, where
   no test can reach it — the same argument as the ambient-clock rule.
2. **The label lies.** JitterTravel records things that already happened outside
   the app, so the moment of *recording* is not the moment the thing *happened*.
   Rendering an envelope stamp as `"Booked on …"` claims a booking time and shows
   a data-entry time. If the value is a recording time, the word must be
   "Recorded on". A separate user-supplied occurrence time is a different field
   and a product decision, not a rename.

Vocabulary and the full argument: Verraes, [*Multi-temporal
Events*](https://verraes.net/2022/03/multi-temporal-events/) — `recorded_at` is
metadata; occurrence time is a payload property named after its purpose, filled by
the producer.

**`BackupService` is not this.** Event-oriented backup writes every `event_log`
row verbatim, envelope included, and restores it verbatim. Reading the envelope
there is the whole point and stays.

**Enforcement (to build with the fix, see
`docs/EventOccurrenceTimestampsPlan.md`).** A plain source scan in the style of
`NoAmbientClockReadsTest` and `DomainIsPureTest`: no `StoredEvent.timestamp()`
read anywhere in `application` or `web`, with `BackupService` exempt. Cheap,
mechanical, and it fails on arrival rather than after someone remembers to look.

---

### R12. A read model is built from events alone — never from another read model.

A projector folds events, plus the read-time criteria its caller supplies (R9,
H8). It may not hold, be constructed with, or name **another projector** — not a
field, not a constructor parameter, not a method parameter, not a static call.

**Composing read models is a different operation, and it is allowed — encouraged,
even.** It belongs one layer up, where the fan-in is visible:
`CalendarAggregator` combines the five calendar projectors into the owner's
calendar, and `CalendarController` hands `ScheduleGapProjector.awayDays()` to the
renderer alongside those entries rather than routing it into a projection. Both
are read models being *composed*; neither is a read model *depending on* one.

**When two read models need the same derived value, share the rule, not the
projection.** `BookedHotelsProjector` puts the schedule's `Place` on its view
"derived by the same rule `ScheduleGapProjector` uses, not alongside it" — the
derivation lives in the domain (`Place.of`), so both projectors reach the same
answer without either knowing the other exists. That is the fix whenever this
rule seems to be in the way.

**Not this rule: a Translator/Processor consuming a read model.** R10's modelling
twin lists "a consumed read model" as a legitimate source for an event's fields.
That is Read Model → Processor → Command/Event, a different edge from Read Model →
Read Model: the consumer is an automation component, not a projection. R1 governs
it — fold from the authoritative stream to *decide* — and R12 does not reach it.

**Why:** a projector that reads another inherits its staleness and, worse, its
half-folded state mid-batch — and it silently makes the subscriber order in
`EventSourcingConfig` load-bearing. That is an ordering dependency nothing
declares and no test would catch, because at replay both projectors are handed
the same list in whatever order the wiring happens to pick (H5). It also undoes
H2: two projectors sharing a shape can no longer evolve independently.

**Enforcement.** `ProjectorsDependOnEventsAloneTest` — a plain source scan in the
style of `DomainIsPureTest`, over every `*Projector` in `application` with
comments and string literals blanked out, failing on any mention of another
`*Projector` or `*Aggregator`. Comments may name one (`BookedHotelsProjector`
does, deliberately); code may not.

---

## Heuristics (prefer, with reason)

### H1. Prefer the smallest delta events over full-snapshot deltas when feasible.

While a "FlightChanged" carrying the complete new field set lets every consumer
overwrite the row keyed by the flight's ID, it unnecessarily increases the cost
of storage and applying of events (fold). It also makes it more difficult to see
the actual change that the event represents.

While the current implementation of JitterTravel has used large events,
which are very CRUD-like, this is due to the edit screens being generic,
allowing any kind of change (e.g., FlightChanged instead of FlightDepartureTimeChanged)
and not because it represents a best-practice for Event-Sourcing.

Therefore, more events that are more fine-grained are preferred over larger
CRUD-like events.

### H2. One projector per web view.

Don't share a projector across views. Each projector shapes / pre-formats data
for exactly one view, so the web layer stays presentation-only, and projectors
can evolve independently.

### H3. DTOs in `web`, view records in `application`.

Request DTOs (e.g., `BookFlightRequest`) live in `dev.ted.jittertravel.web`.
Read-model / view records (`BookedFlightView`, `CalendarEntry`) live in
`dev.ted.jittertravel.application`. View records carry pre-formatted strings
ready for the template — no formatting logic in templates or controllers.

### H4. Use the entity id as the command id.

The entity id (e.g., `flightId`, `conferenceId`) is also the command id
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

### H7. There are no aggregates in this application

All "concepts" are Entities with IDs, e.g., `flightId`, `trainId`, `conferenceId`, but are not further grouped into larger sets such as Aggregates.
In event-sourcing as we prefer to dynamically combine events into pieces of state specifically needed to execute domain logic inside command's `execute` method.
All queries are done without entities, making the rigid grouping that Aggregates provide unnecessary.

### H8. Projecting a read model and filtering one are different operations — keep them apart.

Two things a projector does that are easy to conflate (this is the working
distinction behind R9):

- **Projecting** *builds* the read model from events. It runs in `handle(...)`,
  once per handled batch, and depends only on the events. What it maintains is the
  **whole** model — every booked hotel, every schedule problem.
- **Filtering / selecting** *narrows* that maintained model at read time, using
  **select criteria supplied by the caller**. Most often the criterion is the
  current time (`views(TimeView, now)`), but not always: it can be the viewer's
  identity, a requested date (`ItineraryProjector.entriesForDate(date)`), or a
  standing predicate over the view records' own fields
  (`ConferenceProjector.migratableViews()` = the single-day conferences).
  A filter reads the already-projected records; it never re-derives them from events.

The test for which one you are looking at: **does the read method run the domain
logic that *produces* the records, or does it just *choose among* records already
produced?** Choosing among them — by time, viewer, date, or a predicate on their
fields — is a legitimate query on the read model. Re-running the production logic
on read is the R9 violation `ScheduleGapProjector.problems()` committed: the gap /
conflict / missing-hotel detection is *projection* work (it belongs in `handle`),
not a filter.

Practical consequence, and why the two never blur in the code: a filter's select
criteria are **not events**, so per R4/R9 they enter at the boundary and are passed
in on read (`now`, viewer, date) — never folded from the stream. If a "filter"
needs no such caller-supplied criterion and instead recomputes from the
event-shaped state, it is projection wearing a filter's clothes — move it into
`handle`.
