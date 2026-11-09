# City-Only Transfer Endpoint — Plan

**Status:** designed 2026-11-09 (Ted), nothing built. Decisions D1–D6 are settled and nothing is
open. D6 widens what reaches the anonymous calendar; read it before building slice 1.

A ground transfer's destination can be a **city with no address yet**. No new event, no schema
bump, no migration, no new route.

**This repo is public.** The case below is written with cities and dates only, which are public by
decision. It names no hotel, no clock time and no transfer mode, and the tests and fixtures for
this slice must do the same (use invented names, as the existing transfer tests do).

## The case (Ted, 2026-11-09)

- A hotel in **Potsdam**, checking out **Nov 19**, with Agile Testing Days in Potsdam Nov 16–19.
- A hotel in **Berlin**, checking in **Nov 21**.
- Ted is going to Berlin on Nov 19 and has not chosen where he sleeps on Nov 19–21.

`/schedule-problems` raises two rows: no travel Potsdam → Berlin, and a missing hotel. By my reading
of `ScheduleTimeline.walk()` the missing hotel is reported in **Potsdam** for Nov 19–21, because
Potsdam is the last place the schedule put him; its Book hotel link would prefill Potsdam.

The ride on the 19th cannot be recorded. The only Berlin option in the To list is the hotel he
reaches on the 21st, and `PlanGroundTransferCommand` refuses two ends whose windows are more than
24 hours apart. The refusal is right: that ride does not go to that hotel. What is missing is a way
to say "Berlin, somewhere".

### What recording it does

A transfer Potsdam → Berlin on Nov 19 enters the timeline as a `Movement`, as every transfer does.
Read from `ScheduleTimeline` (not run):

- Nov 19 holds the check-out (`LEAVE`, rank 0), then the departure (`REQUIRE` Potsdam) and the
  arrival (`ARRIVE` Berlin), both rank 1 and ordered by their own moments.
- The nights of Nov 19 and 20 are then filled as **Berlin**.
- Nov 21's check-in requires Berlin, which is where he is, so the travel row goes.
- The missing-hotel row stays and becomes **Berlin, Nov 19–21**, with a Book hotel link that
  prefills Berlin. That is the true state of the trip.

Slice 1 has a test that proves this through the real projectors rather than leaving it as a reading.

## Decisions (Ted, 2026-11-09)

Mockup, approved the same day: <https://claude.ai/artifact/XEXgiVkhV9xArNH87GxNwQ>. Its source is
`city-only-endpoint-mockup.html` in that session's scratchpad
(`/private/tmp/claude-501/-Users-tedyoung-IdeaProjects-github-jitterted-jittertravel/38e5bbc4-e8ad-4b29-80a0-8a8fdab3b72b/scratchpad/`).
It is deliberately not copied into this repo, because it shows the real bookings. **Build from the
mockup itself**, not from the prose here.

**D1 — The cities offered are derived, never typed.** The To list gains one option per distinct
place among the destination options already in it. Berlin is offered because a Berlin hotel is.
This keeps D3 of `archived/GroundTransferPlan.md` (Ted never types an address) and does not reopen
D12 (no free-text endpoint): D12 refused strings the timeline could never match, and a place taken
from an existing option matches by construction.

It also covers every gap the report can raise. A `MissingTravel` into city B exists only because
something places Ted in B, and every kind that can do that (a stay, a conference, a gathering, a
private event, a departing flight or train) is already a destination option. So whenever there is a
gap to close, the city at its far end is on offer.

**D2 — The words.** A group headed **`City only`**, at the bottom of the To list, with options
reading **`Berlin — no address yet`**. A closed `<select>` shows the option and never its group, so
the option has to say on its own that the address is missing. A bare `Berlin` under a
`Somewhere in…` heading was the alternative and was rejected for that reason.

**D3 — To only.** The From list is unchanged. The mirror case (staying on somewhere unknown, then
riding to a known hotel) stays unrecordable until Ted meets it. Because only one end can be a city,
"both ends are cities" cannot happen from the form, and the date rule always has the origin's window
to hold the date to.

**D4 — The too-far-apart refusal moves under To and names the way out.** Today both date refusals
land under Date with one sentence. The first of them (the two windows are more than 24 hours apart)
cannot be fixed by any date, so its message named a fix that does not exist. New arrangement:

| Refusal | Lands on | Words |
|---|---|---|
| The two places' windows are more than 24 hours apart | `destination` | `These places are more than a day apart — pick a city if the address is not known yet` |
| The date is in neither place's window | `date` (unchanged) | `Date must be a day at one of these places` |

The second sentence replaces `Date must be within a day of both places`, which was never what that
rule checks and reads worse once one end has no window at all.

**D5 — Preselection is unchanged.** A fix link still opens with the gap's own candidates chosen,
and a city is never a candidate. For this case that means To opens on the Berlin hotel and Ted
changes it; if he does not, D4's message says what to do.

**D6 — The stored city is the place the schedule matches on** (was open question O1). A city-only
destination stores the option's place, which is the source's `locationForMatching`, as both `city`
and `locationForMatching`. The option Ted picked, his calendar and the public calendar then all say
the same word.

