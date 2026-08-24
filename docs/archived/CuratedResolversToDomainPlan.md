# Curated Resolvers Belong in Domain

Status: `shipped` — designed and built 2026-08-23, archived the same day. The rule below is now a
standing architecture rule in `CLAUDE.md`, which cites this file for the reasoning; the one tail,
the `setState` shims, is in `../Cleanup_Tasks.md` (Open).
Slice 1 of `GroundTransferEndpointReadModelPlan.md` is now unblocked: a `Place` value in
`domain` can take an `AirportCityResolver`. (That plan shipped in full the same day.)

## The rule this is applying

A type belongs in `domain` when it states a fact or a rule about travel and depends on **none** of
the following:

- **I/O** — files, sockets, the database, HTTP, or a serialization library. (Persistence and
  wire formats live in `infrastructure`.)
- **A framework** — no Spring, no annotations from one.
- **The clock** — no `Clock`, and none of the ambient `now()` calls `NoAmbientClockReadsTest`
  already bans. A decision that depends on *when* it runs receives `now` from the boundary.
- **Randomness** — no `Random`, no `Math.random()`.
- **Identifier generation** — no `UUID.randomUUID()`. Ids are minted at the boundary and passed
  inward, for the same reason as `now`: a value the domain invents is a value a test cannot fix.
  **Standing exception (Ted, 2026-08-23): the seven `*Id.random()` factories stay.** They are
  test-only (see finding 2), production already mints at the boundary, and no change is planned.
- **Presentation** — no display strings (`cityCountry()`, `asLabel()`), per the standing rule.

A curated in-memory table is **data**, not I/O. Being an interface with a future non-static
implementation in mind does not make it application code either: that is ordinary ports and
adapters — the interface is the domain's, and an I/O-backed implementation, if one ever exists,
is the adapter in `infrastructure`.

## Problem

Three curated tables sit in `application` for no reason the code gives (Ted, 2026-08-23):

| Type | What it is | Non-`java.*` imports |
|---|---|---|
| `AirportCityResolver` + `StaticAirportCityResolver` | airport code → city, a `Map.ofEntries` literal | `domain` only |
| `AirportZoneResolver` | airport code → IANA zone | `domain` only |
| `LocationZoneResolver` | address / city+country → IANA zone | `domain` only |
| `ZoneResolutionException` | thrown by both zone resolvers | `domain` only |

Every one passes the checklist above. They state facts about the world, they hold no state, and
they touch nothing outside `java.*` and `domain`.

**The concrete cost** is in `GroundTransferEndpointReadModelPlan.md` D1: a `Place` value in `domain`
cannot take an `AirportCityResolver` that lives in `application`, so the airport arm of the
derivation has to be written outside `Place` — the one kind left deriving in two places, in a plan
whose whole point is that the derivation is written once.

## What shipped

The four types above moved to `dev.ted.jittertravel.domain`. Imports updated across 86 files;
nothing else changed. `ZoneResolutionException` moved with them — a domain type must not throw an
application exception, and it is referenced from 22 files, so leaving it behind was the worse of the
two sweeps.

`CityCountry` stayed in `application`: neither resolver uses it (its users are `LocationZoneAudit`,
`LocationAuditProjector` and `SessionizePrefillService`), and it names the resolver only in prose.

The Spring `@Bean` methods in `EventSourcingConfig` are unaffected — infrastructure constructing
domain types is the normal direction. Its `application.*` wildcard no longer covered the four, so
they are now named imports.

**Three test classes moved with their subjects** — `AirportZoneResolverTest`,
`LocationZoneResolverTest`, `StaticAirportCityResolverTest` — since a test lives by what it tests,
not by where its fixtures came from.

**One javadoc claim went stale and was rewritten rather than relinked.** `CfpDeadlineMissing` said
it "lives beside `ZoneResolutionException`", which was the argument for it being application-layer;
with the exception in `domain` that sentence would have been false, so it now says it stays put
rather than moving down with it. The link is spelled fully-qualified, because
`NoFullyQualifiedClassReferencesTest` skips javadoc continuation lines — so this costs no import that
only javadoc would use.

