# Home City Plan — stop reporting problems for being at home

> **Status: DONE (2026-07-26, commit `26d15ac`).** `HomeCities` and the home-aware
> `ScheduleGapProjector` guards shipped as planned. Kept as a design record; see
> `docs/Backlog.md` for what is still open.

## Problem

`ScheduleGapProjector` has no notion of a home location, so ordinary "I flew home" itineraries
produce two kinds of false problems on `/schedule-problems`:

1. **False `MissingHotel`.** `detectMissingHotel()` takes each leg's arrival city and marks every
   night until the next departure *from that city* as a needed night
   (`ScheduleGapProjector.java:184-206`). Land at SFO, and the nights at home are reported as
   "missing hotel in San Francisco".

2. **False `MissingTravel`.** `detectMissingTravel()` compares `current.toCity()` with
   `next.fromCity()` by name only (`ScheduleGapProjector.java:132-142`). Arriving at SFO and later
   departing from SJC (or OAK) reports "missing travel San Francisco → San Jose", even though both
   are home.

The Bay Area is home, and any of **SFO**, **SJC**, **OAK** can be the arrival or departure airport
for the same trip, so all three resolve to home cities: `San Francisco`, `San Jose`, `Oakland`.

## Design

Introduce a small value type that owns the "is this home?" and "are these the same place?"
questions, and hand it to the projector. No Spring types in it; configuration happens in
`EventSourcingConfig`.

### `application/HomeCities`

Plain Java, constructed from a collection of city names. Instance methods only:

- `boolean includes(String city)` — case-insensitive membership.
- `boolean sameLocation(String cityA, String cityB)` — `true` when the names match
  case-insensitively, **or** when both are home cities. This is the single place that knows
  SFO/SJC/OAK are interchangeable.

Normalize on construction (trim, ignore blanks) so a property value with spaces after commas
works. An empty `HomeCities` behaves exactly like today's code: `includes` is always false and
`sameLocation` degrades to `equalsIgnoreCase`.

### Wiring

- `ScheduleGapProjector` gains a second constructor parameter `HomeCities`, keeping the existing
  one-arg constructor as an overload that delegates with an empty `HomeCities`. That keeps the
  ~20 existing test call sites untouched; new tests use the two-arg form.
- `EventSourcingConfig.scheduleGapProjector()` (line 240) builds it from a property so the home
  list is editable without a code change:

  ```properties
  # application.properties
  jittertravel.home-cities=San Francisco,San Jose,Oakland
  ```

## Changes in `ScheduleGapProjector`

### Missing hotel

- Guard the needed-night collection (line 202) with `!homeCities.includes(city)` — nights at home
  never need a hotel.
- Skip the conference-occupancy needed-night loop (lines 208-212) when
  `homeCities.includes(occ.city())` — a conference in a home city doesn't imply a hotel stay.
- A hotel actually *booked* in a home city is untouched: this suppresses the *problem*, not the
  booking, so it still appears in the hotel list and calendar.

### Missing travel

- `detectMissingTravel` (line 136): replace
  `!current.toCity().equalsIgnoreCase(next.fromCity())` with
  `!homeCities.sameLocation(current.toCity(), next.fromCity())`.
- `deduplicateMissingTravel` (lines 108-109): use `sameLocation` for the `sameCities` check so
  SFO-arrival / SJC-departure duplicates still collapse into one problem.
- `nextDepartureFromCity` (line 257): use `sameLocation` so a departure from a sibling home
  airport closes the stay in the arrival city.

### Out of scope (note it, don't do it yet)

`detectMissingTravelToFromConferences` (lines 144-182) also compares city names directly. A
conference held in a home city, reached from another home city, could still report missing travel.
Leave it as-is for now; revisit if it shows up in practice.

## Tests (`ScheduleGapProjectorTest`)

New nested class `HomeCityHandling`, using a resolver that maps real codes (or the existing
`StaticAirportCityResolver`) and `new HomeCities(List.of("San Francisco", "San Jose", "Oakland"))`:

Missing hotel:
1. Fly SFO → AMS and back AMS → SFO with a multi-night gap at home → no `MissingHotel`.
2. Same itinerary with a gap in Amsterdam → still reports `MissingHotel` for Amsterdam
   (the guard is home-specific, not a blanket suppression).
3. Conference in San Francisco → no `MissingHotel`.
4. Conference in Amsterdam → still reports `MissingHotel` (regression guard).

Missing travel:
5. Arrive SFO, later depart SJC → no `MissingTravel`.
6. Arrive SFO, later depart LAX → still reports `MissingTravel`.
7. Arrive AMS, later depart BRU → still reports `MissingTravel` (empty-home behavior unchanged for
   non-home cities).

Plus a focused `HomeCitiesTest` for `includes` / `sameLocation`: case-insensitivity, whitespace
normalization, and the empty instance degrading to `equalsIgnoreCase`.

AssertJ style: chained calls on their own lines; any boolean assertion gets `.as(...)` before the
terminal assertion.

## Verification

Run the **All Tests** IDEA run configuration. Then start the app and check
`/schedule-problems` against real data: the home-city missing-hotel and San Francisco → San Jose
entries should be gone, with everything else unchanged.
