# Schedule Problems Rewrite Plan

**Status:** shipped 2026-08-20. All seven slices done; suite green at 1116 (+ 36 js). Supersedes
the detection half of `ScheduleGapProjector`; the event handling, caching, and `context()` read
model stay.

D13 (the 14-night **trip break**) was added during implementation and confirmed by Ted 2026-08-20.
D14 (**home does not strand him**) followed from a bug Ted found on the live report the same day.
Its mirror image — gaps *into* home — is still open.

`/schedule-problems` is one of the main reasons this app exists, and it is currently wrong in both
directions: it invents travel gaps that do not exist and misses nights with no bed. This plan
replaces the detectors wholesale. The event handling is fine; the *model of the world* is not.

## Why the current detector is wrong

Its model is "a list of flights and trains, with conferences bolted on the side." Everything below
follows from that.

**1. Hotels and gatherings never establish position.** `hotelStays` is consulted only as *coverage*
for a night (`coversNight`); `gatheringOccupancies` only for conflict detection. So a hotel
checkout cannot be one end of a travel gap, and a gathering cannot be the other end. The three
problems that most obviously exist in a real itinerary — checkout here, check-in there; checkout
here, gathering there; gathering here, conference there — are unreachable by construction.

**2. "Adjacent" means adjacent *legs*, however far apart.** `detectMissingTravel` walks
`legs[i]`/`legs[i+1]`. An itinerary with a flight in and a flight out three weeks later has exactly
two legs, and they are adjacent — so the whole trip between them collapses into one phantom gap
from the arrival airport's city to the departure airport's city.

**3. Every conference runs its own unbounded nearest-leg search.**
`detectMissingTravelToFromConferences` asks "is there any leg to/from this conference's city?" and,
finding none, reaches for the nearest leg *anywhere in the timeline* — which for a trip with two
legs is always the same two legs, so every conference on the trip produces a gap against them.

**4. `deduplicateMissingTravel` is a symptom suppressor.** It exists only because detectors 2 and 3
generate overlapping garbage, and it merges by matching city-pair — so it can never fix a *wrong*
city-pair. It is deleted by this plan, not repaired.

**5. Missing-hotel nights come only from leg arrivals and conference interiors.** A checkout creates
no need for the next night, a gathering creates no need at all, and the conference loop is
`night.isBefore(endDate)`, which drops the conference's last night. The
`conferenceElsewhere`/`hotelInConferenceCity` heuristics inside that method are guesses standing in
for the position information the model never had. They are deleted too.

**6. City matching is exact-string across three separate namespaces.** An airport's resolved city,
a train station's `city()`, and an address's `locationForMatching()` are compared with
`equalsIgnoreCase`. Any spelling drift between them ("New York" vs "New York City") silently
manufactures a travel gap.

**7. Duplicate hotel bookings are not detected at all.** Two stays covering the same night is a
real problem that has really happened, and it costs money. `ScheduleProblem` has no such variant
and never has (confirmed: no such code in git history).

### Worked example of the damage

Given: flight MUC→HAM arriving Aug 25 · hotel Hamburg Aug 25–26 · hotel Soltau Aug 26–31 ·
conference in Soltau Aug 27–30 · hotel Hamburg Aug 31 – Sep 7 · gathering in Aachen Sep 8 ·
conference in Johannesberg Sep 10–13 · flight FRA→SFO Sep 14.

Reported today: `Hamburg → Frankfurt` (flaw 2), `Hamburg → Johannesberg` and `Soltau → Frankfurt`
(flaw 3), `Hamburg → Soltau` (right pair, wrong reason — attributed to the flight, not the
checkout), `Johannesberg → Frankfurt` (correct by luck). Three of five are phantoms. Two of the
three missing-hotel runs are never reported.

## The model: one located timeline

Every event source contributes a **presence fact** — a claim about where Ted is, and when. They go
into one chronological sequence, and every detector reads that one sequence.

| source | contributes |
|---|---|
| flight / train | a **movement**: leaves `fromCity` at departure, is in `toCity` at arrival |
| hotel stay | **stationary** presence in the city, check-in day → check-out day |
| conference | **stationary** presence in the venue city, start → end |
| gathering | **stationary** presence in the city, start → end |

## Decisions

### D1 — Last known location; Ted is never "unlocated"

