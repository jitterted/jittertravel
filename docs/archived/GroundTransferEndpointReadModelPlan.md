# Ground Transfer Endpoints — a dedicated read model, and trains as endpoints

Status: `done` — designed and shipped 2026-08-23, all three slices. The reported bug is fixed: a
missing-travel gap that starts or ends at a train station now has the station to pick, and
preselects it.
**Prerequisite: done.** `archived/CuratedResolversToDomainPlan.md` shipped 2026-08-23, so
`AirportCityResolver` is in `domain` and slice 1's `Place` can take one — the airport arm of the
derivation is written once, inside `Place`, as the slice assumes.

## Problem

**The bug (Ted, 2026-08-23):** a missing-travel gap that starts or ends at a **train station** has
nothing to pick on `/plan-ground-transfer`. Neither end offers it, so the fix link lands on a form
that cannot express the hop it was clicked to record.

`GroundTransferEndpointOptions` builds its lists from `BookedFlightsProjector` and
`BookedHotelsProjector` only, and `GroundTransferEndpointResolver` knows exactly two token
prefixes — `airport:` and `hotel:` — with no free-text fallback by design (D12 of
`archived/GroundTransferPlan.md`). `GroundTransferEndpointChoices` even documents the hole as
expected behaviour:

> No match is the ordinary case for a gap the app holds no endpoint for at all (a train station, a
> conference venue), and it simply leaves "Choose a place…" showing.

**The structural problem underneath it.** The form wants rows shaped like *endpoints* — a place,
the moment it happens, and which end of a hop it can serve. No read model has that shape, which is
why `GroundTransferEndpointOptions` exists at all: it converts three list views built for three
other screens into a fourth shape. Adding trains means teaching that conversion a third source.
Per the standing heuristic — **read models are specific to the view or form they feed** — the
endpoints should be their own projector, consuming flight, hotel and train events directly.

**And the abstraction the bug exposes.** Both `ScheduleGapProjector` and the endpoint list have to
agree on what "place" means, per kind, or preselection silently stops matching and a submitted
transfer fails to close the gap it was entered for. Today that agreement is an unwritten
convention held together by a comment:

| Kind | `ScheduleGapProjector` uses | Endpoint options use |
|---|---|---|
| hotel | `e.address().locationForMatching()` | `hotel.locationForMatching()` |
| train | `e.departureStation().city()` | *(nothing — the bug)* |
| airport | `cityResolver.cityFor(code)` | `airportCities.cityFor(code)` |
| transfer | `e.origin().locationForMatching()` | — |

Split the derivation across two independent switches over the same events and nothing says when
they diverge. That is a missing value type, not a missing test (Ted, 2026-08-23).

## What ships

1. **`Place`** — the value both projectors derive the same way, once.
2. **`TransferEndpointProjector`** — a read model for this form, built straight from events.
   `GroundTransferEndpointOptions` survives as the thin layer that applies `now` and direction.
3. **Train stations as ground-transfer endpoints**, at both ends, closing the reported bug.

## Decisions

### D1 — `Place` is a domain value, and **every** kind derives inside it

A record over one normalized `String`, in `domain` beside `Address`:

```java
Place.of(Address address)                                  // address.locationForMatching()
Place.of(TrainStationAddress station)                      // station.city()
Place.of(AirportCode airport, AirportCityResolver cities)  // cities.cityFor(airport.code())
boolean matches(Place other)                               // the case-insensitive compare done today by hand
```

The airport arm is only legal once `AirportCityResolver` is a domain type — hence the prerequisite
above. Without it the airport derivation has to sit outside `Place`, leaving one kind of four still
written twice, which defeats the point.

`matches` replaces the bare `equalsIgnoreCase` in `GroundTransferEndpointChoices.theOnlyCandidate`.

### D2 — `Place` reaches the derivation points only, not through `ScheduleProblem`

`ScheduleProblem.MissingTravel.fromCity()/toCity()` stay `String`, and so do
`ScheduleTimeline.Movement`/`Stay`/`Occupancy`. Threading `Place` through them touches
`ProblemRef`, `ProblemBand`, `ScheduleProblemsRenderer`, `ProblemCalendarViewBuilder` and the
itinerary — a wide sweep for no additional guarantee. **The thing that must agree is which field of
which event becomes the place**, and after this that is written once. Carrying it deeper is a
follow-up, not part of this plan.