## Two findings from the audit, neither in scope here

**2 (settled first, because it is short). Seven `*Id.random()` factories call `UUID.randomUUID()`
in domain** — `FlightId`, `TrainTripId`, `HotelBookingId`, `GroundTransferId`, `ConferenceId`,
`GatheringId`, `PrivateEventId`. They have **zero call sites in `src/main`** and 407 in tests;
production mints ids at the boundary (`PlanGroundTransferController` does
`UUID.randomUUID().toString()` itself), so the rule holds where it matters.
**Decided (Ted, 2026-08-23): allowed, deferred, no change.** Pin the real rule instead — see the
test below.

## Finding 1 — `Address` imports Jackson: **resolved 2026-08-23, by retiring the alias**

Done in two moves on the same day. First the annotation became `AddressMixIn`, a record mix-in
registered in `EventJsonMapperFactory` — which proved the mechanism worked and, in running the
certification step, produced the measurement that made the second move obvious. Then, on Ted's
call, **the alias was retired outright**: mix-in deleted, registration removed, and the samples
that encoded the old spelling moved to `region`.

Why retiring beat keeping it: **nothing in rotation carries `"state"`.** All five event-format
backups on hand and the 2026-08-24 production dump contain zero occurrences against 101 `"region"`,
and the only files that do carry it are the June-2026 **command** exports — a format today's
`BackupService` cannot read. The event-format backup itself postdates the rename, so no restorable
artifact has ever used the old spelling.

**What shipped**

- `Address` has no annotation and no import; `domain` now has **zero** non-`java.*` imports, so
  `DomainIsPureTest` needs no carve-out.
- `GoldenEventDeserializationTest` rebuilds from `EventJsonMapperFactory.create()` and then applies
  its one deliberate difference (`FAIL_ON_UNKNOWN_PROPERTIES = true`) — it had been certifying a
  configuration production does not use. Its two legacy samples keep every other legacy aspect
  (bare-scalar datetimes, absent `locationForMatching`, absent `mapsUrl`/`cancelBy`, the retired
  logical name) and simply spell the field `region`.
- New `EventJsonMapperFactoryTest` pins the two production-config facts the strict mapper cannot:
  unknown properties are **ignored**, and a pre-rename `"state"` therefore reads back as an
  **empty** region rather than failing. The second is the cost of retirement, recorded where
  someone hitting it would look.
- `LegacyEventMigrationTest`'s fixtures likewise stop advertising a shape that is no longer read.

**Verified:** 1620 unit + 61 js green. Mutation-verified across both states — with the mix-in
registered the same payload bound `region` to `"IL"`; without it the strict golden cases raise
`UnrecognizedProperty` and the lenient factory test sees an empty region, which is exactly the
loud-vs-silent asymmetry the assessment predicted. `-Preplay-preflight` green against the
2026-08-24 production dump and the oldest event-format backup on hand (2026-08-16), before and
after.

**Still true, and worth keeping in view:** `BookHotelRequest.setState` / `ChangeHotelRequest.setState`
remain, commented "backward compat for old exports" — the same retired command-export format. Not
touched here; likely the same retirement, one layer up.

### Risk assessment (written before the change)

`com.fasterxml.jackson.annotation.JsonAlias("state")` on `Address.region` is the **only**
non-`java.*` import in the entire `domain` package. The target shape is a Jackson **mixin** in
`infrastructure`, registered on the event mapper, which keeps the alias and takes the annotation out
of the domain. Ted asked for the risk before the change (2026-08-23); this is it.

### What the alias is actually holding up — more than expected

The obvious assumption is that this only matters for old backup files. It does not.
`LegacyEventMigration.plan()` writes

```java
String newPayload = payloadChanged ? upcasted.toString() : row.payloadJson();
```

