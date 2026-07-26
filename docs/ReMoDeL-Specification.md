# ReMoDeL: Read Model Definition Language

ReMoDeL, short for **Read Model Definition Language**, is a KDL-based language
for defining event-sourced read models. A ReMoDeL definition describes how
domain events project into queryable rows: which events introduce rows, which
events change existing rows, which events remove rows, and which events express
non-CRUD domain transitions.

The language is intentionally explicit. Repetition inside event mappings is
acceptable when it makes each event's projection behavior easy to see locally.

## Introduction

A read model is a purpose-built view of past domain events. It is not the source
of truth. It is a projection: a convenient shape for rendering screens,
answering queries, exporting reports, or integrating with another system.

ReMoDeL focuses on the relationship between events and rows:

- an event can create or upsert a row;
- an event can update, modify, enrich, correct, or otherwise reshape a row;
- an event can remove a row from the read model;
- an event can be ignored by this read model;
- an event can increment or decrement a count;
- an event can increase or decrease an amount using a named measurement;
- an event can express a domain transition that is not naturally CRUD-shaped.

For example:

```kdl
readmodel "TravelView" title="Upcoming Travel"

values "status" "startLocation" "startDateTime" "endLocation" "endDateTime" "note"

events {
    TrainBooked {
        id "tripId" namespace="train"
        put {
            status (literal)"booked"
            startLocation "departureStation.name"
            startDateTime "departureDateTime.localTime"
            endLocation "arrivalStation.name"
            endDateTime "arrivalDateTime.localTime"
        }
    }

    TrainChanged {
        id "tripId" namespace="train"
        put {
            status (literal)"changed"
            startLocation "departureStation.name"
            startDateTime "departureDateTime.localTime"
            endLocation "arrivalStation.name"
            endDateTime "arrivalDateTime.localTime"
        }
    }

    TrainDelayed {
        id "tripId" namespace="train"
        put {
            status (literal)"delayed"
            startDateTime "newDepartureDateTime.localTime"
            endDateTime "newArrivalDateTime.localTime"
            note "reason"
        }
    }

    TrainCancelled {
        id "tripId" namespace="train"
        remove
    }

    DifferentCityConflictDetected {
        id "conflictId" namespace="conflict"
        put {
            status (literal)"open"
            startLocation "expectedCity"
            endLocation "actualCity"
            note "message"
        }
    }

    DifferentCityConflictCleared {
        id "conflictId" namespace="conflict"
        remove
    }
}
```

This example includes three broad categories:

- `TrainBooked` introduces a train row.
- `TrainChanged` and `TrainDelayed` modify the existing train row.
- `TrainCancelled` removes the train row.
- The conflict events show a non-CRUD domain concept. A conflict is detected,
  represented in the read model, and later cleared. The language does not
  require the domain to name events as create/update/delete.

ReMoDeL can also define singleton summary read models. When an event mapping
does not declare an `id`, the event affects the read model's default row:

```kdl
readmodel "TravelStats" title="Travel Stats"

values "gatheringsSpokenAt" "hotelDays"

events {
    GatheringPlanned {
        increment "gatheringsSpokenAt" when="speaking"
    }

    HotelBooked {
        increaseBy "hotelDays" measurement="daysBetween" from="checkInDate" to="checkOutDate"
    }

    HotelCancelled {
        decreaseBy "hotelDays" measurement="daysBetween" from="checkInDate" to="checkOutDate"
    }
}
```

In this example, `GatheringPlanned` increments a count only when the event's
`speaking` property is true. Hotel events contribute measured durations to the
same default stats row. The language still avoids arbitrary arithmetic: the
allowed changes are named projection operations with bounded semantics.

## Design goals

ReMoDeL should:

- make read-model behavior visible in one place;
- preserve event-sourced language by mapping domain events, not table commands;
- support multiple event types contributing to the same row;
- support multiple row types in the same read model;
- make row identity explicit through event data;
- support singleton summary read models without requiring a synthetic id;
- support simple count and measured-total projections without becoming a
  scripting language;
- allow non-CRUD domain events to participate naturally;
- remain deterministic when replaying the same event stream;
- produce useful errors for unknown event types, invalid paths, invalid ids, and
  incompatible values.

ReMoDeL should not:

- replace domain code or command handling;
- mutate the original event stream;
- require domain events to be named after CRUD operations;
- hide important behavior behind excessive reuse or indirection;
- become a general-purpose programming language.

## Basic structure

A ReMoDeL file defines one read model:

```kdl
readmodel "TravelView" title="Upcoming Travel"
values "startLocation" "startDateTime" "endLocation" "endDateTime"

events {
    EventTypeName {
        id "path.to.id" namespace="namespace"
        put {
            fieldName "path.to.value"
        }
    }
}
```

