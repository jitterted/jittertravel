# Event Serialization Contract Tests

Exploration doc — **do not implement without discussion.**

## Goal

Guard the backward-compatibility of our serialized **events** (primary concern) and
**commands** (secondary). Today the same code both writes and reads each payload, so a
serialization change compiles and passes tests cleanly — then breaks in production when
older stored events (or a previously-exported backup) are read by newer code. We want
golden-file + snapshot tests that fail in the build when an event's on-the-wire JSON drifts.

This is the actionable form of the long-standing "event serialization contract tests" TODO.
Related guard already in place: backup JSON backward-compat is a hard constraint
(see export/import compatibility notes). Round-trip coverage lives in
`CommandExportImportRoundTripTest`.

## Tool under evaluation: Strictland

JVM contract-testing library for message serialization (events, commands, queue messages,
HTTP payloads). Validates that serialized formats stay stable and compatible across versions.

- **Repo / docs:** https://github.com/event-driven-io/strictland
- **Coordinates:** `io.event-driven:strictland` (Maven Central), version `0.3.0`, `test` scope
- **Requires:** JDK 21+ (we're on JDK 26 — fine)

### How it works

Two check types, written as ordinary unit tests with a fluent "given / when / then" API:

1. **Snapshot checks** — assert a message serializes byte-for-byte to an approved baseline
   file. Catches accidental format changes.
   ```java
   MessageContract.specification(Json.Jackson.defaults())
       .given(new OrderPlaced(orderId, "Alice", placedAt))
       .whenSerialized()
       .thenContractIsUnchanged();
   ```
   First run writes `OrderPlaced.approved.txt` next to the test; you review and commit it.
   Later runs diff against it and fail on drift.

2. **Compatibility checks** — verify version evolution against an older serialized form.
   ```java
   // new type reads old data
   MessageContract.specification(Json.Jackson.of(yourObjectMapper))
       .given(new OrderPlaced(orderId, "Alice"))
       .whenDeserializedAs(OrderPlacedWithCoupon.class)
       .thenBackwardCompatible(order -> assertNull(order.couponCode()));

   // old type reads new data
   MessageContract.specification(Json.Jackson.of(yourObjectMapper))
       .given(new OrderPlacedWithCoupon(orderId, "Alice", "SAVE10"))
       .whenDeserializedAs(OrderPlaced.class)
       .thenForwardCompatible();
   ```

### Key facts

- **Snapshot files:** JSON baselines named after the message type (`OrderPlaced.approved.txt`),
  stored next to the test and committed — reviewed in normal diffs. Custom name via
  `.whenSerialized(Snapshot.forMessageType("InvoiceIssuedEvent"))`.
- **Mapper config:** `Json.Jackson.of(yourObjectMapper)` validates against *your* production
  mapper; `Json.Jackson.defaults()` gives ISO-8601 dates, null retention, unknown-property
  tolerance. Custom serializers supported for non-Jackson formats.
- **Workflow:** generate → review → commit the approved file; future drift fails in CI/code
  review before it ships. Lightweight — no live services or brokers (unlike consumer-driven
  contract testing).

## Spike results (2026-06-16)

**Verdict: works for us, via a one-class adapter. Viable to adopt.**

- **Jackson mismatch confirmed.** Strictland 0.3.0 bundles **Jackson 2**
  (`com.fasterxml.jackson.core:jackson-databind:2.21.4`, per `mvn dependency:tree`), and
  `Json.Jackson.of(...)` takes a `com.fasterxml.jackson.databind.ObjectMapper`. Our app
  serializes events with **Jackson 3** (`tools.jackson…JsonMapper`). So we **cannot** hand our
  production mapper to `Json.Jackson.of(...)` — the types don't line up.
- **Escape hatch used.** Strictland exposes a serializer-agnostic seam:
  `SpecificationOptions.serializer(MessageSerializer)` where `MessageSerializer` is just
  `byte[] serialize(Object)` + `<T> T deserialize(byte[], Class<T>)`. We implemented a
  ~15-line adapter delegating to a Jackson-3 `JsonMapper`
  (`src/test/java/dev/ted/jittertravel/contract/JsonMapperMessageSerializer.java`). This routes
  Strictland through our real mapper, so snapshots are the exact bytes we'd persist.
- **Proof.** `ConferenceCancelledContractTest` (same package) runs two checks against
  `ConferenceCancelled`:
  - `thenContractIsUnchanged()` — snapshot, approved file `ConferenceCancelled.approved.txt`
    generated next to the test:
    `{"conferenceId":{"id":"22222222-…"},"reason":"Venue double-booked"}` — matches the
    shape the golden deserialization test already expects.
  - `thenBackwardCompatible(...)` — round-trips through the real mapper.
  Both pass. Verified the guard is **not** a no-op: editing the approved file to drift the
  payload fails the build as expected.
- **Plain unit tests.** No Spring context / DB / auth needed — same tier as
  `GoldenEventDeserializationTest`.

### Relationship to existing tests

`GoldenEventDeserializationTest` already covers the **read** side (old JSON → current type, with
`FAIL_ON_UNKNOWN_PROPERTIES`). Strictland adds the **write** side (current type → approved bytes)
that we don't have today, plus first-class forward/backward-compat helpers. They're complementary,
not redundant.

## Pinned mapper config (2026-06-16)

**Done.** The mapper is now pinned to a single source of truth shared by production and tests, so
snapshots/contracts can't drift from what we persist:

- `EventJsonMapperFactory.create()` (in `infrastructure`) is the one place the event/command
  `JsonMapper` is built.
- `EventSourcingConfig` now declares an explicit `@Bean JsonMapper` from the factory, replacing
  reliance on Spring Boot's auto-configured mapper (this also pins us against a framework-default
  change silently altering stored-event format).
