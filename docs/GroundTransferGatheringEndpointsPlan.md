# Plan: gatherings, private events and conference venues as ground-transfer endpoints

> **Status: `built, not pushed`** — requested by Ted 2026-10-06. All four slices built and the first
> three committed the same day (gatherings plus the window rule, conference venues, private events);
> slice 4 (fix links) turned out to need no new code — see "Slice 4 as built". Pre-push gates
> (`-Pjs-tests`, `-Ppit-spring`) not yet run.

## 1. Context

The ground-transfer form offers three kinds of endpoint: a flight leg's airport, a train station,
and a hotel (`TransferEndpointProjector`, `GroundTransferEndpointResolver`; D12 forbids free text).
A gathering, a private event or a conference venue cannot be picked, so the ride to a dinner or a
talk cannot be recorded.

It was noticed on 2026-10-06 while fixing the date rule (`PlanGroundTransferCommand`,
`InvalidGroundTransferDate`). Ted had asked for a rule that refuses endpoints days apart; the
example that argued against it — a mid-stay ride from a hotel to a gathering — could not happen
because the gathering cannot be chosen. Two earlier notes record the same gap from the other side:
`PrivateEventMatchingLocationPlan.md` rejected "a taxi there and a taxi back" because "a ground
transfer cannot name a private event as either endpoint", and `ScheduleGapProjector` makes
gatherings, private events and conferences an `Occupancy`, so `/schedule-problems` raises a
`MissingTravel` row to each whose fix link lands on a form that cannot express it. That is the same
defect `archived/GroundTransferEndpointReadModelPlan.md` fixed for stations.

## 2. Design

Follow the station pattern (D4, D5, D7), one read-model fact per end.

- **Two ends per event, like a hotel.** You *arrive at* an event at its start and *leave from* it at
  its end. New `TransferEnd` values `GATHERING_START`/`GATHERING_END`, `PRIVATE_EVENT_START`/
  `PRIVATE_EVENT_END` and `CONFERENCE_START`/`CONFERENCE_END` (starts are destinations, ends are
  origins; `isOrigin()` grows the three origin cases — consider a field on the enum rather than a
  longer `||` chain).
- **Tokens:** `gathering:<id>`, `private-event:<id>`, `conference:<id>`. One place, so no end in
  the token (D7); the role decides the moment, as with a hotel.
- **Rows** come from `TransferEndpointProjector`, one *event's* pair of rows each:
  - Gathering: `GatheringPlanned` puts both; `GatheringChanged` replaces them. **Gatherings cannot
    be cancelled** — there is no `GatheringCancelled` — so there is no removal case.
  - Private event: `PrivateEventPlanned` puts both; `PrivateEventCancelled` removes both.
    **`PrivateEventMatchingLocationChanged` amends the existing event** (it carries only the id and
    `locationForMatching`): the projector keeps the row's other fields and **replaces the city used
    for matching**. It does not create rows.
  - Conference: from the conference events that carry the venue (`ConferencePlanned`,
    `venueAddress`, start/end dates); removed when the conference is cancelled or dropped. Which
    events apply is to be read from the conference projector before slice 3 starts.
- **Resolution** snapshots the venue name and `Address` into the command, so the schedule can match
  the transfer to the occupancy it joins. For a private event the matching city is its
  `locationForMatching`. The zone is the event's own, which cannot fail, as for a station.
- **Labels (Ted, 2026-10-06):**
  - Gathering: its **title** when present, else venue — `Title — City · starts Sat Sep 19, 7:00 PM`.
  - Private event: **city / state / country only**, never title or venue.
  - Conference: name and city (public by decision).
- **Offered until** the event's own end-of-day, the "today or later in the endpoint's zone" rule
  (D10/D14) as for every other kind.
- **Recorded transfers are snapshots** of the event as it stood when the transfer was created
  (Ted, 2026-10-06). If an event later moves significantly, the mismatch is a *schedule problem*
  for `/schedule-problems` to raise, not something to rewrite on the transfer.

## 3. The date rule: endpoints carry a window, not only a moment

The rule shipped 2026-10-06 compares *moments*: two known moments must be within 24 hours, and the
typed date must be one moment's day. A hotel's single "moment" is a guess, so a mid-stay ride
(stay Sep 13–18, dinner Sep 15) would be refused.

**Decision (Ted): a hotel contributes a range, check-in through check-out, not a moment.** Applied
to every endpoint kind, with a flight or train as the degenerate range (start = end):

- each endpoint carries a `window` (start, end) alongside the `moment` that still drives the
  prefill and the label;
- the typed date must fall within **either** window's days, in that window's own zone;
- two windows must be within 24 hours of each other, measured as the **gap between them** (overlap
  is zero);
- gatherings, private events and conferences are ranges too, so a ride *during* a conference works
  without special-casing.