### D3 — The projector is keyed by occurrence, and the token is a field

`airport:DEN` names a *place*, not a leg — deliberate, from the original plan ("a transfer is
between places, not between flights"). Two arrivals into DEN are two rows sharing one token, so the
map is keyed `(subjectId, end)` and carries the token as data. Nothing about the stored event
changes.

### D4 — Direction is event-derived and lives in the row; `now` does not

Each row is tagged with the end it can serve, because that is a fact about the event:

- flights — you leave from an airport you **landed** at, and travel to one you **fly out** of
  (Ted, 2026-08-20)
- trains — the same rule: **arrival** stations on "From", **departure** stations on "To"
- hotels — both ends, carrying **check-out** on "From" and **check-in** on "To" (Ted, 2026-08-21)

The day filter needs a clock and stays outside the projector, in
`GroundTransferEndpointOptions.choicesAt(now)`, unchanged: an endpoint is offered while its own
local day is today or later, judged in **its own** zone.

### D5 — Trains take their zone from the event, never from the curated table

`TrainBooked.departureDateTime`/`arrivalDateTime` are `ZonedTimestamp`s whose zone was resolved at
**booking** time by `StationZone`. The endpoint reads that zone. It cannot fail, and it cannot
disagree with the train leg the transfer is being recorded next to.

This is deliberately *not* extended to hotels in this plan — see "Not in this plan".

### D6 — A station name is private, exactly like a hotel name

The station endpoint resolves to `TransferEndpoint(airportCode = "", name = <station name>,
address = Address("", city, "", "", country, <place>), zone = <the event's zone>)`, so
`GroundTransferPlanned` carries the name in `originName`/`destinationName`.
`TransferEndpointLabel.publicLabel` already publishes the airport code or city/region/country and
**never** the name, so no redaction rule changes — but a new private value now reaches the event,
which means both tiers of redaction test (see Tests).

`locationForMatching` on that `Address` is built from `Place.of(station).value()`, so the write path
and the gap report cannot disagree about where the hop ended.

### D7 — Token shape: `train:<tripId>:<end>`

A trip has two stations, so the end has to be in the token — `train:<uuid>:arrival`,
`train:<uuid>:departure`. Hotel and airport tokens are untouched.

### D8 — The resolver keeps its own lookup

`GroundTransferEndpointResolver` gains a `train:` branch reading a new
`BookedTrainsProjector.findById(TrainTripId)` (mirroring `HotelDetailsViewProjector.findById`).
Pointing the resolver at the new read model instead is a real option — hotel and station tokens are
unique keys, and it would retire the hotel zone divergence noted below — but it rewrites the
**submit path** for all three kinds, where a wrong answer is frozen into an event that restore
replays verbatim. Separate decision, separate change.

## Security

No new routes. `/plan-ground-transfer` is already OWNER-only, and stays so — it is a
`ProblemContext` fix target, and that banner prints hotel cities and exact arrival times. No change
to `SecurityConfig` or `AuthorizationMatrixTest`.

## Slices

### Slice 1 — `Place` — `shipped 2026-08-23`

`Place` + factories + `matches`. **No behaviour change** — the existing suite was the safety net,
and 1639 unit + 61 js are green on a `clean` run.

**Wider than the sentence above, deliberately.** Every place that turns an event field into a
matching place goes through `Place`, not only the four kinds in the divergence table:

| Where | Was | Now |
|---|---|---|
| `ScheduleGapProjector` — trains | `e.departureStation().city()` | `Place.of(station)` |
| `ScheduleGapProjector` — transfers, hotels, **conferences, gatherings, private events** | `…locationForMatching()` | `Place.of(address)` |
| `ScheduleGapProjector.flightLeg` | `cityResolver.cityFor(code)` | `Place.of(code, cityResolver)` |
| `BookedHotelsProjector` | `address.locationForMatching()` | `Place.of(address)` |
| `GroundTransferEndpointOptions` — airports | `airportCities.cityFor(code)`, **twice** | one `Place`, used for the option and its label |
| `GroundTransferEndpointResolver.airportEndpoint` | `airportCities.cityFor(code)` | `Place.of(airport, airportCities)` |
| `GroundTransferEndpointChoices.theOnlyCandidate` | `equalsIgnoreCase` | `Place.matches` |

The conference/gathering/private-event arms were not in the table because they are not
*endpoints* — but they are the same `locationForMatching` rule, and they are compared against
movement places by the very same projector. Routing four `Address` arms through `Place` and leaving
three beside them spelled out would have been worse than either extreme. The resolver's airport arm
matters most of the three airport sites: it is the **write** path, so what it derives is frozen into
the event the gap report later looks for.

Two derivations that stayed put, both correct as they are: the resolver copies a hotel's `Address`
**verbatim** (re-deriving it there could disagree with the stay it connects to), and
`ScheduleGapProjector`'s gathering-vs-conference different-city check compares two `Occupancy`
strings that were *already* built from `Place` — D2 keeps `Place` out of `Occupancy` itself.

**On `Place.value()` being unwrapped immediately at every call site:** that is the point, not a
smell. D2 says the type does not travel; what had to stop being written twice is *which field
becomes the place*, and after this it is written once.

**One trap the type carries, pinned by a test:** the record's generated `equals` is case-sensitive
while `matches` is not, so two Places that match are not equal. `PlaceTest` asserts exactly that, so
a later "simplification" to `equals` shows its cost.

Mutation-verified beyond `PlaceTest`: pointing `Place.of(Address)` at `city()` and
`Place.of(TrainStationAddress)` at `name()` fails 12 tests across `ScheduleGapProjectorTest` and
`ScheduleProblemsAcceptanceTest`; pointing the airport factory at the raw code fails those **plus**
`GroundTransferEndpointOptionsTest` and
`PlanGroundTransferHandlerTest.anAirportTokenResolvesToItsCodeAndItsCityAsTheMatchLocation` — which
is what proves all three airport sites are live rather than dead code beside the old expression.

**One gap this inherited and closed:** nothing covered the case-insensitive compare — swapping
`equalsIgnoreCase` for `equals` left the whole suite green, and had done since long before this
slice. `GroundTransferEndpointChoicesTest.aCandidateWhoseCityIsSpelledInAnotherCaseStillSettlesTheGap`
now pins it.

**Not in this slice:** the agreement invariant from the Tests section below. It wants an endpoint
projector to drive alongside `ScheduleGapProjector`, and for trains it would fail until slice 3 —
which is the bug, not a broken test. It belongs with slice 2 (flights and hotels) and slice 3.

### Slice 2 — `TransferEndpointProjector` (flights and hotels only) — `shipped 2026-08-23`

New `EventStreamConsumer` over `FlightBooked`/`FlightChanged`, `HotelBooked`/`HotelChanged`/
`HotelBookingCancelled`, registered in `EventSourcingConfig`. A cancelled hotel is **removed**, so
the `filter(hotel -> !hotel.cancelled())` in the options class became no code at all. All fifteen
existing `GroundTransferEndpointOptionsTest` cases passed unchanged against the new read model,
which is the "no behaviour change" evidence.

**`GroundTransferEndpointOptions` dropped to *one* dependency, not the "one plus
`AirportCityResolver`" this plan predicted.** The prediction assumed the options class would still
resolve an airport's city for the label. It does not: the row carries the label's city, so the
resolver moved wholly into the projector and the options class no longer names a kind of travel
anywhere. It is now exactly the three things that need `now` or are about the form — the day filter,
the ordering, the label.

**The row carries two pairs that look like duplication and are not.** Both were near-miss silent
behaviour changes, and both are pinned by their own test:

- **`city` vs `place`.** For a flight they are the same value. For a hotel the label says the
  *address's* city ("SeminarZentrum Rückersbach — Rückersbach", the words on the building) while the
  schedule matches on `locationForMatching` ("Johannesberg", the town everything else names). The old
  code did this too, three lines apart, with a comment. Collapsing them breaks the label or — the
  silent half — the preselection.
- **`moment` vs `offeredUntil`.** `moment` is what the end is *about*: the prefill, and what the
  label names. `offeredUntil` is the moment whose local day decides whether the endpoint is still
  worth showing. A flight's are equal. A stay's are not: **both** its ends are filtered on
  *check-out*, so the hotel you are riding toward does not vanish from the "To" list the instant you
  arrive — which is when the ride gets written down. That asymmetry was a comment on a private
  method before; now it is a field the projector sets and a test names.

**One deliberate behaviour change, and the only one.** Ordering is now the endpoint's own *instant*
for all four lists. It was previously the instant for hotels and the **local wall clock** for flight
legs (they were sorted on the prefill strings, which the old comment admitted was a convenience).
The two differ only across zones, where the wall-clock version is wrong: a London landing at 09:00
BST happened before a Denver landing at 08:00 MDT, and reading the two clocks side by side says the
opposite. Nothing tested it either way; `arrivalsInDifferentZonesAreOrderedByWhenTheyActuallyHappened`
now does.

**Verified:** 1654 unit + 61 js green on a `clean` run, with `JitterTravelApplicationTests.contextLoads`
proving the bean wires and registers for real. Six mutations, each caught by its own assertions and
several by both tiers at once — which is the shape to want, the read model stating a fact and the
form's behaviour depending on it:

| Mutation | Caught by |
|---|---|
| check-in row `offeredUntil` ← check-in | `bothEndsOfAStayAreOfferedUntilItsCheckOut` + `anOngoingStayIsStillOffered…` |
| hotel `city` ← `place.value()` | `aStayCarriesItsAddressCityForTheLabel…` |
| cancel removes only the check-out row | `aCancelledStayLeavesNoRowAtEitherEnd` + `aCancelledStayIsNotSomewhereTedCanBeDroppedOff` |
| sort ← local wall clock | `arrivalsInDifferentZonesAreOrderedByWhenTheyActuallyHappened` |
| arrival/departure swapped | 12 tests across both tiers |
| `RowKey` ignoring its `end` | 21 tests across both tiers |

### Slice 3 — trains — `shipped 2026-08-23`

Train arms in the projector; the `train:` branch in the resolver; two new optgroups in
`plan-ground-transfer.html`; hint and "Nothing to travel between yet" copy updated to name trains.
The label mirrors a flight leg — `Hamburg Hbf — Hamburg · arrive Wed Sep 16, 11:00 AM (ICE 573)` —
with the parenthesis dropped when `serviceId` is blank. **The reported bug is fixed:** a gap ending
at a station now preselects it.

**The lookup is `TrainDetailsViewProjector.findById`, not `BookedTrainsProjector.findById`.**
This plan named the latter; the parenthetical it wrote — "mirroring `HotelDetailsViewProjector`" —
turned out to name the right one. `TrainDetailsViewProjector` already *had* `findById`, and its view
already carried the whole `TrainStationAddress`. `BookedTrainView` drops the station's **country**,
which the public label publishes, so following the letter of the plan would have meant adding two
undisplayed fields to a list view — the exact pattern slice 2 had just moved away from.

**One thing had to change to satisfy D5: `TrainDetailsView` now keeps its `ZonedTimestamp`s.** It
used to call `localDateTime()` in the projector and throw the zone away — harmless for the
`datetime-local` input it feeds, which reads a wall clock and nothing else, but it is *the same loss*
that makes the hotel path re-derive a zone it already had (see "Not in this plan"). The narrowing
moved to `ChangeTrainController`, at the point it binds, which is where it belongs. So the station
branch is the only one of the three that cannot raise `ZoneResolutionException` — it never asks.

**`GroundTransferEndpointChoices` grew to six lists**, and the churn is worth recording: five test
files construct it positionally, so a second kind meant editing all of them. Two things absorbed
that — a `nothing()` factory for the three all-empty fixtures (the form already reasons about that
state), and a `flightsAndStays(...)` helper local to `GroundTransferEndpointChoicesTest`. If a
*third* kind arrives — conference and gathering venues are the named candidate — reshaping this
record around `TransferEnd` rather than named accessors is the change to make first; the template
reads the lists by name, which is the only thing holding the current shape in place.

The exactly-one preselection rule counts **across** kinds, not within one: a station and an airport
in the same city on the same day are two candidates and the form asks. That is a new way for the
rule to matter, so it has its own case.

**Redaction (D6), both tiers, and neither rule changed.** A station name reaches
`GroundTransferPlanned.originName` exactly as a hotel name does, and `publicLabel` has never read the
name — but a new private value on the event is a new chance to leak it, so the absence is asserted
rather than assumed. Mutation-verified by pointing the public projector's route at `ownerLabel`:
`PublicCalendarProjectorTest.aTransferFromATrainStationPublishesTheCityAndNeverTheStationName` and
`CalendarRedactionSecurityTest.anonymousUserSeesAStationHopWithoutTheStationName` both go red, along
with the pre-existing hotel case.

**Verified:** 1677 unit + 61 js green on a `clean` run. Five mutations, each caught:

| Mutation | Caught by |
|---|---|
| train arrival/departure stations swapped | 3 projector + 2 options + 2 handler tests |
| a station's `place` ← its name | `aStationsPlaceIsItsCityWhileTheLabelKeepsTheStationName` |
| resolver reads the wrong end's station | both `PlanGroundTransferHandlerTest` train cases |
| `origins()` drops `trainArrivals` | `aGapEndingAtATrainStationNowSettlesOnIt` + `aStationAndAnAirportInOneCitySettleNothing` |
| public route ← `ownerLabel` | both redaction tiers |

## Tests

- **`PlaceTest`** — each factory picks the field the gap report picks; `matches` is
  case-insensitive.
- **Agreement invariant** — drive one event stream through `ScheduleGapProjector` and the endpoint
  projector; every `MissingTravel` gap's `fromCity`/`toCity` is a `Place` some endpoint row carries.
  This is the test D2 leaves standing, and the one that would have caught the train hole.
- **`TransferEndpointProjectorTest`** — a booked train yields an arrival row and a departure row
  with the right ends, places and moments; `TrainChanged` supersedes; a cancelled hotel yields no
  rows; two flights into one airport yield two rows sharing a token.
- **`GroundTransferEndpointOptionsTest`** — extended for trains: both directions, the
  own-local-day filter, blank `serviceId`.
- **`GroundTransferEndpointChoicesTest` / `GroundTransferPreselectionTest`** — a gap ending at a
  station preselects it; two trains into one city preselect **nothing** (the exactly-one rule).
- **Resolver** — `train:<id>:arrival` resolves to the arrival station with the event's own zone; a
  malformed or unknown trip id raises `UnknownTransferEndpoint`.
- **`PlanGroundTransferWebIntegrationTest`** — the optgroups render, and a submitted station token
  writes the transfer.
- **Redaction, both tiers** (D6) — `PublicCalendarProjectorTest` asserts the station name is absent
  from what the projector emits, and `CalendarRedactionSecurityTest` asserts the rendered anonymous
  body `doesNotContain` it through the real security chain, with the fixture driven through a real
  `PublicCalendarProjector`.

Mutation-verify each new assertion, per standing practice.

## Not in this plan

**All four are now tracked in `../Cleanup_Tasks.md` (Deferred), each with its trigger** — lifted
2026-08-23 when this doc was archived, so nothing here is the only record of open work. The fourth,
reshaping `GroundTransferEndpointChoices` around `TransferEnd`, was not foreseen: it surfaced while
shipping slice 3, when adding one kind meant editing the record's construction in five test files.

- **The hotel zone divergence.** `GroundTransferEndpointResolver` re-derives a hotel's zone with
  `locationZones.resolve(hotel.address())` at submit time, although `HotelBooked.checkIn()` already
  carries the zone resolved at booking. Two sources for one fact, and the only reason
  `ZoneResolutionException` is reachable on this path. Pre-existing; unchanged here. Fixing it
  means the audit `LocationZoneAudit` already models — assert the two agree for every hotel and
  station in the log — plus deciding whether the controller's now-unreachable `catch` and its
  "fix the hotel's address first" copy get deleted.
- **Conference and gathering venues as endpoints.** The other half of the sentence in
  `GroundTransferEndpointChoices`. Same shape, separate ask.
- **Carrying `Place` through `ScheduleProblem` and the renderers** (D2).