- `EventJsonMapperEquivalenceTest` **proves** the factory serializes byte-for-byte identically to
  the previously auto-configured mapper (imports the real `JacksonAutoConfiguration`; covers
  dates, nested records, empty strings, booleans). This is the backward-compat safety net.
- `ConferenceCancelledContractTest` and `PostgresPersisterTest` both build their mapper from the
  factory. `GoldenEventDeserializationTest` intentionally stays separate — it uses a *stricter*
  mapper (`FAIL_ON_UNKNOWN_PROPERTIES=true`).
- Verified the full Spring context still starts (the `JsonMapper` bean is app-wide, also used by
  web MVC) and `CommandExportImportRoundTripTest` still passes.

## Open questions / remaining work

- **Keep the dependency?** Adapter means we use only Strictland's harness, not its Jackson layer.
  Re-confirm the value over extending the existing hand-rolled golden tests before committing.
- **Scope.** Events first (one snapshot per `Event` subtype). Commands stay with
  `CommandExportImportRoundTripTest` unless we want snapshots there too.
- **Coverage enforcement.** How to ensure a new event type gets a snapshot (parallel to the
  "every new command needs a round-trip case" rule).

## Spike artifacts (remove if we don't adopt Strictland)

- `pom.xml` — `io.event-driven:strictland:0.3.0`, `test` scope (marked SPIKE).
- `src/test/java/dev/ted/jittertravel/contract/JsonMapperMessageSerializer.java`
- `src/test/java/dev/ted/jittertravel/contract/ConferenceCancelledContractTest.java`
- `src/test/java/dev/ted/jittertravel/contract/ConferenceCancelled.approved.txt`

Note: the pinned-mapper work below is **independently valuable** and would stay even if we drop
Strictland: `EventJsonMapperFactory`, the `@Bean JsonMapper` in `EventSourcingConfig`, and
`EventJsonMapperEquivalenceTest`.

## Next steps

- [x] Spike: add Strictland, snapshot one event via our Jackson-3 mapper; confirm compatibility.
- [x] Pin a single shared mapper config (`EventJsonMapperFactory`) used by prod + tests, proven
      equivalent to the auto-configured mapper.
- [ ] Decide: adopt Strictland or revert the spike artifacts (mapper pin stays either way).
- [ ] If adopting: pick the first events to cover and define snapshot-file location/naming.
- [ ] If adopting: decide the coverage-enforcement mechanism for new event types.