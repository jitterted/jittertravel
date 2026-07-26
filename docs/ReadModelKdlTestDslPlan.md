# KDL Read Model Test DSL Plan

This document sketches the test-support direction for the KDL-based read model
definition language. The goal is to keep the KDL itself locally readable while
making Java test setup and assertions much smaller.

## Current decisions

- Keep the read-model acceptance test focused on the behavior:
  `changedEventsUpdateExistingReadModelRows`.
- Use read-model row `id` and `type` to find projected rows. Do not locate rows
  by mutable projected fields such as `startLocation`.
- Use deterministic time in every test, even when the current time should not
  affect the behavior.
- Preserve the compact KDL id namespace form:

  ```kdl
  id (train)"tripId"
  ```

- Do not introduce KDL reuse/DRY constructs yet. Repeated event blocks are
  intentionally easier to scan than indirection.
- Add a namespace/collision test: two different row types may have the same raw
  id value and must still project to separate rows.

## Target test shape

The KDL should remain the main thing the reader sees. Event construction should
read like a short business script:

```java
@Test
void changedEventsUpdateExistingReadModelRows() {
    ReadModelDefinition definition = readModel("""
        readmodel "TravelView" title="Upcoming Travel"
        values "startLocation" "startDateTime" "endLocation" "endDateTime"

        events {
            TrainBooked {
                id (train)"tripId"
                startLocation "departureStation.name"
                startDateTime "departureDateTime.localTime"
                endLocation "arrivalStation.name"
                endDateTime "arrivalDateTime.localTime"
            }
            TrainChanged {
                id (train)"tripId"
                startLocation "departureStation.name"
                startDateTime "departureDateTime.localTime"
                endLocation "arrivalStation.name"
                endDateTime "arrivalDateTime.localTime"
            }
            FlightBooked {
                id (flight)"flightId"
                startLocation "departureAirport.code"
                startDateTime "departureDateTime.localTime"
                endLocation "arrivalAirport.code"
                endDateTime "arrivalDateTime.localTime"
            }
            FlightChanged {
                id (flight)"flightId"
                startLocation "departureAirport.code"
                startDateTime "departureDateTime.localTime"
                endLocation "arrivalAirport.code"
                endDateTime "arrivalDateTime.localTime"
            }
        }
        """);

    KdlProjector projector = new KdlProjector(definition);

    projector.handle(events(
            trainBooked("T-123"),
            flightBooked("F-999").from("JFK").to("LHR"),
            trainChanged("T-123").departingAt(t("jan 1 11:00")).arrivingAt(t("jan 1 14:00")),
            flightChanged("F-999").from("EWR")
    ));

    DynamicViews views = projectedViews(projector, TimeView.ALL, now());

    assertThat(views)
            .hasSize(2);

    assertThat(views.row("train", "T-123"))
            .has("startLocation", "Penn Station")
            .has("startDateTime", dt("jan 1 11:00"))
            .has("endLocation", "Union Station")
            .has("endDateTime", dt("jan 1 14:00"));

    assertThat(views.row("flight", "F-999"))
            .has("startLocation", "EWR")
            .has("endLocation", "LHR");
}
```

This is illustrative, not a final API. The important properties are:

- defaults carry uninteresting setup;
- overridden values are highly visible;
- row lookup uses row type/id;
- date/time syntax is compact and deterministic.

## Event creation DSL

### Principles

- Prefer typed builders over a generic stringly event builder.
- Use domain language at the call site: `from`, `to`, `departingAt`,
  `arrivingAt`, `service`, `airline`, `number`, `because`.
- Give every event builder safe defaults so a test can override only the fields
  relevant to the behavior.
- Keep the builders in test-support code, not production code.
- Return real domain events. The DSL should reduce setup noise, not become a
  parallel event model.

### Event stream helpers

```java
private static Stream<StoredEvent> events(EventBuilder<?>... builders) {
    return Arrays.stream(builders).map(EventBuilder::stored);
}

private interface EventBuilder<E extends Event> {
    E event();

    default StoredEvent stored() {
        return stored(event());
    }
}
```

