# Event Occurrence Timestamps — a displayed time is a payload field

Status: **open** — found 2026-08-27 while extracting the Book Flight event-model chapter, nothing
built. Scope is small; the interface change in slice 2 is the only part that touches shared code.

## The defect

`BookedFlightsProjector` builds the inline change history from `storedEvent.timestamp()` — the
**event-store envelope's `recorded_at`** — and renders it as a domain fact:

```java
case FlightBooked event  -> apply(…, bookingEntry(storedEvent.timestamp()));   // :42
case FlightChanged event -> apply(…, changeEntry(storedEvent.timestamp(), …)); // :47
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

**Decided (Ted, 2026-08-27): `recordedAt`, and the UI says "Recorded on".** No new form field. When
Ted entered a booking is a real and useful fact; claiming it is when he booked is not. A true
user-supplied `bookedAt` was considered and rejected — it is one more field on every booking,
usually guessed, and a recording surface should not grow data entry it does not need.

**Contained.** `BookedFlightsProjector:42,47` are the **only** display-side envelope reads in
`src/main`. `BackupService`'s use of `timestamp()` is correct and stays — event-oriented backup
writes every `event_log` row verbatim, envelope included, which is exactly what an envelope is for.

## The blocker, found before planning

`EventUpcaster` cannot do this backfill as it stands:

```java
void upcast(ObjectNode payload, String eventLogicalType);
```

It receives the **payload only**. The envelope `recorded_at` — the one honest source for a value
that historical events never carried — is not reachable from a rung. The composite is the same:
`EventPayloadUpcaster.upcast(String wireType, JsonNode payload, Integer storedVersion)`.

The timestamp *is* in scope at all three call sites, so widening is local, not viral:

| Call site | Envelope time available as |
|---|---|
| `PostgresPersister.StoredEventRow.toStoredEvent` :608 | `timestamp` (used two lines later) |
| `LegacyEventMigration` :108 | `row.timestamp()` on `BackupEventRow` |
| `BackupService` :211 | `event.timestamp()` |

**Alternatives rejected.** (a) Make the field optional and have the projector fall back to the
envelope for legacy events — leaves `storedEvent.timestamp()` in the projector permanently, which is
the defect. (b) A bespoke one-off migration instead of a rung — the read path must still bind legacy
rows *before* the migration runs, so the rung is needed regardless; this is option A with extra
steps.

## Slices

### 1. Payload field

- `FlightBooked` and `FlightChanged` each gain `Instant recordedAt`.
- `BookFlightCommand` / `ChangeFlightCommand` gain `recordedAt` and pass it through. The value is
  **captured at the boundary** from the injected `Clock` — the controllers already do
  `Instant.now(clock)` for the decision context, so it is the same instant, passed in rather than
  read again.
- **Full-snapshot trap, inverted.** `FlightChanged` is a full snapshot, so the change form must
  **not** round-trip `recordedAt` — every change captures a *fresh* one. This is the opposite of
  `BookingProvenancePlan.md`'s trap, where a field silently clears if the form fails to carry it.
  Note it in the javadoc next to the existing snapshot note.

### 2. Widen the upcaster ladder

- `EventUpcaster.upcast(ObjectNode payload, String eventLogicalType, Instant recordedAt)`.
- `EventPayloadUpcaster.upcast(…, Integer storedVersion, Instant recordedAt)`; thread it from the
  three call sites above.
- Six existing rungs ignore the new parameter. Pass the bare `Instant`, **not** an envelope record —
  there is one user, and the second-user rule says don't abstract yet.
- Keep the interface javadoc honest: a rung still knows one type and one version step; envelope
  metadata is a different axis, not knowledge of neighbouring rungs.

### 3. Schema bump + rung

- New constant beside `ZONED_TIMESTAMP_SCHEMA_VERSION` / `CONFERENCE_FORMAT_SCHEMA_VERSION`;
  `FlightBooked` and `FlightChanged` move to it in `EventTypes`.
- `FlightRecordedAtUpcaster` — v2→v3 for both types, backfilling `recordedAt` from the envelope.
  Idempotent as every rung must be: if `recordedAt` is already present, return untouched.
- Golden sample per new version in `GoldenEventDeserializationTest` (standing rule).

### 4. Projector + renderer + the R11 guard

- `BookedFlightsProjector` reads `event.recordedAt()`; `storedEvent.timestamp()` disappears from it.
- `ChangeEntry` text becomes `"Recorded on …"` and `"<reason> (recorded on …)"`.
- `BookedFlightsRendererTest` assertions updated — whole elements, not bare words.
- **New `NoEnvelopeTimestampReadsTest`** — a plain source scan over `src/main/java/.../application`
  and `.../web` banning `StoredEvent.timestamp()`, with `BackupService` exempt. Same shape as
  `NoAmbientClockReadsTest` and `DomainIsPureTest`: no ArchUnit, no reflection, fails on arrival.
  This is R11's enforcement and the reason the defect cannot come back.

### 5. Backup / restore check

Additive field, and backup format v3 already stamps `schema_version` per event, so an old backup
restores through the new rung. **Verify, don't assume** — `RestoreSafetyTest` plus an actual restore
of a pre-change backup. Flagging per the standing export/import compatibility rule.

## Open decision

**Only flights, or every event?** Verraes recommends a domain timestamp in *every* event payload,
"even for events with no special timestamping requirements", for decoupling and evolution. Flights
are the only events that display one today, so slices 1–4 scope there. Doing all ~30 event types is
a much larger bump and buys nothing until a second projector wants a history — which the
no-abstraction-before-the-second-user rule says is the right time. **Recommendation: flights now,
and let the standing rule below catch the next one.**

## The standing rules this came from

Both landed in `EventSourcingRulesHeuristics.md` on 2026-08-27, ahead of the code:

- **R10 — every field a read model displays must come from an event.** The mirror of R8 (which
  catches the projector ignoring a field the event *does* carry); this catches the view displaying a
  field **no event provides**. R10 is what found this defect.
- **R11 — a time a user reads is an event field, never the store's envelope.** The specific case,
  with the Verraes vocabulary and the "the label lies" argument. Its **enforcement is part of slice
  4 of this plan**: a plain source scan banning `StoredEvent.timestamp()` in `application` and `web`,
  `BackupService` exempt, in the style of `NoAmbientClockReadsTest` and `DomainIsPureTest`.

Hotels, trains, conferences and gatherings have no change history today, so nothing else is
currently exposed — but the next projector that grows one walks into the same trap, which is why the
rules went in now rather than with the fix.
