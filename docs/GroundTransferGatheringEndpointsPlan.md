# Plan: gatherings and private events as ground-transfer endpoints

> **Status: `open`** — requested by Ted 2026-10-06, nothing built. Written as a plan only; the open
> decisions in §4 want Ted before slice 1.

## 1. Context

The ground-transfer form offers three kinds of endpoint: a flight leg's airport, a train station,
and a hotel (`TransferEndpointProjector`, `GroundTransferEndpointResolver`; D12 forbids free text).
A gathering or a private event cannot be picked, so the ride to a dinner or a talk cannot be
recorded.

It was noticed on 2026-10-06 while fixing the date rule (`PlanGroundTransferCommand`,
`InvalidGroundTransferDate`). Ted had asked for a rule that refuses endpoints days apart; the
example that argued against it — a mid-stay ride from a hotel to a gathering — could not happen
because the gathering cannot be chosen. Two earlier notes record the same gap from the other side:
`PrivateEventMatchingLocationPlan.md` rejected "a taxi there and a taxi back" because "a ground
transfer cannot name a private event as either endpoint", and `ScheduleGapProjector` makes both
kinds an `Occupancy`, so `/schedule-problems` raises a `MissingTravel` row to each whose fix link
lands on a form that cannot express it. That is the same defect `archived/GroundTransferEndpointReadModelPlan.md`
fixed for stations.

## 2. Design

Follow the station pattern exactly (D4, D5, D7), one read model fact per end.

- **Two ends per event, like a hotel.** You *arrive at* a gathering at its start and *leave from* it
  at its end. New `TransferEnd` values `GATHERING_START` (a destination, verb "starts") and
  `GATHERING_END` (an origin, verb "ends"), and the same pair for a private event. `isOrigin()`
  grows the two origin cases.
- **Tokens:** `gathering:<id>` and `private-event:<id>`. One place, so no end in the token (D7); the
  role decides the moment, as with a hotel. `placeToken` already lower-cases non-airport tokens.
- **Rows** come from `TransferEndpointProjector`: `GatheringPlanned`/`GatheringChanged` put both
  rows, `PrivateEventPlanned` and `PrivateEventMatchingLocationChanged` likewise, and the
  cancellations remove both (as for a hotel). A cancelled event is no longer somewhere to go.
- **Resolution** snapshots the venue name and `Address` verbatim into the command, `locationForMatching`
  included, so the schedule can match the transfer to the occupancy it joins. The zone is the event's
  own `startsAt.zone()`, which cannot fail, as for a station.
- **Offered until** the event's own end-of-day, the same "today or later in the endpoint's zone" rule
  (D10/D14) as every other kind.
- **Label:** `Venue — City · starts Sat Sep 19, 7:00 PM`. A gathering's title is public; a private
  event's is not, so see §3.

## 3. Redaction: this is the part to get right

A transfer is published to anonymous viewers as its route (two city names) and nothing else:
`PublicCalendarProjector` reads the endpoints and never `name` or `mode`. Both new kinds already put
a city on the public calendar (a gathering in full, a private event as `Busy` with city/country), so
a transfer "to Centennial" adds no city a stranger could not read. It does add a **connection**: a
public transfer line ending at the city of a private event, on the same day. That is the one thing
to ask Ted about, not to assume (§4, D-R).

Rules that carry over regardless: the private event's **title never** reaches a label that an
anonymous viewer can see; the venue name rides on the event as private, as a hotel's does. Needs both
tiers (CLAUDE.md "Redaction", rule 5): a case in `PublicCalendarProjectorTest` and one in
`CalendarRedactionSecurityTest` built from real events through the real projector.

## 4. Decisions for Ted

- **D-H: the hotel moment, and the date rule shipped 2026-10-06.** That rule refuses endpoints more
  than 24 hours apart and a date matching neither end. A mid-stay ride (stay Sep 13-18, dinner
  Sep 15) goes hotel check-out Sep 18 → dinner Sep 15, which is 3 days apart, so **the rule would
  refuse the very ride this feature exists to record.** The two have to be reconciled: exempt hotel
  ends from the pair check, drop the hotel moment, or let a hotel offer the typed date's own day.
  Ted chose "check hotels too" on 2026-10-06 knowing no mid-stay ride could be entered; this plan is
  where that choice is revisited.
- **D-R: whether a transfer may end at a private event's city on the public calendar** (§3).
- **D-L: what a private event's option label says.** Its title is the only thing a reader
  recognises, but it is private everywhere else a label could leak. Venue and city, like a hotel,
  is the cautious default.
- **D-S: slicing.** Gatherings first, then private events, or both together. Gatherings carry no
  redaction question, so they are the cheaper first slice.

## 5. Slices

1. **Gathering as an endpoint**: the two `TransferEnd` values, projector rows, resolver branch,
   options label, `GroundTransferEndpointChoices` lists, and the date rule's moments.
2. **Private event as an endpoint**, behind D-R and D-L, with both redaction tiers.
3. **Fix links**: `ScheduleProblemsRenderer`/`ProblemFix` offer the transfer for a gap that ends at
   either kind, and `GroundTransferPreselection` preselects it. Add the new paths to
   `ProblemContextFragmentConventionTest` only if a new fix target is introduced; none is expected.

## 6. Tests required

- `TransferEndpointProjectorTest`: planned, changed, cancelled, for each kind; a gathering whose
  dates change replaces its own rows.
- `GroundTransferEndpointOptionsTest`: label wording, offered-until, the two lists.
- `PlanGroundTransferHandlerTest`: each token resolves to venue name and verbatim `Address`; the
  moment lands on the command for each role; a stale id is `UnknownTransferEndpoint`.
- `PlanGroundTransferCommandTest`: the mid-stay ride, according to D-H.
- The golden-sample rule does not apply: no new event is introduced.
- A `@WebMvcTest` that the new options render in both selects.
