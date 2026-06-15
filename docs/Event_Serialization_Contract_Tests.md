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

## Open questions for our codebase

- **Jackson version.** We serialize events with **Jackson 3** (`tools.jackson.*` —
  `JsonMapper` in `PostgresPersister`), not the legacy `com.fasterxml.jackson` line. Confirm
  whether Strictland 0.3.0's `Json.Jackson.of(...)` accepts a Jackson-3 mapper, or whether it
  binds the old API. **This is the gating compatibility check before adopting.**
- **Use our real mapper.** Tests must run against the same `JsonMapper` bean used by
  `PostgresPersister` so snapshots reflect the exact JSONB we store, not a default.
- **Scope.** Events are the primary target (one snapshot per `Event` subtype). Decide whether
  to also snapshot command DTOs (secondary) or leave those to
  `CommandExportImportRoundTripTest`.
- **Coverage enforcement.** How do we ensure a newly-added event type gets a snapshot test?
  (Parallel to the existing "every new command needs a round-trip case" rule.)
- **Test tier.** Plain JVM unit tests, no Spring context / DB needed — decide tag/profile and
  where they live relative to existing serialization tests.

## Next steps

- [ ] Spike: add Strictland in `test` scope, write one snapshot check for a single event using
      our production `JsonMapper`; confirm Jackson-3 compatibility.
- [ ] If it works, pick the first events to cover and define the snapshot-file location/naming.
- [ ] Decide the coverage-enforcement mechanism for new event types.