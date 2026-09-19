# Plan: Change a private event's matching location

> **Status: `shipped`** — requested, designed and built 2026-09-18 (Ted). One new event, one new
> projector branch, one new page, one new link. The rest of the private-event edit flow
> (`ChangePrivateEventPlan.md` slice 2) stays deferred, deliberately — see D2.

## Context

A conference and hotel in **Lone Tree, CO**; a dinner with friends in **Centennial, CO**, under four
miles away. `ScheduleGapProjector` makes a private event an `Occupancy` exactly as it makes a
gathering one, so the dinner asserts Ted is in Centennial that evening, and `/schedule-problems`
raises a `MissingTravel` row: Lone Tree → Centennial.

**One row, not two** — this paragraph claimed a symmetric pair (out *and* back) until the build
disproved it, and the correction is worth keeping because the wrong version is the intuitive one.
An occupancy contributes a rank-2 `REQUIRE` at its start and a rank-0 `LEAVE` at its end
(`ScheduleTimeline:461-464`), and a `LEAVE` asserts nothing about where Ted must be — so nothing
asks for Lone Tree again after the dinner and the return journey is never reported.
`PrivateEventMatchingLocationPropagationTest` pins both halves: that the outbound gap is raised, and
that it is the only one.

It is technically true and it is not useful. The honest fix on the data is *"for scheduling
purposes this venue is in Lone Tree"* — which is exactly what `Address.locationForMatching` already
means, and which `plan-private-event.html:173` already collects, with the hint *"City name used to
match with conferences and travel — edit if auto-fill picks the wrong level."*

**So nothing new is modelled here.** The field exists, the plan form collects it, and
`ScheduleGapProjector:160` already reads it through `Place.of`. The only gap is that a private event
has been immutable since it was planned (`archived/PrivateSocialEventPlan.md` shipped it plan-only;
`ChangePrivateEventPlan.md` slice 1 added cancel). This plan makes that one field changeable and
nothing else.

### The alternative that was rejected

Two `GroundTransferPlanned` entries — a taxi there and a taxi back. Technically correct, and wrong
twice over: a ground transfer cannot name a private event as either endpoint (`TransferEndpointProjector`
builds options from `FlightBooked`, `TrainBooked` and `HotelBooked` only), and a four-mile taxi
inside a trip is the kind of detail the year overview already drops on purpose — *"they happen
'inside' the overall trip"* (CLAUDE.md, "Zooming out is lossy on purpose").

## Decisions

### D1 — A field-specific event, not a full snapshot

`PrivateEventMatchingLocationChanged(PrivateEventId, String locationForMatching)`.

**This is what H1 already asks for**, and the first draft of this plan argued against it without
citing it — the mistake to not repeat. H1: *"Prefer the smallest delta events over full-snapshot
deltas when feasible… more events that are more fine-grained are preferred over larger CRUD-like
events."* It also names the reason the codebase is full of big ones, which is exactly the situation
here: *"this is due to the edit screens being generic, allowing any kind of change (e.g.,
`FlightChanged` instead of `FlightDepartureTimeChanged`) and **not** because it represents a
best-practice for Event-Sourcing."*

`ChangePrivateEventPlan.md` slice 2 specifies a full-snapshot `PrivateEventChanged`, and the draft
argued the new event duplicated it and should wait. **Ted's counter-argument won — it is the house
heuristic, and it is measurable besides:**

> *"more precise events are BETTER for projections, because none of the projections except for
> Schedule Problems should care about the matching location — so in fact fewer projections will need
> to be changed."*

Counted, of the six `src/main` consumers of `PrivateEventPlanned`:

| Projector | Address fields read |
|---|---|
| `ScheduleGapProjector:160` | **`locationForMatching`** (via `Place.of`) |
| `ItineraryProjector:253` | `city`, `country` |
| `PrivateEventDetailsViewProjector:42` | `city`, `country` |
| `PublicCalendarProjector:277` | `city`, `country` (via `cityCountry`) |
| `PrivateEventCalendarProjector:46` | `city`, `country` (via `EventCalendarSubtitle:26`) |
| `PlannedPrivateEventsProjector:47-51` | `street`, `city`, `region`, `postalCode`, `country` |

**One of six.** Note the last row especially: the list projector reads four raw address fields and
still never touches `locationForMatching`. So this event needs one new branch where
`PrivateEventChanged` would need six.

The "it is CRUD, not a fact" objection was also raised and withdrawn: a full-snapshot
`PrivateEventChanged` is *more* CRUD, not less, and this event at least names which fact changed.

### D2 — Decomposing the rest of the edit flow is deferred

The logical end of D1 is that slice 2 becomes `PrivateEventRescheduled`, `PrivateEventVenueCorrected`,
`PrivateEventRetitled` and so on, each reaching the subset of projectors that reads those fields.
**Not now** (Ted, 2026-09-18): *"defer decomposing the rest until the specific reasons for change
become more clear from additional use."* Note what this is: a deliberate **pause on H1**, not a
neutral scope cut — H1 prefers the fine-grained events, and the reasons-for-change that would name
them are guesses until a second one actually shows up. `ChangePrivateEventPlan.md` slice 2 stays as
written and unbuilt.