For tests that do not need event-store metadata, `stored(event)` can use a
minimal deterministic `StoredEvent`, or a local `DummyStoredEvent` if the KDL
projector only needs `payload`.

### Train defaults

```java
private static TrainBookedBuilder trainBooked(String id) {
    return new TrainBookedBuilder(id);
}

private static TrainChangedBuilder trainChanged(String id) {
    return new TrainChangedBuilder(id);
}
```

Suggested default train:

- id: supplied by the test
- departure station: Penn Station, New York, USA
- departure time: January 1, 2026 at 09:00
- arrival station: Union Station, DC, USA
- arrival time: January 1, 2026 at 12:00
- service id: Acela

### Flight defaults

```java
private static FlightBookedBuilder flightBooked(String id) {
    return new FlightBookedBuilder(id);
}

private static FlightChangedBuilder flightChanged(String id) {
    return new FlightChangedBuilder(id);
}
```

Suggested default flight:

- id: supplied by the test
- airline: Delta
- flight number: DL1
- departure airport: JFK
- departure time: February 1, 2026 at 18:00
- arrival airport: LHR
- arrival time: February 2, 2026 at 06:00
- change reason: Weather

### Builder API examples

```java
trainChanged("T-123")
        .departingAt(t("jan 1 11:00"))
        .arrivingAt(t("jan 1 14:00"));

flightChanged("F-999")
        .from("EWR");

flightBooked("F-999")
        .from("SFO")
        .to("JFK")
        .departingAt(t("may 17 09:00"))
        .arrivingAt(t("may 17 14:00"));
```

## Short date/time helpers

Date/time construction is the noisiest part of the current tests. The helper
should optimize for compact, readable test literals.

### Recommended baseline

Use one fixed default year and one default zone per test fixture class:

```java
private static final int YEAR = 2026;
private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");
private static final Instant FIXED_NOW = instant("may 16 10:00");
```

Provide two helpers:

```java
private static LocalDateTime dt(String value) {
    return TestDates.localDateTime(YEAR, value);
}

private static ZonedTimestamp t(String value) {
    return ZonedTimestamp.fromLocal(dt(value), DEFAULT_ZONE);
}
```

Examples:

```java
t("jan 1 09:00")
t("feb 2 06:00")
t("may 17 14:30")
```

This is shorter than `LocalDateTime.of(...)`, but still obvious enough that the
test reader can parse it without learning a large mini-language.

### Accepted date/time literal format

Start small:

```text
jan 1 09:00
feb 2 06:00
may 17 14:30
```

Implementation can use `DateTimeFormatterBuilder` with case-insensitive short
month names and the fixture's default year.

Avoid supporting too many aliases initially. More accepted forms make tests
look flexible, but they also make failures and refactors more surprising.

### Optional relative helpers

Relative helpers are useful in command validation tests:

```java
nowAt("jun 2 10:00")
afterNow(days(7), at("09:00"))
beforeNow(hours(1))
```

However, for read-model projection tests, absolute literals are usually better.
They make expected values stable and visible.

## Projection assertion DSL

The main assertion improvement is to make row identity first-class in tests.

```java
assertThat(views.row("train", "T-123"))
        .has("startDateTime", dt("jan 1 11:00"));
```

Potential support types:

```java
final class DynamicViews {
    private final List<DynamicView> rows;

    DynamicView row(String type, String rawId) {
        return rows.stream()
                .filter(row -> row.type().equals(type))
                .filter(row -> row.id().equals(rawId))
                .findFirst()
                .orElseThrow();
    }
}

final class DynamicViewAssert extends AbstractAssert<DynamicViewAssert, DynamicView> {
    DynamicViewAssert has(String field, Object expected) {
        assertThat(actual.data().get(field)).isEqualTo(expected);
        return this;
    }
}
```

The exact identity API depends on `DynamicView`, but tests should use whatever
public row identity the projection layer exposes.

Also add negative assertions for stale data:

```java
assertThat(views)
        .doesNotContainValue("startLocation", "JFK")
        .doesNotContainValue("startDateTime", dt("jan 1 09:00"));
```

