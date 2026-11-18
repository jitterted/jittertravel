# Ground Transfer Endpoint References — Plan

*Status: planned 2026-11-17 (Ted), nothing built. Mockup: `docs/mockups/StaleGroundTransfer.html`
(all five parts approved 2026-11-17; it is the spec for every word on these surfaces). Talk write-up of the bug:
`docs/talks/TheFactYouDidntWriteDown.md`.*

**Builds before `CityOnlyTransferEndpointPlan.md`** (Ted, 2026-11-18). That plan, designed
2026-11-09, adds a `City only` destination to the same form and changes the same resolver, command
and event readers; neither plan knew of the other until 2026-11-18. This one ships first. Four
points of contact, each decided in that plan's "After the endpoint references plan" section: a
city-only end's source is `EndpointSource.NONE` (so the resolver test's seven token kinds become
eight); the extracted date rule stays a **per-end** day check, with the two-ends-too-far-apart rule
a separate method only the commands call, because that plan splits them into two refusals; the
Change form (M5) offers the `City only` group too; and the Nov 20 transfer in the Pre-Push box is
the trip that raised that plan.

## The bug

A ground transfer keeps pointing at something that no longer exists. On Nov 20 the transfer from
the palace tour still drives to the first Berlin hotel (seq 242), which was canceled the next day
(seq 251) and replaced by another Berlin hotel (seq 252). Nothing in the app says so. (Hotel names
in this repo are the anonymized ones from the talk notes: Motel One Berlin-Alexanderplatz, replaced
by Leonardo Hotel Berlin Mitte.)

- `/itinerary` renders the transfer's snapshot, which still names the canceled hotel.
- `/schedule-problems` compares cities. Berlin is Berlin, so there is no gap.
- Cancel Hotel cannot warn, because no read model knows which transfers go to that hotel.