— the **upcast `JsonNode`**, never a re-serialized bound object. No upcaster renames `state` →
`region`. So **legacy rows in `event_log` still literally contain `"state"` today**, even after the
production migration of 2026-08-21. The alias is load-bearing on the live **boot-replay** path, not
merely on restore of an archived backup.

Affected payloads are every event carrying an `Address`: `HotelBooked`, `HotelChanged`,
`ConferencePlanned`, `GatheringPlanned`, `GatheringChanged`, `PrivateEventPlanned`,
`GroundTransferPlanned` — plus the corresponding `command_log` entries, which ride along as opaque
history and are not bound on any current path (worth re-checking before the change, not assumed).

### The failure mode, and why it is the bad kind

A mixin that does not bind correctly does not throw a compile error and may not throw at all:
`region` simply reads back as `""` for legacy rows. Two consequences follow.

1. **`region` feeds zone resolution.** `LocationZoneResolver` resolves city → region → country, and
   region is what disambiguates multi-zone countries — US states, exactly the values stored as
   `"state"`. The `*TimeZoneUpcaster` rungs call it *on legacy payloads*, which are the same rows.
   A dropped region therefore risks a boot-replay failure (loud, recoverable) or a differently
   resolved zone (quiet, and wrong times).
2. **`region` is published.** `TransferEndpointLabel.publicLabel` emits city / region / country to
   anonymous viewers, so a silent blanking degrades a public label as well.

Whether an unmapped `"state"` throws or is ignored depends on `FAIL_ON_UNKNOWN_PROPERTIES` under
Jackson 3, which `EventJsonMapperFactory` does not set either way. **Determine that empirically
rather than assuming it** — it decides whether the failure is loud or silent.

### What makes it low risk anyway

- **One mapper, one choke point.** `EventJsonMapperFactory.create()` is the single source of truth
  for the mapper used to read and write `event_log` / `command_log`, and it is used by the
  production `JsonMapper` bean *and* by the backup, restore, upcaster and contract tests. A mixin
  registered there is in force everywhere events are bound — unlike the usual case where a mixin
  lands on one of several mappers.
- **The exact regression is already pinned by name**, twice:
  `GoldenEventDeserializationTest.hotelBookedLegacyPayloadWithStateFieldAndNoLocationForMatchingDeserializes`
  asserts `region()` is `"IL"` from a `"state"` payload, a second golden case does the same with
  `"CA"`, and `LegacyEventMigrationTest` drives legacy payloads carrying `"state"`.
- **`BootReplayPreflightTest`** restores a real production dump and drives `loadAllEvents()`, so the
  change can be certified against the actual rows before it ships.

### The one prerequisite

`GoldenEventDeserializationTest` **builds its own mapper** (`JsonMapper.builder()...` at line 61)
rather than calling `EventJsonMapperFactory.create()` — despite the factory's own doc claiming "the
serialization tests build their mapper here". So today those golden cases would not exercise a
mixin at all. Point them at the factory **first, as a separate green step**; otherwise the
strongest safety net for this change is testing something production does not use.

### Recommended sequence

1. Golden tests use `EventJsonMapperFactory.create()`. Suite green, no production change.
2. Establish empirically whether an unknown property throws under this mapper.
3. Add the mixin and remove the annotation, in one change. Mutation-verify: delete the mixin
   registration and watch both golden cases go red.
4. Run `-Preplay-preflight` against a current production dump before deploying.

Residual risk after that: **low**. The blast radius is wide but every affected path funnels through
one mapper, and the regression has named tests. Still Ted's call to start it.

### Correction, from running step 4 (2026-08-23)

The claim above that legacy rows "still literally contain `"state"` today" is **wrong**, and the
reasoning that produced it (the migration writes the upcast node, no upcaster renames the field)
was sound but untested against the data. Measured across every backup on hand:

| File | `"state"` | `"region"` |
|---|---|---|
| `…production-2026-08-24T001331Z.json` | 0 | 101 |
| `…production-2026-08-19`, `-08-17`, `-08-16`, `jittertravel-backup.json` | 0 | — |
| June-2026 **command** exports (7 files) | 15, 15, 15, 7, … | — |