These assertions directly express that changed events update existing rows
instead of adding new rows.

## Namespace behavior test

Add a focused test for id namespaces:

```java
@Test
void sameRawIdInDifferentNamespacesProducesDifferentRows() {
    KdlProjector projector = new KdlProjector(travelViewDefinition());

    projector.handle(events(
            trainBooked("123"),
            flightBooked("123")
    ));

    DynamicViews views = projectedViews(projector, TimeView.ALL, now());

    assertThat(views.row("train", "123"))
            .has("startLocation", "Penn Station");

    assertThat(views.row("flight", "123"))
            .has("startLocation", "JFK");
}
```

This protects the intended meaning of:

```kdl
id (train)"tripId"
id (flight)"flightId"
```

Even if current production IDs are UUIDs, this behavior should not depend on
UUID uniqueness forever.

## Outcome-event assertion helpers

Several command tests currently follow this pattern:

```java
List<FlightBooked> events = command.execute(context).toList();

assertThat(events).hasSize(1);
FlightBooked event = events.getFirst();
assertThat(event.airline()).isEqualTo("United");
assertThat(event.flightNumber()).isEqualTo("UA100");
```

This is clear, but repetitive. A small assertion helper can keep the clarity
while removing the ceremony.

### Option A: typed single-event extraction

```java
FlightBooked event = assertOneEvent(
        command.execute(context),
        FlightBooked.class
);

assertThat(event)
        .returns("United", FlightBooked::airline)
        .returns("UA100", FlightBooked::flightNumber)
        .returns(AirportCode.of("SFO"), FlightBooked::departureAirport);
```

Helper:

```java
private static <E> E assertOneEvent(Stream<? extends Event> stream, Class<E> type) {
    List<? extends Event> events = stream.toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(type);
    return type.cast(events.getFirst());
}
```

This is the lowest-friction improvement and keeps normal AssertJ usage.

### Option B: event list assertion DSL

```java
assertThatEvents(command.execute(context))
        .single(FlightBooked.class)
        .has(FlightBooked::airline, "United")
        .has(FlightBooked::flightNumber, "UA100")
        .has(FlightBooked::departureAirport, AirportCode.of("SFO"))
        .has(FlightBooked::arrivalAirport, AirportCode.of("JFK"));
```

This is compact, but it introduces a custom assertion style. Use this only if
many tests benefit from it.

### Option C: expected event object

```java
assertThat(command.execute(context).toList())
        .containsExactly(new FlightBooked(
                flightId,
                "United",
                "UA100",
                AirportCode.of("SFO"),
                departure,
                AirportCode.of("JFK"),
                arrival
        ));
```

This is excellent when the event has few fields or when a builder makes the
expected event readable:

```java
assertThat(command.execute(context).toList())
        .containsExactly(flightBookedEvent(flightId)
                .from("SFO")
                .to("JFK")
                .departingAt(t("may 17 09:00"))
                .arrivingAt(t("may 17 14:00"))
                .event());
```

The downside is that a full event object can obscure which fields are actually
important to the behavior.

### Recommended starting point

Start with Option A:

```java
FlightBooked event = assertOneEvent(command.execute(context), FlightBooked.class);
```

Then use AssertJ `returns(...)` or ordinary field assertions. It is a small
change, easy to adopt incrementally, and does not hide the domain event shape.

## Implementation steps

1. Add a test-support date helper with `dt("jan 1 09:00")`,
   `t("jan 1 09:00")`, and deterministic `now()`.
2. Add typed event builders for train and flight booked/changed events.
3. Add a tiny `events(...)` stream helper.
4. Expose or confirm read-model row identity on `DynamicView`.
5. Add projection assertion helpers around row type/id lookup.
6. Rename the acceptance test to `changedEventsUpdateExistingReadModelRows`.
7. Add `sameRawIdInDifferentNamespacesProducesDifferentRows`.
8. Add `assertOneEvent(...)` for command tests and migrate one or two tests as
   examples before broad rollout.