**Cause.** The plan form submits a token naming the thing an end was picked from
(`hotel:<bookingId>`, `airport:DEN:<flightId>`, `train:<tripId>:arrival`, …).
`GroundTransferEndpointResolver` copies that thing's name and address into
`GroundTransferPlanned` and **drops the id**. The snapshot was deliberate (*"changing the hotel later
must not silently rewrite a past transfer"*) and stays. Dropping the reference was the mistake: the
snapshot answers "what did I plan", only the reference answers "is it still true".

**Not hotels only.** Seven kinds of thing can be an end, and every one except a bare airport can be
canceled or moved after the transfer is planned.

## Decisions (Ted, 2026-11-17)

| #  | Decision |
|----|----------|
| D1 | **All referenced kinds**, not hotels only: hotel, flight leg, train end, gathering, conference, private event. |
| D2 | **Stale means:** the end is **gone**, or it has **moved off its day**, or (for a flight or train leg) it has **moved off its time**. The time check exists because a moved leg can change what kind of transfer Ted takes. |
| D3 | **No backfill.** Old transfers have no reference and are never reported. After the rollout, Ted cancels and re-plans every upcoming transfer by hand (a `Pre-Push-Tasks.md` box). As of the 2026-11-15 backup that is seq 242 (Nov 20) and seq 255 (Nov 25). A confirmed backfill was considered: a new event, a command, an admin page and a one-off task, to link four ends. Rejected as not worth it. |
| D4 | **Three surfaces:** a new problem on `/schedule-problems` (M1), a consequence line on the cancel pages (M2), and a strip on the transfer's card on `/itinerary` (M3). |
| D5 | **The plan form refuses a time its leg contradicts** (M4). "Otherwise I might accidentally put in the wrong time for the transfer and that's what I see in my calendar." Strict: no margin. |
| D6 | **The fix is Change or Cancel** (M1, fix A). "Change transfer" opens a new **Change Ground Transfer** form (M5) and records `GroundTransferChanged` on the same transfer. Rejected: "replace in one step" (cancel + plan under a new id, the unlinked-replace problem again) and two separate links (the problem stays until the second step is remembered). This builds the Change flow `Cleanup_Tasks.md` deferred. |
| D7 | **The cancel pages read a new small projector**, `TransfersByEndpointProjector`, not `ScheduleGapProjector` (M2). The controllers get one narrow method instead of a dozen-method class they never use. The cost, a second fold of the ground-transfer events, is accepted and guarded by the propagation tests. |
| D8 | **Itinerary marker A** (M3): a white strip inside the card, outlined in the problem amber. Chosen over a strike-through with a chip, and over an amber card (nearly the same colour as the transfer card's own yellow). |
| D9 | **M1's card leads with the change**, with the itinerary's taxi icon and GROUND TRANSFER label on its own line under it, then the route and the time. |

## Model

### The reference — an additive field on `GroundTransferPlanned`

```java
public record GroundTransferPlanned(
        GroundTransferId groundTransferId,
        String originAirportCode, String originName, Address origin,
        String destinationAirportCode, String destinationName, Address destination,
        ZonedTimestamp departsAt, ZonedTimestamp arrivesAt,
        String mode,
        EndpointSource originSource,        // new
        EndpointSource destinationSource    // new
) implements Event { … }

/** What an end was picked from. NONE for a bare airport, and for every transfer planned before this field. */
public record EndpointSource(Kind kind, String id) {
    public enum Kind { NONE, HOTEL, FLIGHT_ARRIVAL, FLIGHT_DEPARTURE,
                       TRAIN_ARRIVAL, TRAIN_DEPARTURE, GATHERING, CONFERENCE, PRIVATE_EVENT }
    public static final EndpointSource NONE = new EndpointSource(Kind.NONE, "");
}
```

- **Additive, the `mode` precedent:** no schema bump, no upcaster, no migration. A stored payload
  without the field binds `null`, and the compact constructor turns that into `NONE`. Backup files
  stay compatible both ways.
- **Not the raw token.** A token in a stored payload would make its format a contract forever (R7).
  The kind is an enum and the id is the id.
- **The leg's end is stored, not implied.** An origin is always an arrival and a destination always
  a departure today, but a stored contract should say what it means, not rely on which side of the
  form it came from.
- **`EndpointSource` is domain** (a fact about the transfer, `java.*` only). The application enum
  `TransferEnd` is the form's row key and stays where it is.
- **The resolver stops discarding the id.** `TransferEndpoint` gains the source, and the command and
  event carry it through. The airport token's flight id becomes load-bearing. This partly reverses
  D3 of `archived/GroundTransferPlan.md` ("a transfer is between places, not between flights"), on
  purpose: the place is still what is matched, and the flight is what can move or disappear.
- **Golden sample.** `GoldenEventDeserializationTest` gets a `GroundTransferPlanned` with sources,
  and keeps the existing one without them. That pair pins both the new shape and the `NONE` default.

### `GroundTransferChanged` — the change event (D6)

A full snapshot, mirroring `HotelChanged`/`TrainChanged`: every field `GroundTransferPlanned` has,
sources included, under the same `GroundTransferId`. It is a new event type, so it gets its own
golden sample. Every projector that handles `GroundTransferPlanned` gets a `put` branch for it:
`ItineraryProjector`, `ScheduleGapProjector`, `PublicCalendarProjector`, the calendar projector,
`TransferEndpointProjector` if it reads transfers, and the new `TransfersByEndpointProjector`. A
lifecycle-propagation test in the style of `HotelCancellationPropagationTest` changes a transfer
and asserts every read model follows, so a projector that misses the branch fails by name.

**Watch the full-snapshot trap:** the change form must round-trip `mode` and both sources, or an
edit silently clears them (the trap `HotelChanged`'s javadoc records for `cancelBy`).

### Staleness — a new `ScheduleProblem`, built in `ScheduleGapProjector`

`ScheduleGapProjector` already folds `GroundTransferPlanned`/`Cancelled` and every lifecycle event
involved (hotels, flights, trains, gatherings, conferences through `ConferenceState`, private
events). So the new problem is built there from events alone (R12), with no second projector.

```java
record StaleGroundTransfer(
        GroundTransferId groundTransferId,
        String route,                    // "Palace tour → Motel One Berlin-Alexanderplatz", the snapshot
        ZonedTimestamp departsAt, ZonedTimestamp arrivesAt,
        TransferSide side,               // ORIGIN or DESTINATION
        Staleness why                    // Gone | OffItsDay | OffItsTime(leg moment now)
) implements ScheduleProblem
```

A transfer with both ends stale produces two problems, one per side, so each one names one fix.

**When an end is stale (D2):**

| Kind | Gone | Off its day | Off its time |
|------|------|-------------|--------------|
| Hotel | `HotelBookingCancelled` | the stay's check-in…check-out no longer covers the transfer's day | — |
| Flight arrival / departure | `FlightCancelled` (any cause, including `REBOOKED`) | — (the time check covers it) | **from** an arrival: the transfer leaves before the leg lands. **To** a departure: the transfer arrives after the leg leaves. |
| Train arrival / departure | `TrainCancelled` | — (the time check covers it) | same as flights |
| Gathering | — (there is no cancel event) | its start…end no longer covers the day | — |
| Conference | `ConferenceCancelled`, declined, or dropped by a rejection | its dates no longer cover the day | — |
| Private event | `PrivateEventCancelled` | its start…end no longer covers the day | — |
| Bare airport / `NONE` | never | never | never |

**One rule per check, shared with the form.** "Off its day" is exactly the rule
`PlanGroundTransferCommand` applies at plan time (`requireDateToFitTheEndpoints`), and "off its
time" is exactly the new rule in D5. Both become domain methods that the commands *and* the stale
check call, so "stale" always means "the form would refuse this transfer if it were entered
today", with no exception to explain. Neither may be copied.

**Plumbing the exhaustive switches.** A new `ScheduleProblem` variant must be answered in each of
these: `ScheduleProblem` permits, `ScheduleGapProjector`'s date switch, `ProblemKey.of`,
`ProblemFix.fixesFor`, `ProblemBand.from` (the problem calendar gets a band on the transfer's day),
`ScheduleProblemsRenderer.Sections`, and `GroundTransferPreselection`/`GroundTransferEndpointChoices`
if they switch over problems. The compiler lists them, which is why they are switches.

- `relevantUntil()` is the transfer's `arrivesAt`: a stale transfer stops mattering once it would
  have happened.
- `ProblemKey`: the transfer id plus the side. No content beyond that, because the kind of
  staleness can change while the problem stays the same one.

### `TransfersByEndpointProjector` — for the cancel pages (D7)

Folds `GroundTransferPlanned`, `GroundTransferChanged` and `GroundTransferCancelled` only, indexed
by `EndpointSource`. One public method:

```java
List<ReferencingTransfer> transfersReferencing(EndpointSource source)  // route + departsAt, for the line
```

It knows nothing about whether the thing is still live. It answers "which transfers point here",
which is all a page about to cancel that thing needs.

## The rule the form gains (D5, M4)

Both rules are data entry: they need only the form's values plus the leg's moment, which the
resolver already supplies as the end's window. So they go in the compact constructors of
`PlanGroundTransferCommand` and the new change command, and are never recorded as a refused command
(the "validate before the WAL" split).

| Leg | Refused when | Message, under | 
|-----|--------------|----------------|
| From a flight or train **arrival** | departure time is before the leg arrives | "Leaves before Train arrives" / "Leaves before Flight arrives", under **Departure time** |
| To a flight or train **departure** | arrival time is after the leg departs | "Arrives after Train departs" / "Arrives after Flight departs", under **Arrival time** |

- **Strict.** Leaving exactly as the leg lands, or arriving exactly as it leaves, is allowed.
- **The message names the kind, not the leg** (Ted, 2026-11-17): a train's description can be long,
  and the message must stay one line under a third-width field. The kind is **capitalized**, as Ted
  wrote it, so it reads as the label of the thing picked.
- **Exactly the same rule as "off its time"** above, from the same method.
- Hotels and events keep the day check only.

## Surfaces

### M1. `/schedule-problems` — a "Stale Ground Transfers" section (approved)

One card per stale end, in the problem amber Missing Travel uses. The card reads, top to bottom:

1. **Title: what changed**, in the M3 sentence ("Motel One Berlin-Alexanderplatz was canceled").
2. The itinerary's **taxi icon and GROUND TRANSFER label**.
3. The route, then the transfer's own day and times.
4. Fixes, as links: **Change transfer →** (M5) and **Cancel transfer →**.

Both fix targets stay OWNER-only and show the problem banner, so
`ProblemContextFragmentConventionTest.TEMPLATE_FOR_PATH` gains the change form's path.

### M2. Cancel pages — the consequence line (approved)

Cancel Hotel, Cancel Flight, Cancel Flight Itinerary, Cancel Train, Cancel Private Event and Decline
Conference each say, above the reason field, which transfers go to or from the thing being canceled:
"1 ground transfer goes to this hotel", one line per transfer, then "It stays planned. Schedule
Problems will list it until you change or cancel it." Nothing is added where there are none. It does not block or change the action. It's amber, not red,
because the transfer can be fixed afterwards.

This is the rule for recording pages working as intended: Ted has already canceled with the
provider, and a recording page shows **consequences**.

### M3. `/itinerary` — the strip on the transfer's card (approved)

The sentences for every case are in the mockup and are the spec; M1's card title reuses them, so
the two pages can't disagree.

- **Family sees it too** (`/itinerary` is FAMILY + OWNER), so the sentence never says "you". Only the
  owner gets the **Change transfer →** link (M5), and family sees no greyed control in its place.
- **Composed in the controller, not the projector.** `ItineraryProjector` folds its own events, and
  per R12 it does not read `ScheduleGapProjector`. `ItineraryController` asks both and hands the
  renderer the stale transfer ids with their reason, the same composition the calendar uses for its
  away band.

### M4. Plan Ground Transfer — refusing a contradicted time (approved)

See "The rule the form gains" above.

### M5. Change Ground Transfer (approved)

The plan form with the transfer's values filled in, at `/ground-transfers/{id}/change`.

- Reached from a fix link, it carries the problem banner, whose second line reads **"Currently:**
  *route · day, times*" (Ted: the page title already says it's a ground transfer).
- An end that is **gone** is left empty ("Choose a place…"), with "Was *name*, which was canceled."
  under it. An end that **moved** stays selected, with "Now checks out Sun, Nov 22" (or the like)
  under it.
- Picking a new end fills in only a time that would otherwise be wrong, as the plan form's script
  does today.
- The button is **Save changes**, matching Change Hotel and Change Train.
- New route: OWNER-only in `SecurityConfig` and a row in `AuthorizationMatrixTest`, in the same
  change.

**A consequence to settle when building, not now:** the transfer card on `/itinerary` and the
calendar puts a cancel bin in the pencil's slot, because there was no edit page. With M5 it should
get a pencil like every other kind. The "action affordances never move" rule decides where the bin
goes.

## Redaction

The new fields must never reach the anonymous calendar. A booking id is an internal handle, and
publishing it would tie a public transfer to a private hotel. `PublicCalendarProjector` does not
read them today, which is the allow-list doing its job. **Add the absence tests anyway**
(`PublicCalendarProjectorTest`, and a `CalendarRedactionSecurityTest` case driven by a real
transfer with a hotel source), because "it does not read the field" is exactly the claim a later
refactor breaks without noticing. `PublicCalendarProjector` gains a `GroundTransferChanged` branch,
which must read exactly what its `Planned` branch reads.

## Pre-Push-Tasks box (D3)

> **After the rollout:** open `/itinerary` and change (or cancel and re-plan) every ground transfer
> still to come, so each gets its endpoint references. As of the 2026-11-15 backup that is Nov 20
> (palace → Berlin, which also fixes the hotel) and Nov 25 (Darmstadt → Mannheim). If this is
> skipped, nothing breaks: those transfers simply never report as stale.

Note that a Change on an old transfer *does* add the references, since the form resolves both ends
again, so "change" is enough once M5 has shipped.

## Tests

- `EndpointSource` and both events: golden samples with and without sources (`null` → `NONE`).
- Resolver: each of the seven token kinds yields the right source. A bare `airport:DEN` yields
  `NONE`.
- Both commands: the source reaches the event; the D5 rule's boundaries (exactly at the leg's time
  is allowed, a minute the wrong side is refused), in each direction.
- Controllers, per the three-tier standard: each refusal lands on its input (parameterized unit
  test), and a `@WebMvcTest` per input shows the rendered `<span class="error">`.
- `ScheduleGapProjectorTest`: one case per cell of the staleness table, plus:
  - a reference-less transfer is never reported;
  - a canceled transfer is never reported;
  - a changed transfer whose new end is live clears the problem;
  - both ends stale gives two problems.
- The extracted date rule: its existing cases move with it, and the command keeps a case proving it
  calls it.
- `TransfersByEndpointProjector`: plan, change (the old source no longer lists it), cancel.
- Change propagation: every read model follows a `GroundTransferChanged`.
- Renderers: the M1 section, card and both fix links; the M3 strip (owner sees the link, family sees
  the sentence and **no** link). Whole-element assertions.
- Cancel pages: one `@WebMvcTest` per page showing the line, and its absence when nothing
  references the thing.
- Change form: `@WebMvcTest` for the filled-in render, the emptied gone end with its "Was …" line,
  and the banner's "Currently:" line.
- Redaction: the absence tests above.

## Losses, named

What this deliberately does **not** do:

- **Old transfers** are never reported (D3) until they are changed or re-planned.
- **A changed name or address** is not reported. The snapshot shows the old one until the transfer
  is changed.
- **Hotel and event times** are only checked by day. A check-in moved from 15:00 to 22:00 on the
  same day is not reported.
- **"Probably replaced by…"**, the guess from the talk doc, is not attempted. Ted picks the new end.
- **No automatic fix of any kind.** The app reports; Ted decides.

## Build order

1. `EndpointSource` + the event field + golden samples.
2. Resolver, command and handler carry the source. D5's rule on the plan form (M4).
3. Extract the date rule and the time rule; `StaleGroundTransfer` in `ScheduleGapProjector` and
   through every switch.
4. M1: the `/schedule-problems` section and the problem-calendar band, with **Cancel transfer**
   only.
5. `GroundTransferChanged` + the Change form (M5) + its route; add **Change transfer** to M1.
6. M3: the itinerary strip.
7. `TransfersByEndpointProjector` + the cancel-page line (M2).
8. Redaction tests, `Pre-Push-Tasks.md` box, `Backlog.md` status, and the `Cleanup_Tasks.md`
   "Change a ground transfer" item moved to Done.

Slices 1–4 are useful alone: from the first deploy, every newly planned transfer is watched and
reported.

## Open questions

None. Every surface is approved in the mockup (Ted, 2026-11-17). The pencil-and-bin placement on
the transfer card is settled when slice 5 is built (see M5).