So current production is entirely on `region`, and the only artifacts carrying the old spelling are
the retired **command-export** format, which today's `BackupService` cannot read. The alias is
therefore **dead compatibility code on every live path** — which lowers the risk of this change to
near zero, and raises a different question: whether to retire it outright. Keeping it costs nothing
now that it is a mix-in, and retirement belongs with the other compatibility retirements gated on
old backups leaving rotation, not with this move. **Not decided — worth an ask.**

The two preflight runs above therefore certify that the change does not break current production
data; they do **not** exercise the alias, because no such row exists any more.

**Decision (Ted, 2026-08-23): retire it.** See the section header — the mix-in was deleted the same
day it was written. It was not wasted work: writing it is what got the certification step run, and
that is what produced the measurement.

## Tests

- **`DomainIsPureTest`** — a plain source scan over `src/main/java/dev/ted/jittertravel/domain`,
  in the style of `NoAmbientClockReadsTest` (no ArchUnit). Asserts no import outside `java.*` and
  `dev.ted.jittertravel.domain`, and no `Math.random()` / `new Random`. This is the artifact that
  makes the rule above stick rather than being a paragraph someone has to remember.
  - **No exemption needed** as of 2026-08-23: `Address`'s Jackson import moved to `AddressMixIn`
    (finding 1), so the package is already clean and the test can be written with no carve-out.
  - `UUID` is **not** scanned for — allowed by standing exception (finding 2).
- **Id generation**: assert no `src/main` call site outside `domain` calls `*Id.random()`. That is
  the half of the rule that is actually live — the boundary mints ids — and it holds today without
  touching the 407 test call sites.
- Everything else is the existing suite: a pure package move must leave it green with no edits
  beyond imports.

**What was written, 2026-08-23.** `DomainIsPureTest` (`architecture` package) carries all three
methods, the id one included: the standing `UUID` exception only reads as an exception next to the
purity rule it is carved out of, and the assertion it replaces it with belongs in the same file as
the reason. The id scan turned out to be **stricter than the plan's wording** — it covers all of
`src/main`, `domain` included — because zero call sites exist there either, so there was no
exemption to write. Measured: 0 in `src/main`, 407 in tests.

The import check is written as a **whitelist of two prefixes** (`java.`, `dev.ted.jittertravel.domain.`)
rather than a blacklist of Spring and Jackson. That is what makes it cover the framework, I/O,
serialization and presentation clauses at once, and it is what makes a *new* dependency — some
library nobody has thought of — fail on arrival rather than after someone remembers to add it.

**Verified:** 1628 unit + 61 js green, on a `clean` run. (The commit message says 1623 — that came
from an incremental build taken minutes after three test classes were moved between packages, which
undercounted by five. `./mvnw -o clean test` is the number to quote after a package move; the
incremental one is not wrong so much as not counting what you think it is.) All three
mutation-verified in one run:
a Spring import on `AirportCityResolver`, a `Math.random()` field on `StaticAirportCityResolver`, and
a `FlightId.random()` field on `BookFlightHandler` each failed exactly its own method and no other.

## Not in this plan

- The `Address` Jackson annotation (finding 1) — **resolved 2026-08-23** by retiring the alias, ahead
  of the move itself; see above. Its one tail, the matching `setState` shims on `BookHotelRequest` /
  `ChangeHotelRequest`, was **lifted to `Cleanup_Tasks.md` (Open) 2026-08-23** so it stays findable
  once this doc stops being read.
- Removing `*Id.random()` (finding 2) — allowed by standing exception, deferred indefinitely.
- ~~Whether the rule statement above should be promoted into `CLAUDE.md`.~~ **Ted said yes,
  2026-08-23.** It is now the "What belongs in `domain` — and what a curated table is" section,
  placed immediately above the presentation rule, which is one of its clauses. Both readings people
  get wrong (a curated table is data; a seam interface is ports-and-adapters) went with it, because
  `DomainIsPureTest` says *what* fails and never *why* the line sits where it does.