Cost, as asked: `PlanGroundTransferCommand` swaps its two `ZonedTimestamp` moments for two
windows and `requireDateToFitTheEndpoints` is rewritten around them (≈20 lines); `TransferEndpointRow`
gains the window; `GroundTransferEndpointResolver.originMoment/destinationMoment` return windows;
`PlanGroundTransferCommandTest` gains the mid-stay case and the boundary cases. Not large, and
it removes a rule that would otherwise refuse the headline use case.

## 4. Redaction: this is the part to get right

A transfer is published to anonymous viewers as its route (two city names) and nothing else:
`PublicCalendarProjector` reads the endpoints and never `name` or `mode`.

- **Decided (Ted, 2026-10-06): a transfer to or from a private event may reveal city, state and
  country only.** Nothing else about the event — not its title, its venue, its street address, its
  time of day.
- The private event's title never reaches any label an anonymous viewer can see; the venue name
  rides on the event as private, as a hotel's does.
- The public projector must read the transfer's route from the transfer's own endpoints, never from
  the private event, so an owner-facing venue snapshot cannot be carried across (rule 1,
  "don't read it").
- Both tiers (CLAUDE.md "Redaction", rule 5): a case in `PublicCalendarProjectorTest` asserting the
  private venue and title are not emitted, and one in `CalendarRedactionSecurityTest` built from
  real events through the real projector, asserting the rendered anonymous body `doesNotContain`
  them.

## 5. Decisions

| | Decision | Answer |
|---|---|---|
| D-H | Hotel moment vs. date rule | **Range, check-in through check-out (§3)** |
| D-R | Transfer ending at a private event's city on the public calendar | **City/state/country only (§4)** |
| D-L | Private event's label | **City/state/country** |
| D-G | Gathering's label | **Title if present** |
| D-C | Conferences | **In scope, as a third kind** |
| D-S | Slicing | **Gatherings, then conferences, then private events** |

## 6. Slices

1. **Gathering as an endpoint** (and the window rule of §3, since the first useful ride needs it):
   two `TransferEnd` values, projector rows, resolver branch, options label,
   `GroundTransferEndpointChoices` lists, windows on the command.
2. **Conference venue as an endpoint** — same shape, from the conference projector's data.
3. **Private event as an endpoint**, with both redaction tiers and the matching-location amend.
4. **Fix links**: `ScheduleProblemsRenderer`/`ProblemFix` offer the transfer for a gap that ends at
   one of these kinds, and `GroundTransferPreselection` preselects the matching endpoint token.
   Add the new paths to `ProblemContextFragmentConventionTest` only if a new fix target is
   introduced; none is expected.

### Slice 4 as built

**No production code.** The fix link was already offered for every `MissingTravel` gap, and
`GroundTransferPreselection` is kind-agnostic: it asks `GroundTransferEndpointChoices` for the only
candidate in the gap's city on the gap's days, and slices 1-3 had already put the three new kinds in
those pools (origins on the end, destinations on the start). What was missing was proof, so slice 4
is `EndpointsCloseScheduleGapsTest`: the real `ScheduleGapProjector` and the real endpoint read model
fed the same events, asserting that a gap out to, and a gap out of, a gathering, a conference and a
private event each settle on it — a private event under its *matching* city, and two events in the
gap's city settling nothing (the form asks rather than guesses).

**Known limit, unchanged by this work:** a ride *mid-event* (leaving a conference on its second day
for a dinner) preselects the destination but not the origin, because an origin is a candidate only
when its end falls inside the gap's days. The origin is still on the "From" select and the date rule
now accepts the ride. Widening the preselection to a window would change which endpoint counts as the
only candidate for hotels as well, so it is a decision for Ted rather than a part of this plan.

**Decided (Ted, 2026-10-06): leave it as it is.** What he wants eventually is a much smarter
auto-select that works *given the endpoint already chosen* — pick the "From", and the "To" (and the
date) follow from it — rather than a wider candidate rule. Not now; not scheduled.

## 7. Tests required

- `TransferEndpointProjectorTest`: planned, changed, cancelled where cancellation exists, for each
  kind; a gathering whose dates change replaces its own rows; a private event's matching location
  changes the city on its existing rows and creates none.
- `GroundTransferEndpointOptionsTest`: label wording (title present/absent, private event shows no
  title or venue), offered-until, the two lists.
- `PlanGroundTransferHandlerTest`: each token resolves to its name and `Address`; the window lands on
  the command for each role; a stale id is `UnknownTransferEndpoint`.
- `PlanGroundTransferCommandTest`: the mid-stay ride, the day-boundary and 24h-gap cases.
- A `@WebMvcTest` that the new options render in both selects.
- Redaction, both tiers (§4).
- The golden-sample rule does not apply: no new event is introduced.
- Mutation-verify the new `isOrigin()` cases and the window rule.