For Berlin, and for nearly every booking, that is the same string as the address's city and nothing
changes. It matters where they differ: a hotel whose address says one village and whose matching
location says the town next to it, or a private event re-matched to the hotel's city. There Ted
picks `Town — no address yet` and reads `→ Town` everywhere.

**This publishes a matching location for the first time, and that is the decision.** Until now the
public calendar printed an address's `city`, and `ScheduleGapProjector` was the only reader of the
matching location. `PublicCalendarProjector` does not change: it still names `destination.city()`
and nothing else. What changes is that, for a city-only transfer, that field holds the matching
location.

**What that field is.** `locationForMatching` is free text Ted types on the hotel, gathering and
private-event forms ("Location for matching", hinted as a city name). It falls back to the address's
city when left blank, and nothing checks that it names a city. So the public calendar prints
whatever is in it. As used today it is a town name, the same granularity as the hotel cities that
are public by decision. If it is ever used for something finer, such as a neighbourhood, a city-only
transfer derived from that booking would publish it. Slice 1 writes this into `CLAUDE.md`'s
redaction section so the field is known to be publishable.

Two earlier notes this touches. `PrivateEventMatchingLocationPlan.md` records that the matching
location "is displayed nowhere" and that a chip for it needs asking; D6 is that asking, for this one
use, and it displays the value only as a transfer's city. That plan's own open item (the matching
location is not checked for station-shaped text) now has a public consequence as well as a
scheduling one.

Rejected: storing the source address's own city. It publishes nothing new, but Ted could pick
`Town — no address yet` and then read `→ Village` on his own calendar.

The approved mockup says "nothing new becomes public". That is true for the Berlin case and is not
true in general; this decision supersedes that sentence.

## Design

### What is stored

`GroundTransferPlanned`, unchanged. A city-only destination is written as:

- `destinationAirportCode` = `""`
- `destinationName` = `""`
- `destination` = an `Address` with no street and no postal code, the region and country of the
  option it was derived from, and that option's `locationForMatching` as both `city` and
  `locationForMatching` (D6)

`TransferEndpoint`'s javadoc says exactly one of code and name is non-blank. That stops being true
and must be reworded: both blank now means "the city alone".

Every reader already copes with this shape, which is why nothing else moves:

- `TransferEndpointLabel.ownerLabel` falls back to `address.city()` when code and name are blank,
  so the owner calendar, the itinerary and the cancel page read `… → Berlin`.
- `TransferEndpointLabel.publicLabel` never takes a name; it prints `CityLabel` of the address,
  `Berlin, Germany`.
- `ScheduleGapProjector` reads `Place.of(e.destination())`, the matching location.
- `PlacesUsedProjector` reads the address's country and region, which are still there.

No stored row is rewritten and no new shape is added to the payload, so backup files stay
compatible and `GoldenEventDeserializationTest` needs no new case.

### The token

`city:` followed by the token of the option the city was derived from, for example
`city:hotel:<bookingId>`. It reads as "the city this hotel is in". The source is the earliest
destination option in that place.

`GroundTransferEndpointResolver.resolve` gains one branch: strip the prefix, resolve the inner
token with the existing code for its kind, then drop the code, the name, the street and the postal
code, and set the city to the matching location (D6). All six existing kinds are reused; nothing
new is looked up.

- A nested `city:city:…` is refused as `UnknownTransferEndpoint`.
- A source that has gone between GET and POST (the hotel was cancelled) is refused with the city's
  own words, `That city is no longer offered — pick again`, not with a sentence about a hotel Ted
  did not pick.
- **`destinationWindow("city:…")` must stay empty.** `TransferEndpointProjector.windowOf` matches
  tokens exactly, so it already is. A change that "helpfully" strips the prefix there would hand the
  city the Berlin hotel's window and bring the 24-hour refusal straight back. A test pins it.
- `placeToken` needs no change. `city:hotel:X` and `hotel:X` are two different tokens, so "from that
  hotel to somewhere else in its city" is allowed, as hotel-to-hotel within one city is today.

### The option list

`GroundTransferEndpointOptions.choicesAt` builds the list, because ordering and labels are that
class's job and it holds the rows' instants:

- one option per distinct `Place` among the destination rows that passed the day filter, compared
  with `Place.matches`
- ordered by the earliest row in each place, so the cities read in the trip's own order
- label `<place> — no address yet`; `city` is the place; `prefillDate` and `prefillTime` are blank

`GroundTransferEndpointChoices` carries it as its own list, **outside `destinations()`**. That is
what keeps D5 true with no change to `theOnlyCandidate`, and keeps `isEmpty()` meaning "nothing
recorded to travel between".

A city is offered exactly as long as some real option in it is, with no rule of its own.

### The form

`plan-ground-transfer.html` gains one `<optgroup label="City only">` at the end of the To select,
shown only when the list is non-empty. Nothing else on the page changes, including the hint under
To.

The inline script already does the right thing: an option with no `data-date` changes nothing.
Picking a city leaves the date and both times as they were.