You are in the city of the most recent presence fact until a later one places you elsewhere.
Checked out of Hamburg on Sep 7 with a gathering in Aachen on Sep 8? The night of Sep 7 is in
**Hamburg**, Sep 8 is in **Aachen** (the gathering is itself a presence), and Sep 9 is still in
Aachen until the Johannesberg conference starts on Sep 10.

This is what makes both detectors agree: the same gap yields a missing bed in Hamburg *and* missing
travel Hamburg→Aachen. Both are real. The order in which Ted would fix them is unknown, so both are
reported.

### D2 — One problem per adjacent pair

The missing-travel detector is a single left-to-right walk carrying `currentCity`:

- **movement**: if `fromCity != currentCity`, report `MissingTravel(currentCity, previous end,
  fromCity, departure)`. Then `currentCity = toCity`.
- **stationary**: if `city != currentCity`, report `MissingTravel(currentCity, previous end, city,
  start)`. Then `currentCity = city`.

Two gatherings in the same city produce one problem, not two. There is nothing left to deduplicate,
so `deduplicateMissingTravel` goes away with the detectors that needed it.

### D3 — Within-day ordering comes from the day's legs, never from stored clock times

The subtlest part of the rewrite, and the one most likely to reintroduce phantom gaps.

June 22 of the European Conferences itinerary has four facts on one calendar day: hotel checkout in
London, flight LHR→FRA, train Frankfurt→Leipzig, hotel check-in in Leipzig. June 17 has five. A
naive sort by instant makes the answer depend on whatever clock time happens to sit in the hotel
check-in field — a check-in stored at 00:00 sorts before the flight and invents a gap.

So: order the timeline by **local date**; within a date, the day's legs (which have real, meaningful
departure times) establish the sequence of cities, and a stationary presence on that day is
satisfied if its city matches the day's start city, its end city, or any city in between. A day with
*no* legs and two stationary presences in different cities is exactly the Hamburg→Soltau problem,
and still reports.

### D4 — One city namespace

An airport's resolved city, a train station's city, and an address's `locationForMatching()` are
one namespace and compare directly. `JFK` is New York, `YYZ` is Toronto, `FRA` is Frankfurt — the
last mile from the airport is not modelled, even where it is genuinely long. Where a name needs
reconciling, `Address.locationForMatching` is the existing override and stays the only one (Ted,
2026-08-20).

Consequence for real data: a gathering whose address city reads "New York City" will not match
`JFK → "New York"` until its `locationForMatching` is set. That is a data task, not a code one.

### D5 — Missing hotel: every night away from home needs a bed, split by city

Hotel stays must be contiguous with no gaps whenever Ted is away from home (Ted, 2026-08-20). So
the detector sweeps **nights**, not legs: for each night between leaving home and returning, take
the location from D1, and if no stay covers it, it is missing.

Consecutive uncovered nights **in the same city** merge into one run, reported check-in →
check-out. They split when the city changes, because the row has to tell Ted *where to book*.

