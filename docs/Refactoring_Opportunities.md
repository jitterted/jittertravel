# Projection & Rendering Duplication Report

Generated from codebase analysis — do not implement without discussion.

---

## 1. Projector Bean Wiring — 11 instances, ~56 lines

Every projector in `EventSourcingConfig` follows the identical three-line pattern:

```java
ProjectorType p = new ProjectorType();
eventStore.subscribe(p);
p.handle(eventStore.findAll());
```

**Affected projectors:** BookedFlightsProjector, FlightCalendarProjector, BookedHotelsProjector,
TentativeHotelBookingsProjector, TentativeHotelBookingProjector, HotelCalendarProjector,
BookedTrainsProjector, TrainCalendarProjector, ItineraryProjector, ConferenceCalendarProjector,
ScheduleGapProjector (only variant — extra constructor arg).

**Proposed abstraction:** A generic `registerProjector(P projector, EventStore eventStore)` helper
or a `ProjectorBootstrapper` that handles subscribe + replay in one call. Would cut wiring to one
call site per projector.

**Difficulty:** Low — **highest priority win.**

---

## 2. Calendar Projector Structure — 4 projectors, ~70 lines

`FlightCalendarProjector`, `TrainCalendarProjector`, `HotelCalendarProjector`, and
`ConferenceCalendarProjector` share identical scaffolding:

- `Map<Id, CalendarEntry>` (or `Map<Id, List<CalendarEntry>>`)
- `handle()` switching on one event type
- `entries()` returning values sorted by `CalendarEntry::start`

Differences are only in how `CalendarEntry` fields are populated and whether `mapsUrl` is set.
The multi-day logic in flights and trains (producing two entries per booking) is the one structural
outlier.

**Proposed abstraction:** `BaseCalendarProjector<E, ID>` abstract class with template method
pattern. Subclasses override `buildEntries(E event)` returning `List<CalendarEntry>`; the base
class owns the map, the `handle()` dispatch, and the sorted `entries()` method.

**Difficulty:** Medium.

---

## 3. Booked-X Simple Projectors — 2 projectors, ~50 lines

`BookedHotelsProjector` and `BookedTrainsProjector` are near-identical:
`Map<Id, View>`, simple `put` in `handle()`, sorted `views()`. `BookedFlightsProjector` is the
exception — it carries change history and an `apply()` method, so it stays as-is.

**Proposed abstraction:** `BaseBookedItemProjector<ID, V>` with overridable sort-key extraction.
The two simple projectors become thin subclasses.

**Difficulty:** Medium. Note: don't pull `BookedFlightsProjector` into the base class.

---

## 4. Itinerary Multi-Day Branching — 2 methods, ~18 lines

`toFlightEntries()` and `toTrainEntries()` in `ItineraryProjector` both execute:

```java
Entry departure = new Entry(DEPARTURE, ...);
if (depDt.toLocalDate().equals(arrDt.toLocalDate())) return List.of(departure);
return List.of(departure, new Entry(ARRIVAL, ...));
```

**Proposed abstraction:** A private helper:
```java
private static <E> List<E> twoEntryListIfMultiDay(
        LocalDateTime dep, LocalDateTime arr,
        Supplier<E> departureFactory, Supplier<E> arrivalFactory)
```
Makes the same-day vs multi-day logic explicit and testable once.

**Difficulty:** Low.

---

## 5. View Record Structural Similarity — Structural only, 0 lines saved

`BookedFlightView`, `BookedTrainView`, and `BookedHotelView` all carry: an ID, a textual
descriptor, datetime fields, location, and an optional maps URL. Differences are meaningful
(flight change history, hotel booking intent), so a shared supertype wouldn't reduce code.

**Proposed abstraction:** A `BookedItemView` sealed interface documenting the common contract
(`getId()`, `getPrimaryLocation()`, `getMapsUrl()`, `getPrimaryDateTime()`). Enables polymorphic
template logic if views are ever rendered together.

**Difficulty:** Low — but benefit is documentation and future-proofing only.

---

## 6. Booked-X Template Scaffolding — ~80 lines across 3 files

`booked-flights.html`, `booked-trains.html`, and `booked-hotels.html` all share the same outer
shell: nav breadcrumb, page title (`<h1>`), empty-state message, item list/table, and a
"Book another…" call-to-action link at the bottom. Only the inner card/table structure differs.

**Proposed abstraction:** A shared Thymeleaf layout fragment (`booked-layout.html`) parameterised
with title, empty-state text, and a content slot. Inner card markup stays in each file.

**Difficulty:** Medium.

---

## 7. Itinerary Entry-Card Pattern — ~90 lines in `itinerary.html`

Four separate `th:if` blocks (Flight, Train, Hotel, Conference) each repeat the same card chrome:

```html
<div class="entry-card entry-card--<type>">
  <div class="entry-header">
    <svg><!-- icon --></svg>
    <span class="entry-kind entry-kind--<type>">…</span>
  </div>
  <div class="entry-title">…</div>
  <div class="entry-detail">…</div>
</div>
```

**Proposed abstraction:** Per-type Thymeleaf fragment files (`flight-entry.html`,
`train-entry.html`, etc.) with a shared `entry-card.html` fragment for the outer chrome. The
`itinerary.html` switch block reduces to four `th:replace` calls.

Alternatively: a single `th:switch="${entry.kind().name()}"` block replacing the four `th:if`
blocks, reducing nesting depth without extracting fragments.

**Difficulty:** Medium.

---

## Priority Order

| # | Area | Lines | Difficulty |
|---|---|---|---|
| 1 | Projector bean wiring (EventSourcingConfig) | ~56 | Low |
| 2 | Itinerary multi-day branching (ItineraryProjector) | ~18 | Low |
| 3 | Booked-X template scaffolding | ~80 | Medium |
| 4 | Itinerary entry-card pattern | ~90 | Medium |
| 5 | Calendar projector base class | ~70 | Medium |
| 6 | Booked-X simple projectors (Hotel + Train) | ~50 | Medium |
| 7 | View record interface (BookedItemView) | 0 (structural) | Low |