### The refusals (D4)

- `PlanGroundTransferCommand` throws a new domain exception for the first rule,
  `TransferEndpointsTooFarApart`, and keeps `InvalidGroundTransferDate` for the second with its new
  sentence. Both stay in the compact constructor: neither needs state, so neither reaches the
  command log.
- The domain message says only what is wrong (`Places are more than a day apart`).
  `PlanGroundTransferController` supplies the sentence in D4, because "pick a city" is a fact about
  the form and the domain does not know the form has one. `ZoneResolutionException` on the same
  controller is the precedent.
- The controller maps it to `destination`, where `SameTransferEndpoints` already lands, so the
  `<span class="error">` it needs is already in the template.

## Slices

Both ship in one push. Slice 2's message points at the option slice 1 adds, so slice 2 never goes
first.

### Slice 1 — the city option

1. `GroundTransferEndpointResolver`: the `city:` branch, its stale-source message, the javadoc
   table.
2. `GroundTransferEndpointOptions` and `GroundTransferEndpointChoices`: the destination-cities
   list.
3. `plan-ground-transfer.html`: the optgroup.
4. Javadoc that goes stale: `TransferEndpoint` (the exactly-one invariant),
   `PlanGroundTransferRequest` and `TransferEndpointOption` (the token examples).
5. `CLAUDE.md`, redaction section, "Public by decision": a city-only transfer's city is the
   matching location of the booking it was derived from (D6), with the free-text caveat. Same
   change as the code, so the rule and the behaviour arrive together.

Tests, each at the tier that runs the real code:

- `GroundTransferEndpointResolverTest` (new, or the handler test if the resolver has none of its
  own): a `city:` token for each of the six kinds resolves to a blank code, a blank name and an
  address with no street; nested and stale tokens are refused with their own words. One case has a
  source whose address city and matching location differ, and asserts the stored `city` is the
  matching location (D6).
- `PlanGroundTransferHandlerTest`: a city destination reaches the command with a null destination
  window and the origin's window intact.
- `PlanGroundTransferCommandTest`: with only the origin's window known, a date inside the stay is
  accepted and one outside is refused. This is existing behaviour that nothing exercised from the
  form until now.
- `GroundTransferEndpointOptionsTest`: one city per place, in trip order, with the exact label; a
  city disappears with its last real option; two hotels in one city give one city.
- `GroundTransferEndpointChoicesTest`: with cities present, `destinationFor` still answers with the
  real option.
- `EndpointsCloseScheduleGapsTest`: the case above through the real projectors. The travel gap
  closes, and the missing hotel moves to the far city for the same nights.
- `GroundTransferCalendarProjectorTest` and the itinerary's: the route reads `<origin> → <city>`.
- **Redaction, both tiers.** `PublicCalendarProjector` does not change, but a new shape of event
  reaches it. `PublicCalendarProjectorTest`: the public entry is `Ground transfer` with
  `<city>, <country> → <city>, <country>` and contains neither the origin's name nor the mode.
  `CalendarRedactionSecurityTest`: the same absence in the rendered anonymous body, from real events
  through the real projector. One projector case uses a destination whose city is a matching
  location that differs from the source's address city, so the test states in so many words which
  value D6 makes public.
- `PlanGroundTransferWebIntegrationTest`: the rendered To select contains the whole
  `<optgroup label="City only">` and the whole option element, value included.
- `GroundTransferPrefillJsTest` (`js` tier): picking a city leaves the date and both times alone.

### Slice 2 — the refusal split

1. `TransferEndpointsTooFarApart` in `domain`, thrown from `PlanGroundTransferCommand`.
2. The second rule's new sentence.
3. `PlanGroundTransferController`: one new `catch`, mapped to `destination` with D4's sentence.

Tests, on the three-tier standard:

- `PlanGroundTransferCommandTest`: each rule throws its own type, with the 24-hour boundary.
- `PlanGroundTransferControllerTest`: one new row in the `exception → field` table.
- `PlanGroundTransferWebIntegrationTest`: the whole `<span class="error">…</span>` under To.
  Thymeleaf escapes nothing in D4's sentence (no apostrophes, by design).

Mutation-verify both slices before the commit; `-Ppit-spring` narrowed to the controller before the
push.

## Not in this slice

- **A city at the From end** (D3).
- **A bare venue with an address**, the "venue endpoint" `archived/GroundTransferPlan.md` deferred.
  This plan is narrower: a city, not a building.
- **Changing a transfer.** There is still no change flow, so once the Berlin hotel is booked the
  transfer goes on reading `→ Berlin`. Naming the hotel means cancelling it and entering it again.
  Nothing requires that: the schedule matches on the city.
- **Preselection that knows a pair is too far apart.** When a gap leaves exactly one candidate at
  each end and their windows are more than a day apart, the fix link opens on a pair that is
  certain to be refused. D4 makes that refusal useful; it does not prevent it. Worth a look once
  this has been used.

## Nothing to do outside the repo

No environment variable, no `/admin` run, no `Pre-Push-Tasks.md` box. After the rollout Ted records
the Nov 19 transfer himself.