The run does **not** split at a conference boundary. Johannesberg Sep 10–13 followed by a Sep 14
departure from Frankfurt is one row, `Johannesberg Sep 10 → Sep 14` — one hotel booking, one row.
(This merges two of the rows in Ted's original example; see Example A.)

Confirmed by Ted 2026-08-20, along with what happens next: booking the conference hotel Sep 10–13
leaves the night of Sep 13 uncovered, so a new, smaller `Sep 13 → Sep 14` problem appears and gets
fixed in turn. A run shrinking to its residual as bookings land is the detector working, not a
regression — the night sweep gives this for free, since it re-derives coverage from scratch.

The sweep runs from the **first presence fact to the last**, and no further: an itinerary that
opens with a flight into Hamburg demands no bed before it, and one that closes with a flight home
demands none after.

### D6 — Overnight travel needs no bed

A night fully spanned by a booked leg is spent in transit. "I am technically in SFO until I land in
FRA" (Ted, 2026-08-20) — the red-eye departing Jun 6 and landing Jun 7 leaves the night of Jun 6 as
a home night, needing nothing.

### D7 — Nights at home need no bed

Unchanged; `HomeCities` already carries this, and any two home cities are the same location.

### D8 — Duplicate hotels are a new `ScheduleProblem` variant

Any night covered by **two or more** stays is a problem, **whether or not they are in the same
city** — Ted can only sleep in one place, and the second booking is a real expense (Ted,
2026-08-20). Reported as the overlapping run of nights with both hotel names.

Contiguous stays in one city are *not* duplicates: checkout day equals check-in day shares no
night, so the two Antwerp hotels (Jun 10–13, Jun 13–15) and the two Munich hotels (Jun 24–27,
Jun 27–30) of Example B stay clean.

`DuplicateHotel` carries the hotel names and their `HotelBookingId`s, so a later slice can offer a
cancel link. This means `HotelStay` in the projector must start retaining `hotelName` and the
booking id, which `HotelBooked`/`HotelChanged` already carry.

### D9 — Every located entry kind contributes a presence fact, now and in future

**Private events are treated exactly like gatherings** for schedule-problem purposes (Ted,
2026-08-20): a stationary presence in their city for their time range. `PrivateEventPlanned` is
missing from `ScheduleGapProjector` entirely today and gets a case in the switch. (No change or
cancel event exists for private events yet; when one lands it joins the switch like the others.)

The standing rule, beyond this rewrite: **any future entry kind that has a location must be added
as a presence fact.** An entry the projector cannot see is an entry that silently breaks the
location trace for everything after it — a private dinner in Berlin between two Hamburg stays makes
the Berlin nights and both travel legs invisible.

The located events today are `ConferencePlanned`, `GatheringPlanned`/`GatheringChanged`,
`HotelBooked`/`HotelChanged`, `PrivateEventPlanned`, `TrainBooked`/`TrainChanged` (all carrying an
`Address`), plus `FlightBooked`/`FlightChanged` (carrying `AirportCode`s). `Event` is deliberately
not sealed, so this cannot be a compile-time guard; see Open below.

### D10 — The conflict detectors are untouched

`detectGatheringConflicts` and `detectDifferentCityConflicts` are independent of position and are
correct. They stay as they are, including the cleared-conflict handling.

### D11 — A booking exists whether it is tentative or final

`BookingIntent` is ignored by problem detection (Ted, 2026-08-20): "tentative still means I booked
the hotel with a provider, and until I cancel it, that reservation exists." So a `TENTATIVE` stay
covers its nights like any other, and a `TENTATIVE` stay overlapping a `FINAL` one **is** a
duplicate — that is precisely the case where Ted is paying for two rooms.

The intent is not useless here, it is just not a detection input: it is what tells him *which* of
the two duplicates to cancel, so `DuplicateHotel` carries each stay's `BookingIntent` for display.

### D12 — The timeline is a real object, and `context()` comes from it too

`ScheduleGapProjector` keeps event handling and read-model caching. It builds a `ScheduleTimeline`
from its state and asks it for problems and for context. One cohesive object owning the ordered
presences — not a bag of single-method detector classes — and it preserves the existing invariant
that `problems()` and `context()` cannot describe two different versions of the schedule, because
now they are literally derived from the same ordered sequence.

### D13 — A fortnight of silence ends the run (NEEDS TED'S RULING)

**Not agreed in advance — found while implementing, and decided in order to finish.**

Last-known-location has no natural stopping point. An existing test paired a conference in Oslo in
January with one in Lima in December and nothing between them, and the night sweep duly demanded a
hotel room in Oslo every night for eleven months. The same thing would happen in Ted's real data
the moment a conference is planned months out with no flights booked yet — which is exactly what
CFP-season planning looks like.

So `ScheduleTimeline.TRIP_BREAK_NIGHTS = 14`: if the schedule holds nothing at all for longer than
a fortnight, the run ends at the last fact rather than carrying on. The reasoning is that Ted's
trips are dense — a leg, a stay, a conference or a gathering every day or two — so a fortnight of
total silence is the gap *between* trips, spent at home. The travel gap between the two conferences
is still reported, which is the real problem in that schedule.

It is a threshold, and thresholds are arbitrary. Checked against the real itineraries: the longest
in-trip silence in any of the three is six nights (Rush Tours, between the two New York gatherings),
so 14 has room. Alternatives if Ted dislikes it: report the long run anyway and let him live with
it; or treat a return to a home city as the only thing that ends a trip, which does not help here
because that schedule never mentions home.

### D14 — Home does not strand him: a gap out of home is dated by the away end

Away from home, a gap runs from the moment Ted was last accounted for to the moment he has to be
somewhere else, and every day between is part of the problem — checked out of Hamburg on the 7th
with a gathering in Aachen on the 8th, both days are the gap.

Leaving home is not like that (Ted, 2026-08-20). Landing at SJC on Oct 15 with a conference in
North Gower on the 19th, the four days at home are not a problem to solve: the problem is one
journey, on the 19th. Read the other way, the gap spanned every day since he got home — on the
YYZ→SFO/JFall pair, eleven days of amber for one missing flight.

The reason is that **the window is the span of the problem, and time at home is not part of any
problem** — he is home, indefinitely, by choice. Stranded in Aachen with no bed and no way onward,
every one of those days is wrong; eleven days at home, nothing is wrong. So the window is anchored
at the away end: both ends of a home-origin gap are the moment he has to be elsewhere.

Note what this is *not* about: his presence at home is recorded perfectly well — the landing at SJC
is exactly what puts him there, and sets the window's start in the first place. The rule turns on
home being a fine place to be, not on any gap in the data.

This also keeps the problem actionable *longer*, since `relevantUntil()` is the far end of the
window — the missing flight stays on the report until the day he needed to have taken it, rather
than expiring on the day he got home.

Both views special-case the resulting zero-length window: "Nothing booked — needed by Nov 11, 9:00
AM" rather than naming the same date twice.

**Not yet done — the mirror image.** A gap *into* home has the same shape: a conference ending in
Ede on Nov 12 with the next home departure on Dec 1 spans nineteen days. It was left alone because
collapsing that window moves `relevantUntil()` *earlier* — the "no flight home" warning would
vanish the day the conference ends, which is exactly when it matters most. Needs Ted.

## Acceptance examples

These three are the acceptance suite. They are written against `problems()` and survive any
internal restructuring.

### Example A — German Summer (the reported bug)

```
flight  MUC -> HAM             Aug 25
hotel   Hamburg                Aug 25 -> Aug 26
hotel   Soltau                 Aug 26 -> Aug 31
conf    "…" Soltau             Aug 27 -> Aug 30
hotel   Hamburg                Aug 31 -> Sep 7
gather  "…" Aachen             Sep 8
conf    "…" Johannesberg       Sep 10 -> Sep 13
flight  FRA -> SFO             Sep 14

EXPECTED
travel  Hamburg      -> Soltau        Aug 26 -> Aug 26
travel  Soltau       -> Hamburg       Aug 31 -> Aug 31
travel  Hamburg      -> Aachen        Sep 7  -> Sep 8
travel  Aachen       -> Johannesberg  Sep 8  -> Sep 10
travel  Johannesberg -> Frankfurt     Sep 13 -> Sep 14
hotel   Hamburg                       Sep 7  -> Sep 8
hotel   Aachen                        Sep 8  -> Sep 10
hotel   Johannesberg                  Sep 10 -> Sep 14
```

The last row merges the two Ted listed separately (Sep 10–13 and the night of Sep 13) per D5.

### Example B — European Conferences: clean

The 28-row itinerary Ted supplied on 2026-08-20 (SFO→FRA red-eye Jun 6, through Cologne, Gembloux,
Antwerp, Brussels, Oxfordshire, London, Leipzig, Munich, MUC→SFO Jun 30). **Expected: no problems
of any kind.** This is the most valuable test in the suite — it is the one that catches phantoms,
and it exercises D3 (four facts on Jun 22, five on Jun 17), D6 (the red-eye), D7 (the home nights),
and the contiguous-stay half of D8 twice.

### Example C — Rush Tours

```
2026-07-27 8:55 AM | 2026-07-27 5:45 PM | Flight    | SFO -> JFK
2026-07-28 7:30 PM | 2026-07-28 11:30 PM| Gathering | "Rush", New York
2026-08-03 6:30 PM | 2026-08-03 11:30 PM| Gathering | "Rush", New York
2026-08-04 2:55 PM | 2026-08-04 6:24 PM | Flight    | JFK -> SFO
2026-08-08 8:10 AM | 2026-08-08 4:10 PM | Flight    | SFO -> YYZ
2026-08-08 | 2026-08-14 | Hotel     | "Oak House", Toronto
2026-08-08 | 2026-08-14 | Hotel     | "Doubletree by Hilton", Toronto
2026-08-09 … 2026-08-13 | Gathering | "Rush", Toronto  (three evenings)
2026-08-14 5:16 PM | 2026-08-14 7:52 PM | Flight    | YYZ -> SFO

EXPECTED
hotel      New York             Jul 27 -> Aug 4
duplicate  Toronto              Aug 8  -> Aug 14   "Oak House" + "Doubletree by Hilton"
```

No missing travel: `JFK → New York` and `YYZ → Toronto` both resolve, per D4. Two gatherings in New
York produce one missing-hotel run, not two, per D2/D5. Aug 4–8 is at home, per D7.

## Slices

1. **Acceptance tests, red.** All three examples above, plus a compact test-data builder so an
   itinerary reads like the tables here rather than 60 lines of event construction. Written against
   `problems()`.
2. **`ScheduleTimeline`.** The ordered presence sequence, with its own unit tests for D3 ordering,
   D1 last-known-location, and home-city collapsing. Nothing consumes it yet.
3. **Missing travel via the walk** (D2). Deletes `detectMissingTravel`,
   `detectMissingTravelToFromConferences`, and `deduplicateMissingTravel`.
4. **Missing hotel via the night sweep** (D5, D6). Deletes the `conferenceElsewhere` /
   `hotelInConferenceCity` heuristics.
5. **`DuplicateHotel`** (D8). New sealed variant, so the exhaustive switches break compilation until
   each decides: `ProblemBand.from` needs a new `Lane`, and `ScheduleProblemsRenderer` a new card
   column. `HotelStay` starts carrying `hotelName` and `HotelBookingId`. Needs a `relevantUntil()`
   — the last night of the overlap, anchored `ANYWHERE_ON_EARTH` like `MissingHotel`.
6. **`context()` from the timeline** (D12), and the **D9 guard**: a convention test over the
   `EventTypes` registry asserting that every event carrying an `Address` or an `AirportCode` is
   handled by `ScheduleGapProjector`. `Event` is deliberately not sealed, so this is the only
   available catch, and it fails the moment a located entry kind is added without a presence fact.
7. **Re-judge the existing tests.** `ScheduleGapProjectorTest` is ~1400 lines and several of its
   nests encode the bug rather than the behaviour — the whole `MissingTravelToFromConference` and
   `MissingTravelDeduplication` nests exist to pin flaws 3 and 4 in place. Each nest is kept,
   rewritten to the new semantics, or deleted with a note saying which flaw it pinned. This is a
   review step with Ted, not a mechanical one.

Slices 3–5 each need mutation-verification per standing practice, and `docs/Backlog.md` updated on
completion.

## What the re-judged tests turned out to be

Slice 7 expected a bloodbath and got six tests, every one of them encoding an assumption the model
no longer makes:

- **Four** assumed that a conference ending, or a stay in another city, carries Ted back to where
  he flew in. `conferenceInDifferentCityExcludesOverlappingNightsFromArrivalCity` expected a London
  bed on the night the Steventon conference ended, with no leg to London until the next morning —
  a bed he had no way to reach. Rewritten to the new semantics.
- **Two** cast every problem in the list to `MissingHotel`, and now trip over the (correct) travel
  gap between two conferences in different cities with nothing booked between them. Filtered by
  type; their actual claims were untouched.

The `MissingTravelToFromConference` and `MissingTravelDeduplication` nests — the ones expected to be
deleted wholesale — passed unchanged. Their fixtures are small enough that the old detector's
unbounded nearest-leg search and the new adjacency walk agree; it took a whole itinerary to tell
them apart, which is the argument for the acceptance suite in one sentence.

## Open

- **The mirror image of D14**: a gap *into* home still spans every day from the last away fact to
  the next home departure. Collapsing it costs `relevantUntil()`, so it needs a decision, not just
  a symmetric edit.
- Whether the problem calendar's Travel lane needs revisiting once the phantom bands stop being
  drawn — far fewer bands, and the ones that remain are real.
- `DifferentCityConflict` still ignores private events, unlike `SchedulingConflict`, which now
  includes them. Nothing about the detection resists them — clearing one of these is keyed to a
  `GatheringId`, and a private event has no such id to record against.