Top-level nodes:

- `readmodel`: names the read model. The first argument is the stable programmatic
  name. Optional properties describe display metadata.
- `values`: declares the fields that rows in this read model may expose.
- `events`: contains event mappings.

## Rows, identity, and namespaces

An event mapping may declare an explicit row identity:

```kdl
id "tripId" namespace="train"
```

The string argument is a path expression evaluated against the event payload.
The `namespace` property identifies the kind of row being projected.

The pair of namespace and raw id identifies the row:

```text
(namespace, rawId)
```

Examples:

```kdl
id "tripId" namespace="train"
id "flightId" namespace="flight"
id "conflictId" namespace="conflict"
id "gatheringId" namespace="gathering"
```

Two different namespaces may use the same raw id without colliding:

```text
(train, 123)
(flight, 123)
```

These are different rows.

If an event mapping does not declare `id`, the event affects the read model's
default row. This supports singleton summary models without requiring a
synthetic id in every event mapping:

```kdl
readmodel "TravelStats"

values "gatheringsSpokenAt"

events {
    GatheringPlanned {
        increment "gatheringsSpokenAt" when="speaking"
    }
}
```

The default row identity is internal and stable. It is scoped to the read model.

A ReMoDeL definition should generally choose either singleton projection or
identified rows. Mixing both is allowed when the read model intentionally
contains multiple row kinds, but it should be used sparingly because it can make
the read model harder to reason about.

## Event mappings

Each child of `events` maps one domain event type:

```kdl
events {
    FlightBooked {
        id "flightId" namespace="flight"
        put {
            startLocation "departureAirport.code"
            endLocation "arrivalAirport.code"
        }
    }
}
```

The event node name should match the event payload type name known to the
projection runtime.

An event mapping consists of:

- an optional `id` node;
- one or more projection operations.

The initial operations are:

- `put`
- `remove`
- `ignore`
- `increment`
- `decrement`
- `increaseBy`
- `decreaseBy`

## Target row

Most projection operations affect a target row.

If the event mapping declares `id`, the target row is identified by the pair of
`namespace` and raw id value:

```kdl
id "flightId" namespace="flight"
```

If the event mapping does not declare `id`, the target row is the read model's
default row.

## `put`: create, replace, update, or enrich a row

`put` writes fields onto the target row.

```kdl
TrainChanged {
    id "tripId" namespace="train"
    put {
        startLocation "departureStation.name"
        startDateTime "departureDateTime.localTime"
        endLocation "arrivalStation.name"
        endDateTime "arrivalDateTime.localTime"
    }
}
```

If the row does not already exist, `put` creates it.

If the row already exists, `put` updates the listed fields. Fields not listed in
the `put` block are left unchanged.

This means `put` supports several domain meanings:

- booking or registering a thing;
- correcting a detail;
- delaying, rerouting, renaming, or rescheduling a thing;
- enriching a row with information that arrived later;
- reopening, resolving, or changing status;
- translating a non-CRUD event into read-model state.

The language deliberately uses `put` instead of `create` or `update` because
domain events do not always map cleanly to CRUD categories.

### Full-row and partial-row puts

A `put` may provide every field needed to render a row:

```kdl
FlightBooked {
    id "flightId" namespace="flight"
    put {
        status (literal)"booked"
        startLocation "departureAirport.code"
        startDateTime "departureDateTime.localTime"
        endLocation "arrivalAirport.code"
        endDateTime "arrivalDateTime.localTime"
    }
}
```

A `put` may also provide only the fields changed by the event:

```kdl
FlightDelayed {
    id "flightId" namespace="flight"
    put {
        status (literal)"delayed"
        startDateTime "newDepartureDateTime.localTime"
        endDateTime "newArrivalDateTime.localTime"
    }
}
```

The projection runtime must define how to handle partial rows that are queried
before they have all display-required values. Recommended behavior is:

- allow partial rows internally;
- let the read model declare required fields in a later version;
- fail loudly if rendering assumes a missing field.

## `remove`: remove a row from the read model

`remove` deletes the target row:

```kdl
TrainCancelled {
    id "tripId" namespace="train"
    remove
}
```

`remove` does not mean the domain object was deleted from history. It only means
this read model should no longer include that row.

Examples:

- a booking was cancelled;
- a conflict was cleared;
- a task was completed and should leave an "open work" view;
- a user withdrew from a waiting list;
- a shipment was delivered and should leave an "in transit" view.

If a `remove` event references a row that does not exist, the recommended
behavior is idempotent no-op. Replaying an event stream should be safe.

`remove` should be the only projection operation in its event mapping.

## `ignore`: document intentional irrelevance

Some events are known to the domain but irrelevant to a particular read model:

```kdl
PaymentMethodUpdated {
    ignore
}
```