**What slice 2 must do, and it is one line: `PrivateEventEditView` folds
`PrivateEventMatchingLocationChanged`.** Then its form prefills the corrected location, the snapshot
it writes back carries that correction forward, and nothing is lost. This is **R8a**, added
2026-09-18 and prompted by exactly this question (Ted): *"Any and all edit screens must take their
current state from all relevant events in the event log."*

**An earlier draft of this section called the snapshot itself a trap. That was wrong** and is
recorded here so nobody re-derives it: a full-snapshot `*Changed` written from correctly-folded state
reverts nothing. The hazard is only ever an **under-folded read model**, and it applies here because
A5 gives slice 2 its own projector (H2, one projector per web view) — so nothing compiler-forces the
new class to handle an event `PrivateEventMatchingLocationViewProjector` already handles. Guard it
with a propagation case, in the shape of `PrivateEventCancellationPropagationTest`; nothing else
fails when the branch is missing.

### D3 — One projector branch, and it rebuilds the occupancy

`ScheduleGapProjector` gains:

```java
case PrivateEventMatchingLocationChanged e -> privateEvents.computeIfPresent(e.privateEventId(),
        (id, occupancy) -> occupancy.inCity(new Place(e.locationForMatching()).value()));
```

`computeIfPresent` rather than `put` makes the ordering safe for free: an override arriving after a
cancellation is a no-op, and one arriving before the plan event (which cannot happen, but replay
order is not this projector's to assume) does not conjure an occupancy out of nothing.

`Occupancy.inCity` is a new copy-with on the record, rather than five positional arguments at the
call site — the record has five components and two of them are `ZonedTimestamp`s that would read
identically if transposed.

**Through `Place`, and the first version was not** (corrected 2026-09-18 after a review). It shipped
as `occupancy.inCity(e.locationForMatching())`, which was not wrong — the event's compact
constructor trims, exactly as `Address` does for the plan branch above, so the two paths produced
identical strings and no test could tell them apart. Three things decided it anyway:

- **Every other city in the file goes through `Place`.** Counted: 14 values become an
  `Occupancy`/`Stay`/`Movement` city in `ScheduleGapProjector`, and this was the only one that did
  not. A lone exception is read as an oversight whether or not it is one.
- **It is insurance against `Place`'s normalization being strengthened, which is the likely
  direction.** CLAUDE.md names the open gap — `trim()` does not remove U+00A0 — and `Place`'s
  compact constructor is where a fix for it would go. Today `Address` would pick that up and a
  re-matched city would not.
- **The failure it would cause is event 92's, in the one field most exposed to it.** A city that
  renders identically and compares as somewhere else grows back the exact `MissingTravel` *and*
  `MissingHotel` rows this event exists to remove (D8) — and this form is typed on the iPad, which
  is where the original trailing space came from.

**Note what is deliberately not added: a test.** The two paths normalize identically today, so no
assertion can distinguish them, and a test that no production mutation can fail is a test that pins
nothing — the same reason `aPrivateEventThatHasAlreadyHappenedIsStillRematchable` was deleted from
`ChangePrivateEventMatchingLocationCommandTest` in the same pass. The comment on the branch carries
the reason instead.

The rejected alternative was a fourth `Place.of(PrivateEventMatchingLocationChanged)` factory,
symmetric with the `Address`/`TrainStationAddress`/`AirportCode` three. `Place.of` exists to answer
*which field of this thing becomes the place*, and here the event has one field and it **is** the
place — so the factory answers a question nobody can get wrong, and earns less than the API costs.

### D4 — Blank is refused, not treated as "revert"

`""` would be a natural "clear the override and go back to the typed city", and it cannot work here:
the projector holds a resolved `Occupancy` carrying one `city` string, not the original `Address`, so
it has nothing to revert *to* without re-reading the plan event. Refusing is honest and the undo is
typing `Centennial` back in, which costs nothing.

Refused in the **command**, not in the record — CLAUDE.md, "A city that is really a station is
rejected on the write path, never in the record": a rule in a compact constructor applies
retroactively to the whole log and one bad old row stops a replay and a restore dead.

**Not** wired to `EnteredLocation`'s venue-word rules, and that is a scope decision rather than an
oversight — see O1.

### D5 — The link lives on the list row, and the problem report cannot carry it

`/planned-private-events` row, appended **below** `Cancel`, which is the slot
`PlannedPrivateEventsListPlan.md` explicitly reserved:

> *"Cancel goes first in the cell, and that ordering is load-bearing: the cell is a column flex, so
> the edit flow's future 'Edit' link is appended below this one rather than above it, and no control
> that is here today moves when it arrives."*

A plain text link reading **"Match location"**. No icon: a pencil means edit and nothing else
(CLAUDE.md, "an icon means one thing, app-wide"), and this is not the general edit.

**Not** on the calendar or the itinerary — those want the pencil, and the pencil belongs to the
deferred slice 2.

**And not on `/schedule-problems`, which is where it would be most useful.** `ScheduleProblem.MissingTravel`
carries two city strings and two timestamps and **no ids** (`ScheduleProblem.java:45-50`), so a fix
link cannot say which private event to re-match. That is the cause-linking gap, and this feature is
its second and sharper motivating case — written up in `ProblemCauseLinkingPlan.md`.

### D6 — Its own view, not a widened `PrivateEventDetailsView`

`PrivateEventMatchingLocationView` + its own projector. `PlannedPrivateEventsListPlan.md` settled
this shape already (*"that record is the cancel page's… giving it three fields it must then not
render is a value carried and stripped later"*), and A5 of `ChangePrivateEventPlan.md` calls the same
call "a correction, not a preference".

This makes it the **second** read model in the tree to carry `locationForMatching` for a private
event. That is fine and honest: this page is a *decision-support* surface — Ted is choosing a value —
so it carries what makes the choice answerable, including the current city it would otherwise match
as (CLAUDE.md, "A recording surface needs no decision-support information", pointing the other way).

### D7 — Additive, no schema movement

New event type at `schema_version` 1. **No schema bump, no upcaster, no migration, backup stays v3** —
the same shape `PrivateEventCancelled` and `TrainCancelled` went in as.

### D8 — `locationForMatching` stays invisible everywhere else

Nothing displays a private event's matching location on the list, the calendar, the itinerary or the
details page. Raised as a concern (a silent field that changes schedule behaviour with no visible
trace) and **dismissed by Ted, 2026-09-18**: *"since the entire reason for 'location for matching' is
the schedule problems, this is unsurprising and I'm not too concerned."* The visible effect is on
`/schedule-problems`, which is the thing he was looking at. Do not add a chip for it without asking.

**The effect is on two rows, not one, and this said "the `/schedule-problems` row" until a review on
2026-09-18 measured it.** The city the override sets is read by both detectors in
`ScheduleTimeline`, so re-matching the dinner to Lone Tree:

- removes the `MissingTravel` row — the one the feature was asked for; and
- **merges two `MissingHotel` rows back into one.** `missingHotels()` splits a run of uncovered
  nights where the *city* changes, because the row has to say where to book, so a dinner in the next
  town turned the conference's nights into `Lone Tree` (Oct 1–2) **plus a spurious `Centennial`
  (Oct 2–3) demanding a bed in a town Ted is not sleeping in** — and carrying no conference name,
  because a private event does not lend one.

The hotel half is arguably the bigger win and it is the easier one to lose: nothing outside
`PrivateEventMatchingLocationPropagationTest` reaches this branch, and that test asserted only on
`MissingTravel` until the same review. It covers both now.

## Open

### O1 — The matching location is unchecked for station-shaped text

`EnteredLocation` refuses `"Frankfurt (Main) Hbf"` in a city box from the four booking commands, and
a matching location is *exactly* as compared as a city — `"Hbf"` in this field matches nothing and
the report silently invents a gap. It is **not** checked here, for consistency rather than laziness:
`PlanPrivateEventCommand` does not check it either (CLAUDE.md: *"Not wired to gatherings, conferences
or private events"*), and guarding the edit while leaving the plan form open would be two
vocabularies for one rule.

Doing it properly means a city-only path through `EnteredLocation` — its rule 1 demands a venue name,
and a private event's is optional — and covering `PlanPrivateEventCommand` in the same change. Worth
asking about; not worth doing unilaterally inside this one.

## What was built

- `PrivateEventMatchingLocationChanged` (domain), registered in `EventTypes`, with two golden samples.
- `ChangePrivateEventMatchingLocationCommand` / `Context`, refusing `PrivateEventNotFound` and
  `InvalidMatchingLocation`.
- `ChangePrivateEventMatchingLocation` (application), folding existence from the event stream per R1,
  through `CommandExecutor`.
- `PrivateEventMatchingLocationView` + `PrivateEventMatchingLocationViewProjector`.
- `ChangePrivateEventMatchingLocationController` + `Request` + `change-private-event-matching-location.html`.
- `ScheduleGapProjector` branch; `ScheduleTimeline.Occupancy.inCity`.
- `SecurityConfig` matcher `/planned-private-events/*/matching-location` + `AuthorizationMatrixTest` row.
- The `Match location` link on `/planned-private-events`.

**`LocatedEventsReachScheduleProblemsTest` cannot guard the projector branch** — that test walks
events carrying an `Address`, and this one carries a bare `String`, exactly as `TrainCancelled`
carries no `TrainStationAddress`. `PrivateEventMatchingLocationPropagationTest` is the only thing
standing between a deleted branch and a silent regression — **so it has to cover both detectors that
read the city**, the missing-travel walk *and* the missing-hotel sweep. It asserted only on
`MissingTravel` until a review the same day; see D8 for what the second one does.
