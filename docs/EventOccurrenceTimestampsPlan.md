# Event Occurrence Timestamps — every event carries when it was recorded

Status: **open** — found 2026-08-27 while extracting the Book Flight event-model chapter, nothing
built. **Re-planned 2026-11-15** against the code as it now stands: the scope widened from two
flight events to every event that lacks an occurrence time (20 of 34), and the field name changed
from `recordedAt` to the `*On` form the tree already uses. What prompted it: Ted asked "how often
do I cancel a hotel within 3 days of booking it?", and no payload can answer it.

## The defect

`BookedFlightsProjector` builds the inline change history from `storedEvent.timestamp()` — the
**event-store envelope's `recorded_at`** — and renders it as a domain fact:

```java
case FlightBooked event  -> apply(…, bookingEntry(storedEvent.timestamp()));   // :47
case FlightChanged event -> apply(…, changeEntry(storedEvent.timestamp(), …)); // :52
```

```
▸ Booked on 2026-08-14 3:12PM
▸ Seat change (changed on 2026-08-20 9:04AM)
```

Two things are wrong, and the second is the one that bites.

**1. Provenance.** Per Verraes, [Multi-temporal
Events](https://verraes.net/2022/03/multi-temporal-events/): `recorded_at` is *metadata* — the
moment the row was written, there for chronological ordering and nothing else. A time the domain
means, and a user reads, is an **occurrence time**: a payload property, named for its purpose in the
domain, filled by the producer, never defaulted to `now()`. Reading the envelope for a displayed
value couples the read model to infrastructure and makes it untestable at a chosen instant — the
envelope stamp is set deep inside `PostgresPersister`, where no test can pin it, while a payload
field is captured from the injected `Clock` at command time and a `Clock.fixed(…)` pins it exactly.

**2. The label lies.** JitterTravel records things that already happened outside the app — the
flight was booked with the airline days before Ted typed it in. `"Booked on 2026-08-14"` is
*data-entry time* wearing a *booking-time* label. This is Verraes's own `reported_at` vs
`report_entered_at` distinction. Moving the value into the payload fixes provenance and leaves the
lie intact.

**Decided (Ted, 2026-08-27): the value is when Ted recorded it, and the UI says "Recorded on".** No
new form field. When Ted entered a booking is a real and useful fact; claiming it is when he booked
is not. A true user-supplied booking time was considered and rejected — it is one more field on
every booking, usually guessed, and a recording surface should not grow data entry it does not
need.

**What that means for the question that re-opened this.** "Cancelled within 3 days of booking" will
be answered in *recording* time on both ends, so it measures the gap between two data entries.
Where Ted records promptly that is the same thing; where an entry lags, it is not. Known and
accepted, not a reason to revisit the 2026-08-27 decision.

**Contained.** `BookedFlightsProjector:47,52` are still the **only** display-side envelope reads in
`src/main` (re-checked 2026-11-15). `BackupService`'s reads of `timestamp()` (`:235–251`) are
correct and stay — event-oriented backup writes every `event_log` row verbatim, envelope included,
which is exactly what an envelope is for.

## What changed between 2026-08-27 and 2026-11-15

- **The convention arrived without the plan.** Thirteen events now carry an occurrence time,
  all as `Instant …On`: `FlightCancelled.cancelledOn`, `FlightItineraryCancelled.cancelledOn`,
  `FlightItineraryChanged.changedOn`, `FlightItineraryRebooked.rebookedOn`,
  `ConferenceAttendanceConfirmed.confirmedOn`, `ConferenceAttendanceDeclined.declinedOn`,
  `TalkSubmitted.submittedOn`, `TalkAccepted.decidedOn`, `TalkRejected.decidedOn`,
  `TalkWithdrawn.withdrawnOn`, `InvitedToSpeak.invitedOn`, `OneOffTaskCompleted.completedOn`, and
  `FamilyNotified.notifiedAt` (the one `…At`). Each was born with its field, so none needed a
  backfill — which is why the blocker below is still unsolved.
- **It was not applied consistently**, so "new events have one" is not a rule the tree keeps:
  `FlightItineraryBooked`, `CfpOpened`, `ConferenceDatesChanged` and
  `PrivateEventMatchingLocationChanged` all came after the plan and have none. That is what settles
  the old open decision (below).
- **Flight producers multiplied.** `FlightBooked` is now built in four production places
  (`BookFlightCommand`, `ChangeFlightItineraryCommand`, `ItineraryLeg`, `NotifyFamily`) plus
  `EmailPreviewSamples`, and `FlightChanged` in two.
- **Schema versions moved.** The location-codes migration put trains, hotels and gatherings at v3,
  `ConferencePlanned` at v4, and private events and ground transfers at v2.

## Decided 2026-11-15

**D1. Scope: every event that lacks an occurrence time** (Ted). This answers the open decision the
first draft left — "flights only, or every event?" — the other way from its recommendation. The
recommendation leaned on the second-user rule; what tipped it is that the convention is already
the majority of new events and is being missed piecemeal, and the next question asked of the log
(as the hotel one was) should not need its own backfill. One ladder change, one rung, applied once.

**D2. Naming: `*On`, per event** (Ted), matching the thirteen fields in the tree. The verb is the
event's own (`bookedOn`, `changedOn`, `cancelledOn`, `plannedOn`, `clearedOn`), and the javadoc on
each says it is when Ted *recorded* it, as `FlightCancelled`'s already does. The UI wording stays
"Recorded on" per the 2026-08-27 decision.

**D3. Ad-hoc questions are answered by a read model, never by querying payloads.** Written down as
**R13** in `EventSourcingRulesHeuristics.md` the same day. Consequence for this plan: nothing here
adds a SQL view or report; the hotel-cancellation question becomes a projector once the fields
exist.

## The 20 events

| Event | Field | Version now → after | Prod producers | Test `new` sites |
|---|---|---|---|---|
| `FlightBooked` | `bookedOn` | 2 → 3 | 4 (+ `EmailPreviewSamples`) | 76 |
| `FlightChanged` | `changedOn` | 2 → 3 | 2 | 21 |
| `FlightItineraryBooked` | `bookedOn` | 1 → 2 | 1 | 25 |
| `TrainBooked` | `bookedOn` | 3 → 4 | 1 | 42 |
| `TrainChanged` | `changedOn` | 3 → 4 | 1 | 10 |
| `TrainCancelled` | `cancelledOn` | 1 → 2 | 1 | 22 |
| `HotelBooked` | `bookedOn` | 3 → 4 | 1 | 40 |
| `HotelChanged` | `changedOn` | 3 → 4 | 1 | 10 |
| `HotelBookingCancelled` | `cancelledOn` | 1 → 2 | 1 | 19 |
| `ConferencePlanned` | `plannedOn` | 4 → 5 | 2 | 50 |
| `ConferenceCancelled` | `cancelledOn` | 1 → 2 | **0** | 34 |
| `ConferenceDatesChanged` | `changedOn` | 1 → 2 | 1 | 9 |
| `CfpOpened` | **`recordedOn`** | 1 → 2 | 1 | 8 |
| `GatheringPlanned` | `plannedOn` | 3 → 4 | 1 | 34 |
| `GatheringChanged` | `changedOn` | 3 → 4 | 1 | 10 |
| `PrivateEventPlanned` | `plannedOn` | 2 → 3 | 1 | 27 |
| `PrivateEventCancelled` | `cancelledOn` | 1 → 2 | 1 | 14 |
| `PrivateEventMatchingLocationChanged` | `changedOn` | 1 → 2 | 1 | 15 |
| `GroundTransferPlanned` | `plannedOn` | 2 → 3 | 1 | 19 |
| `GroundTransferCancelled` | `cancelledOn` | 1 → 2 | 1 | 6 |
| `DifferentCityConflictCleared` | `clearedOn` | 1 → 2 | 1 | 2 |

Counts are `new <Event>(` occurrences, taken 2026-11-15. Three rows need a decision before slice 1:

- **`CfpOpened` cannot take `openedOn`.** It already has `closesOn`, the CFP's deadline, so
  `openedOn` would read as *the date the CFP opened* — a different domain fact the event does not
  carry. **Decided `recordedOn` (Ted, 2026-11-15)** — the one deliberate exception to D2's
  verb-per-event naming.
- **`ConferenceCancelled` has no production producer.** It is still read by five projectors, so
  stored rows exist, but nothing emits it. It needs the rung (so its stored rows bind) and perhaps
  not the field — or the field, for the uniformity D1 wants. Ask.
- **`DifferentCityConflictCleared` is appended through `commandExecutor.appendEvents`**, not a
  domain command, so its `now` comes from wherever that internal action is started. Find that
  boundary before assuming a controller.

**The cost is in the tests, not production: ~490 test construction sites against 25 production
ones.** Do not add a convenience constructor that omits the field — production could call it, and
that is the optional-field fallback rejected below with extra steps. `FlightCancelled`'s 3-argument
constructor is not a precedent: it defaults a *cause*, not a time. The test sites want a fixed
instant, which is a fixture question; deal with it per event family as each slice lands.

## The blocker, still unsolved

`EventUpcaster` cannot do this backfill as it stands:

```java
void upcast(ObjectNode payload, String eventLogicalType);   // EventUpcaster:28
```

It receives the **payload only**. The envelope `recorded_at` — the one honest source for a value
that historical events never carried — is not reachable from a rung. The composite is the same:
`EventPayloadUpcaster.upcast(String wireType, JsonNode payload, Integer storedVersion)` (`:75`).

The timestamp *is* in scope at all three call sites, so widening is local, not viral:

| Call site | Envelope time available as |
|---|---|
| `PostgresPersister` :674 | the row's `timestamp` |
| `LegacyEventMigration` :135 | `row.timestamp()` |
| `BackupService` :211 | `event.timestamp()` |

**This is the one place the envelope legitimately feeds a payload field**, and it is the store's own
infrastructure doing it, once, for rows that predate the field — R13's "the owner touching its own
field". After the rung, nothing outside `infrastructure` reads the envelope (slice 4 enforces it).

**Alternatives rejected.** (a) Make the field optional and have the projector fall back to the
envelope for legacy events — leaves `storedEvent.timestamp()` in the projector permanently, which is
the defect. (b) A bespoke one-off migration instead of a rung — the read path must still bind legacy
rows *before* the migration runs, so the rung is needed regardless; this is option A with extra
steps.

## Slices

Slices 2 and 3 are shared infrastructure and go first, once. Slice 1 then repeats **per event
family** — flights, trains, hotels, conferences, gatherings, private events, ground transfers, the
conflict-clear — each a commit of its own: payload field, producers, the type's version bump, golden
sample, test fixtures. Hotels first after flights, because that is what the question needs.

### 1. Payload field (per family)

- Each event gains its `Instant …On` (D2), with a javadoc saying it is when Ted recorded it,
  captured at the boundary, and that it is a payload field because of R11.
- The command that emits it gains the value and passes it through. The value is **captured at the
  boundary** from the injected `Clock` — the controllers already do `Instant.now(clock)` for the
  decision context, so it is the same instant, passed in rather than read again.
- **Full-snapshot trap, inverted.** `FlightChanged`, `TrainChanged`, `HotelChanged` and
  `GatheringChanged` are full snapshots, so a change form must **not** round-trip the time — every
  change captures a *fresh* `changedOn`, and the original booking time lives only on the `…Booked`
  / `…Planned` event. This is the opposite of `BookingProvenancePlan.md`'s trap, where a field
  silently clears if the form fails to carry it. Note it in each javadoc beside the existing
  snapshot note.
- The family's `EventTypes` registration moves to its next version and the rung (slice 3) learns
  it. Golden sample per new version in `GoldenEventDeserializationTest` (standing rule).

### 2. Widen the upcaster ladder

- `EventUpcaster.upcast(ObjectNode payload, String eventLogicalType, Instant recordedAt)`.
- `EventPayloadUpcaster.upcast(…, Integer storedVersion, Instant recordedAt)`; thread it from the
  three call sites above.
- The existing rungs (`ConferenceFormatUpcaster`, `LocationCodesUpcaster`, and the rest) ignore the
  new parameter. Pass the bare `Instant`, **not** an envelope record — there is one user, and the
  second-user rule says don't abstract yet.
- Keep the interface javadoc honest: a rung still knows one type and one version step; envelope
  metadata is a different axis, not knowledge of neighbouring rungs.

### 3. The rung

- `OccurrenceTimeUpcaster` — one rung, keyed by logical type → field name, backfilling the field
  from the envelope at each type's version step. Idempotent as every rung must be: if the field is
  already present, return untouched.
- Each type's versions are independent (the table above), so the rung is registered per
  `(type, version)` as each family lands in slice 1 — not all at once.

### 4. Projector + renderer + the R11 guard

- `BookedFlightsProjector` reads `event.bookedOn()` / `event.changedOn()`;
  `storedEvent.timestamp()` disappears from it.
- `ChangeEntry` text becomes `"Recorded on …"` and `"<reason> (recorded on …)"`.
- **Fold in the `Cleanup_Tasks.md` item** on the same lines: `toLocal` converts at
  `ZoneOffset.UTC` and prints the time with no zone label, while every other time on the page goes
  through `ZonedTimeTag`. Same code, same commit — fixing it separately is doing it twice.
- `BookedFlightsRendererTest` assertions updated — whole elements, not bare words.
- **New `NoEnvelopeTimestampReadsTest`**: an ArchUnit rule over `ProjectClasses.PRODUCTION`.
  No class in `application` or `web` may access `StoredEvent.timestamp()`, with `BackupService`
  exempt. It has the same shape as `NoAmbientClockReadsTest` and fails on arrival. This is R11's
  enforcement and the reason the defect cannot come back.
  - **Use `accessTargetWhere`, not `callMethod`.** `callMethod` sees calls only, so
    `.map(StoredEvent::timestamp)`, the likeliest shape in a projector, would slip past it. That
    gap was found in the clock and domain rules on 2026-09-25 and closed there.
  - **Mutation-verify both shapes.** Plant a `storedEvent.timestamp()` call *and* a
    `StoredEvent::timestamp` reference in an `application` class, and confirm each fails by name.
- Can land as soon as the flight family's slice 1 has; it does not wait for the other families.

### 5. Backup / restore check

Additive fields, and backup format v3 already stamps `schema_version` per event, so an old backup
restores through the new rung — and since backup preserves the envelope verbatim, a restored row
backfills to the same value it would have in place. **Verify, don't assume** — `RestoreSafetyTest`
plus an actual restore of a pre-change backup, once per family. Flagging per the standing
export/import compatibility rule.

### 6. The question that re-opened this

Once the hotel family has landed: a projector folding `HotelBooked.bookedOn` and
`HotelBookingCancelled.cancelledOn` by `HotelBookingId`, counting cancellations within 3 days.
Rendered on a page or persisted to its own table, per R13 — which one is a separate decision, made
when it is built.

## The standing rules this came from

- **R10 — every field a read model displays must come from an event** (2026-08-27). What found this
  defect.
- **R11 — a time a user reads is an event field, never the store's envelope** (2026-08-27). Its
  enforcement is slice 4.
- **R13 — a stored payload is opaque; nothing outside the event store queries into it**
  (2026-11-15). Why the hotel question waits for this plan rather than being answered with SQL.