`ignore` is optional. A runtime may simply ignore unmapped events. However,
explicit `ignore` mappings can be useful when a read model wants to document
that an event was considered and intentionally excluded.

An `ignore` mapping does not require an `id`.

`ignore` should be the only projection operation in its event mapping.

## `increment` and `decrement`: change a count by one

`increment` increases a count field on the target row by one:

```kdl
GatheringPlanned {
    increment "gatheringsPlanned"
}
```

`decrement` decreases a count field on the target row by one:

```kdl
GatheringCancelled {
    decrement "gatheringsPlanned"
}
```

Both operations may include `when`, which is a boolean path evaluated against
the event payload:

```kdl
GatheringPlanned {
    increment "gatheringsSpokenAt" when="speaking"
}
```

The `when` property is intentionally limited. It is not a condition expression
language. It names a boolean value on the event. If the value is true, the
operation applies. If the value is false, the operation does nothing.

`increment` and `decrement` are intended for counts. They avoid numeric
expressions such as `add 1` or `subtract 1`, keeping ReMoDeL declarative rather
than script-like.

## `increaseBy` and `decreaseBy`: change an amount by a named measurement

`increaseBy` increases an amount field on the target row using a named
measurement:

```kdl
HotelBooked {
    increaseBy "hotelDays" measurement="daysBetween" from="checkInDate" to="checkOutDate"
}
```

`decreaseBy` decreases an amount field on the target row using a named
measurement:

```kdl
HotelCancelled {
    decreaseBy "hotelDays" measurement="daysBetween" from="checkInDate" to="checkOutDate"
}
```

The measurement is a fixed built-in understood by the projection runtime. It is
not arbitrary code. The first supported measurement is:

- `daysBetween`: measures the number of whole days between two date-like values.

The meaning of `daysBetween` should match the domain's convention for durations.
For hotel stays, `from="checkInDate" to="checkOutDate"` usually means nights
stayed: a check-in on Monday and check-out on Wednesday contributes 2.

Additional measurements may be added later, such as:

- `hoursBetween`
- `minutesBetween`
- `amount`

Each measurement must be explicitly specified by ReMoDeL. Definitions cannot
compose measurements, perform arithmetic, or refer to previous row values.

Like `increment`, `increaseBy` and `decreaseBy` may optionally include a
boolean-path `when` property.

## Field assignments

Inside a `put` block, each node assigns one read-model field:

```kdl
startLocation "departureStation.name"
```

The node name is the read-model field. The string argument is a path expression
against the event payload.

Field names should appear in the top-level `values` declaration:

```kdl
values "startLocation" "startDateTime" "endLocation" "endDateTime"
```

If an event mapping writes a field not listed in `values`, the compiler should
reject the definition.

## Constants

Some field values should come from the event mapping rather than the event
payload. Constants may be represented as scalar KDL values:

```kdl
TrainBooked {
    id "tripId" namespace="train"
    put {
        status (literal)"booked"
        kind (literal)"train"
        startLocation "departureStation.name"
    }
}
```

This creates one ambiguity: strings can mean either constants or paths.

The recommended initial rule is:

- a string value is a path expression by default;
- literal constants use a `literal` type annotation.

Example:

```kdl
status (literal)"booked"
kind (literal)"train"
```

If implementation starts without constants, the spec should reserve the
`(literal)` annotation for this purpose.

## Path expressions

Path expressions select values from event payloads:

```kdl
"departureStation.name"
"departureDateTime.localTime"
"arrivalAirport.code"
```

Initial path expression rules:

- path segments are separated by `.`;
- each segment maps to a Java record accessor, bean getter, or public property
  according to runtime conventions;
- paths must be deterministic and side-effect free;
- collection navigation, filtering, and arbitrary expressions are not part of
  the initial language.

If a path cannot be resolved, the compiler or runtime should report:

- read model name;
- event type;
- field name;
- invalid path;
- failing segment, if known.

## Handling non-CRUD domains

ReMoDeL must not assume that events correspond to create/update/delete.

Many useful read models represent temporary or derived concepts:

- schedule conflicts;
- policy violations;
- overdue work;
- unresolved alerts;
- customer lifecycle stages;
- fraud-review queues;
- inventory exceptions;
- trip readiness;
- pending approvals;
- operational dashboards.

In these domains, events may have names such as:

```text
DifferentCityConflictDetected
DifferentCityConflictCleared
PaymentAuthorized
PaymentCaptured
PackageScannedAtFacility
PatientRiskScoreRaised
IncidentEscalated
IncidentMitigated
```

These events can still map to read-model rows:

```kdl
IncidentEscalated {
    id "incidentId" namespace="incident"
    put {
        status (literal)"escalated"
        note "escalationReason"
    }
}

IncidentMitigated {
    id "incidentId" namespace="incident"
    put {
        status (literal)"mitigated"
        note "mitigationSummary"
    }
}

IncidentClosed {
    id "incidentId" namespace="incident"
    remove
}
```

The read model defines what should be visible now. The event stream still
preserves everything that happened.

## Ordering and replay

Projection is order-sensitive. Events are applied in event-stream order.

For a given row:

- later `put` operations overwrite earlier values for the same fields;
- later `put` operations leave unmentioned fields unchanged;
- later `increment` and `increaseBy` operations increase the current field
  value;
- later `decrement` and `decreaseBy` operations decrease the current field
  value;
- `remove` removes the row as of that point in the stream;
- a later `put`, `increment`, `decrement`, `increaseBy`, or `decreaseBy` for
  the same target row may recreate the row.

This allows event streams such as:

```text
ConflictDetected -> ConflictCleared -> ConflictDetected
```

to produce an open conflict row after the final event.

Replaying the same event stream in the same order must produce the same read
model.

## Missing rows and idempotency

Recommended behavior:

- `put` on a missing row creates a row.
- `increment` or `decrement` on a missing row creates a row and treats the
  missing count as 0.
- `increaseBy` or `decreaseBy` on a missing row creates a row and treats the
  missing amount as 0.
- `remove` on a missing row does nothing.
- `ignore` does nothing.

These rules make projections tolerant of partial streams, rebuilds, and
read-model definitions that start listening after some concept already exists.

## Error handling

The ReMoDeL compiler should reject:

- missing `readmodel`;
- missing `values`;
- missing `events`;
- event mappings with no operation, unless explicitly allowed;
- `remove` combined with other operations in the same event mapping;
- `ignore` combined with other operations in the same event mapping;
- fields in `put` that are not declared in `values`;
- fields in `increment`, `decrement`, `increaseBy`, or `decreaseBy` that are
  not declared in `values`;
- duplicate field assignments in the same `put`;
- invalid path syntax;
- invalid boolean paths in `when`;
- unknown measurements;
- unknown type annotations, except those reserved by the specification.

The ReMoDeL runtime should report:

- event type resolution failures;
- path resolution failures;
- null id values when `id` is declared;
- non-boolean `when` values;
- unsupported projected value types;
- attempts to render missing required values, if required values are introduced.

Error messages should include the read model name, event name, and relevant
field or path.

## Open design questions

These are intentionally left undecided until implementation pressure makes them
valuable:

- Should `values` support type declarations?
- Should fields be declared as required or optional?
- Should constants be supported immediately through `(literal)`?
- Should `put` be implicit when field assignments appear directly under an
  event mapping?
- Should aggregate read models support collections, minimums, maximums, or
  averages?
- Should event mappings support anything beyond boolean-path `when`?
- Should row removal support tombstones for debugging or audit views?
- Should a read model expose row `type` separately from id namespace?

## Minimal initial grammar

This is an informal grammar for the first implementation target:

```text
read-model-definition :=
    readmodel-node
    values-node
    events-node

readmodel-node :=
    readmodel STRING [properties]

values-node :=
    values STRING*

events-node :=
    events {
        event-mapping*
    }

event-mapping :=
    EVENT_TYPE_NAME {
        id-node?
        operation+
    }

id-node :=
    id STRING_PATH namespace=STRING

operation :=
    put-operation
    | remove-operation
    | ignore-operation
    | increment-operation
    | decrement-operation
    | increase-by-operation
    | decrease-by-operation

put-operation :=
    put {
        field-assignment*
    }

field-assignment :=
    FIELD_NAME STRING_PATH

remove-operation :=
    remove

ignore-operation :=
    ignore

increment-operation :=
    increment STRING_FIELD [when=STRING_PATH]

decrement-operation :=
    decrement STRING_FIELD [when=STRING_PATH]

increase-by-operation :=
    increaseBy STRING_FIELD measurement=MEASUREMENT_NAME from=STRING_PATH to=STRING_PATH [when=STRING_PATH]

decrease-by-operation :=
    decreaseBy STRING_FIELD measurement=MEASUREMENT_NAME from=STRING_PATH to=STRING_PATH [when=STRING_PATH]
```

## Compatibility note

Early examples may use field assignments directly under the event node:

```kdl
TrainBooked {
    id "tripId" namespace="train"
    startLocation "departureStation.name"
    startDateTime "departureDateTime.localTime"
}
```

That compact form can remain supported as shorthand for:

```kdl
TrainBooked {
    id "tripId" namespace="train"
    put {
        startLocation "departureStation.name"
        startDateTime "departureDateTime.localTime"
    }
}
```

The explicit `put` form is preferred in this specification because it sits
cleanly alongside `remove` and `ignore`.
