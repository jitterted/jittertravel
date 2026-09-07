# Cancel Train, and the overlapping-legs detector

Planned 2026-09-06 (Ted). **Slice 1 shipped the same day; slice 2 is next.** Two slices, in this
order, because **slice 2's fix link is slice 1**: the way to fix "two trains at once" is to cancel
one of them, and until slice 1 there was no way to cancel a train at all.

Prompted by Ted, 2026-09-06: *"schedule problems fails to identify problem when two trains with
similar journeys are entered on the same day (especially if overlapping times), also do we not have
'cancel train' yet?"*

---

## What is actually wrong, verified

Both findings below were confirmed by driving events through a real `ScheduleGapProjector` in a
throwaway probe (written, run, deleted — not committed).

### The overlap is invisible; the *non*-overlap is visible but says the wrong thing

`ScheduleTimeline.orderedPoints` sorts by local day, then by **role rank**, then by UTC. A leg
contributes a `REQUIRE` point at its departure ("he must already be here") and an `ARRIVE` point at
its arrival ("a booked leg put him here"). Both are rank 1, so two same-day legs interleave by UTC.

| Fixture | Points, in sorted order | Reported today |
|---|---|---|
| Hamburg→Berlin 09:00–11:00 **and** Hamburg→Berlin 10:00–12:00 | `REQ Ham`, `REQ Ham`, `ARR Ber`, `ARR Ber` | **nothing** |
| Hamburg→Berlin 09:00–11:00 **and** Hamburg→Munich 09:30–14:00 | `REQ Ham`, `REQ Ham`, `ARR Ber`, `ARR Mun` | **nothing** |
| HAM→BER flight 09:00–10:00 **and** HAM→BER train 09:30–12:00 | as above | **nothing** |
| Hamburg→Berlin 09:00–11:00 **and** Hamburg→Berlin **14:00**–16:00 | `REQ Ham`, `ARR Ber`, `REQ Ham`, `ARR Ber` | `MissingTravel[Berlin → Hamburg]` |
| Hamburg→Berlin 09:00–11:00 **and** Cologne→Munich 10:00–14:00 | `REQ Ham`, `REQ Col`, `ARR Ber`, `ARR Mun` | `MissingTravel[Hamburg → Cologne]` |

So Ted's parenthetical is exactly the mechanism. **When the times overlap, the two `REQUIRE`s land
before the two `ARRIVE`s, every `REQUIRE` agrees with the city the walk is currently holding, and
the contradiction disappears.** When they do not overlap, the first `ARRIVE` has already moved the
walk to Berlin, so the second `REQUIRE` in Hamburg raises a gap — a real signal wearing a sentence
that describes a journey Ted never intended to make.

Two consequences beyond the missing row:

- **The bogus leg silently relocates the walk.** In row 2 above the timeline ends believing Ted is
  in Munich, because the last `ARRIVE` by UTC wins. That city then feeds `locationByNight`, so the
  missing-hotel city, `awayDays()` and `atHomeOn()` are all computed off whichever duplicate
  happened to sort last.
- **There is no leg-vs-leg detector at all.** `computeProblems` runs five detectors
  (`ScheduleGapProjector.java:301`): `missingTravel`, `missingHotels`, `duplicateHotels`,
  `overlappingOccupancies` (gatherings + private events only) and `differentCityConflicts`
  (gathering vs conference). Nothing compares two `Movement`s. `duplicateHotels` exists on the
  reasoning *"Ted can only sleep in one of them"*; **"Ted can only be on one train"** is the same
  argument with no detector behind it.

### There is no Cancel Train

Cancel exists for exactly three kinds — hotel, ground transfer, private event. `/booked-trains` rows
offer **Edit only** (`BookedTrainsRenderer.java:139`). No `TrainCancelled` event, no command, no
route. Flights and gatherings are in the same position; a conference has *decline attendance* but no
organizer-cancel action.

`docs/Cleanup_Tasks.md` recorded the gap in passing ("flight, train, gathering, conference,
private event have **no cancel action at all**") and had gone **stale** — private event shipped
2026-08-24. Corrected with slice 1; flight, gathering and the organizer-facing `ConferenceCancelled`
remain open there.

---

# Slice 1 — Cancel Train — **SHIPPED 2026-09-06**

Built as planned, with three things worth recording that the plan did not predict; see
"How it shipped" at the end of this section. Suite green both tiers: **2275 unit + 78 js**.

`CancelPrivateEvent` (shipped 2026-08-24) with the types swapped. Read that slice before starting;
this section only records where trains differ.

## The event

```java
public record TrainCancelled(TrainTripId tripId, String reason) implements Event
```

`reason` optional free text, `null → ""` in the compact constructor, read only in `/admin/eventlog`.

**Additive and cheap by construction.** A new event type, not a change to `TrainBooked` /
`TrainChanged`: register it in `EventTypes` at `INITIAL_SCHEMA_VERSION`, **no schema bump, no
upcaster, no migration, backup stays v3**, every existing backup still restores. If an
`infrastructure` edit other than the one `register(...)` line appears, something stopped being
additive.

**No sequencing conflict with `ScheduledTransitTripPlan.md`** (open, nothing built), which adds a
`TransitMode` enum to `TrainBooked`/`TrainChanged`. Different events, different files. Nor with
`BookingProvenancePlan.md`, for the same reason.

## Domain

- `CancelTrainCommand(TrainTripId tripId, String reason)` — one refusal, `!context.trainExists()`,
  throwing the existing `TrainNotFound` (already a `RuntimeException`, already used by
  `ChangeTrainHandler`). **No time gate**, for the private event's reason exactly: a past bogus leg
  is the one most worth removing, because it is the one still asserting Ted was somewhere he was
  not. `reason` is carried onto the event and never inspected.
- `CancelTrainContext(boolean trainExists)` — one fact, no clock.

## Application

`CancelTrain`, folding `trainExists` from `commandExecutor.eventsForDecision()` (R1: never from a
projector — `TrainBooked`/`TrainChanged` → true, `TrainCancelled` → false), and appending through
`CommandExecutor`. `commandId` captured at the boundary. Copy `CancelPrivateEvent` line for line.

## Every read model that sees a train — and what it does

This is the part to get right; a projector that handles `TrainBooked` and forgets `TrainCancelled`
keeps showing a trip that is not happening.

| Consumer | On `TrainCancelled` | Why |
|---|---|---|
| `BookedTrainsProjector` | **remove** | D1 below |
| `TrainCalendarProjector` | remove | owner calendar |
| `PublicCalendarProjector` | remove | **redaction tier**: a leftover entry tells a stranger Ted travels on a day he does not |
| `ItineraryProjector` | remove | |
| `TrainDetailsViewProjector` | remove | also backs the cancel page itself and the `train:` transfer token |
| `ScheduleGapProjector` | `trainLegs.remove(tripId)` | the false `Movement` is the whole point |
| `TransferEndpointProjector` | remove **both** rows | it already drops a cancelled hotel — *"a cancelled booking is not a place Ted can be dropped off"* |
| `LocationAuditProjector` | **do nothing — deliberately** | its own javadoc settles this for `HotelBookingCancelled`: the `TrainBooked` row stays in the log forever and the read-time upcaster resolves its zone on **every replay**, so dropping it from the audit would hide exactly the unresolvable location that breaks startup |

That last row is the one someone will "fix" later. It gets a **positive** test asserting the
cancelled trip's stations are *still* audited.

**One knock-on, already handled in the code:** `GroundTransferEndpointResolver` reads
`TrainDetailsViewProjector.findById` to resolve a `train:<tripId>:arrival` token, and already throws
`UnknownTransferEndpoint("That train trip is no longer available")` when it misses. A transfer
form left open in another tab therefore fails cleanly at submit. Transfers **already recorded** are
untouched — the endpoint address is snapshotted into the command, never referenced live.

## Web

- `CancelTrainRequest(UUID tripId, String reason)`, `""`-normalizing.
- `CancelTrainController`: GET `/booked-trains/{tripId}/cancel` renders the confirmation, POST
  performs it. Malformed id, unknown id, and already-cancelled all redirect silently to
  `/booked-trains` — a j2html list cannot render a flash (`CancelPrivateEventController`'s comment
  and `Cleanup_Tasks.md`'s dead-flash entries).
- `cancel-train.html`, Thymeleaf (it POSTs — the j2html/Thymeleaf split). **Include
  `~{fragments/problem-context :: problemContext}` above the form now**, in slice 1: slice 2 makes
  this page a fix target, and the fragment renders nothing when no `?problem=` is present. One line
  here saves reopening the file.
- `SecurityConfig`: add **`/booked-trains/*/cancel`** to the OWNER list. The existing
  `/booked-trains/*` does **not** cover it — `*` is one path segment. Same shape as
  `/ground-transfers/*/cancel` and `/planned-private-events/*/cancel`.
- `AuthorizationMatrixTest`: `arguments("/booked-trains/abc/cancel", OK, DENIED_HOME, LOGIN)`.

## Where the action appears

Three surfaces, and the affordance rules decide each.

1. **`/booked-trains` row** — a `Cancel` link **after** the existing `Edit`, so nothing that is
   there today moves.
2. **Itinerary train card** — a `cancelBin` at the end of the service-ID line, beside the pencil
   that is already there, OWNER-only. Family can never cancel, so it renders **not at all** for
   them (the authorization half of the affordance rule, not a greyed control).
3. **`/calendar`** — `EntryDetails.Train` gains a `cancelPath`, and
   `CalendarViewBuilder.ownerActions` returns **pencil + bin** for a train. This makes trains the
   first kind carrying two icons in that slot; it is the end state
   `ChangePrivateEventPlan.md` already anticipated ("the card gains pencil and bin together rather
   than one action at a time"). **Redaction holds by construction**: `EntryDetails.PublicTrain` is a
   separate record with no slot for a path, so there is nothing to strip. Taken as the default
   unless Ted would rather cancel stayed on the list and the itinerary only.

## Decisions

**D1 — hard removal, not a tombstone. Settled 2026-09-06 (Ted).** A cancelled hotel
keeps a greyed row on `/booked-hotels` (`view.cancelledWith(reason)`); a ground transfer and a
private event vanish. Trains go with the second group: the dominant reason to cancel a train is that
the *entry* was wrong, a train carries no `cancelBy` deadline and no money story in this model, and
slice 2 will link here specifically to remove a duplicate — a tombstone would leave the duplicate
visible forever, which is the thing being complained about. Note the hotel tombstone exists because
Cancel Hotel has no undo and the greyed row is where "Undo" will live; that argument does not
transfer.

**D2 — it records a reason.** Reversing the ground transfer's "no booking, nothing to explain", for
the private event's reason: *"rebooked for the 17th"* is worth having, it costs one `String`, and
nothing keys off it.

**D3 — plain confirm, ~~amber~~ red, no typed word.** Per CLAUDE.md: appending a `*Cancelled` after a
`*Booked` destroys nothing — the booking stays in the log, on `/admin/eventlog`, and an undo is a
future event away. The typed word belongs to admin operations that actually destroy stored data.

**The colour half was wrong and was corrected in review the same day.** The claim was "amber because
booking it again puts it back". It does not: re-booking mints a new `TrainTripId`, and a past trip
cannot be re-booked at all, since `BookTrainCommand` requires a future departure. The colour asks
*"can Ted put this back from inside the app?"* and the answer is no, so it is **red**, like Cancel
Hotel. Note this doc contradicted itself — `CancelTrainCommand`'s own javadoc said, correctly, "that
one cancellation is not reversible from inside the app". Generalised in CLAUDE.md: **re-entering
something by hand is not an undo.**

**D4 — "Cancel", not "Remove".** Keeps one vocabulary with the three existing cancels, even though
the common case is a mistyped entry rather than a real cancellation. A second verb would need Ted to
classify the situation at the moment he least wants to. Taken as the default unless Ted says
otherwise.

## Tests

Beyond the ordinary controller/renderer tier:

- **`TrainCancellationPropagationTest`** — the forcing function, sibling of
  `GroundTransferCancellationPropagationTest` and `PrivateEventCancellationPropagationTest`. One
  case per consumer in the table above, driving `TrainBooked → TrainCancelled` through each real
  projector, **plus the negative case** for `LocationAuditProjector`. Note that
  `LocatedEventsReachScheduleProblemsTest` will **not** catch a missing branch here —
  `TrainCancelled` carries no `TrainStationAddress`, so it is invisible to that scan. This test is
  the only guard.
- `CalendarRemovalPropagationTest` — a new row (present in both calendar models, then gone from
  both). Known cost, already recorded there: a fifth removal event needs a new row and nothing
  forces it. This is the fifth.
- `CalendarRedactionSecurityTest` — the anonymous body `doesNotContain` the cancel path, through
  the real security chain. Paired with a `PublicCalendarProjectorTest` assertion, per the both-tiers
  rule.
- `GoldenEventDeserializationTest` — a golden sample for `TrainCancelled`, same change.
- `EventTypesTest`, `AuthorizationMatrixTest`, `BookedTrainsRendererTest` (whole `href` attribute),
  `ItineraryRendererTest` (owner sees the bin, family does not).

`ApplicationServicesUseCommandExecutorTest` and `DomainIsPureTest` pass by construction.

## Also in slice 1

Correct `docs/Cleanup_Tasks.md:790` (private event now has a cancel), and add the Backlog row.

## How it shipped

**Three things the plan did not predict.**

1. **`EntryDetails.Train` is the first kind to carry two owner actions**, so
   `CalendarViewBuilder.ownerActions` grew a `pencilAndBin` arm beside `pencil`. Each path is
   independently optional, so a missing one drops its icon without shifting the other — the pencil
   sits exactly where it does on every other kind whether or not a bin follows it. Pinned by an
   ordering assertion, not just a presence one.
2. **A too-loose test assertion was caught by its own failure**, exactly as CLAUDE.md's precise-HTML
   rule predicts. `doesNotContain("cancel-bin")` for a non-owner itinerary card failed because
   `.cancel-bin` lives in the always-rendered CSS block; the real claim is
   `doesNotContain("class=\"cancel-bin\" href=\"/booked-trains/")`, the whole attribute.
3. **The `/booked-trains` row's action cell became a `div`**, since it now holds two links, and that
   cell is a direct child of a `subgrid` row. Verified headlessly at 1024px and 560px: the columns
   still line up across rows and the stacked layout still stacks. No horizontal scroll at either.

**Mutation-verified in two rounds.** Neutralising all seven removal branches at once failed exactly
seven `TrainCancellationPropagationTest` cases and left the eighth — the `LocationAuditProjector`
case, which asserts the *opposite* — green, so every branch is pinned by its own test and the one
deliberate non-removal is pinned too. Removing the `SecurityConfig` matcher failed the new
`AuthorizationMatrixTest` row alone.

**The one deviation from the plan, and it is a nothing:** the POST redirects to `/booked-trains`
rather than to a dated view. A private event redirects to `/itinerary?date=…` because landing on the
day it left is what shows the evening is gone; a train is cancelled *from* the list far more often
than from a calendar day, and the list is where the duplicate was visible.

---

# Slice 2 — the overlapping-legs detector — **SHIPPED 2026-09-06**

Built as planned. Suite green both tiers: **2305 unit + 78 js**. Three things worth recording that
the plan did not predict — including one where **the plan's own stated reason was wrong**; see
"How it shipped" at the end of this section.


## The rule

Two legs whose intervals overlap as **instants**, the exact predicate
`ScheduleTimeline.Occupancy.overlapsWith` already uses:

```
a.departure.utc() < b.arrival.utc()  &&  b.departure.utc() < a.arrival.utc()
```

Half-open, so **a connection is not a conflict**: arrive 11:00, depart 11:00 does not fire. That is
load-bearing — back-to-back legs are the normal case.

Kind-agnostic: flight × flight, train × train, flight × train, transfer × anything. "Ted can only
be on one of them" does not care what it is.

**What this rule deliberately does not catch**, and the reason it is still the right rule: the
*non*-overlapping duplicate (row 4 of the table above) already produces a visible `MissingTravel`,
just under a confusing name. Widening the detector to cover it means re-deriving what the walk
already computes, and the honest fix for that case is a **separate** defect — "a leg departing a
city the walk did not put him in should say so, rather than asking for a journey". Named as a
residual below, not folded in here.

## Step 0 — rename `ProblemRef` to `ProblemKey`

Settled 2026-09-06 (Ted), and its own small commit at the head of the slice, before the new variant
lands.

`Ref` was vague because it was **stretched over two different things**: a content-derived *string*
that survives in a URL, and a typed *in-memory identity plus display name*. Naming both `…Ref`
invites a reader to expect one to behave like the other. So they get different names, chosen for
what each actually is.

`ProblemRef(String key)` → **`ProblemKey(String value)`**. It is a key: derived from the problem's
own content, compared, parsed back out of a query string. The type name also stops stuttering —
`ProblemRef.key()` becomes `ProblemKey.value()`.

**Cheap and contained, measured:** 7 Java files, 22 occurrences, 2 file renames
(`ProblemRef.java`, `ProblemRefTest.java`). The `?problem=` **query parameter name is unchanged**,
so no URL changes, no bookmark breakage, and `ProblemContextAdvice` and
`ProblemContextFragmentConventionTest` are untouched in substance. Update the four docs that
mention it by name (`Cleanup_Tasks.md`, `archived/ProblemContextOnFixPagesPlan.md`,
`archived/GroundTransferEndpointReadModelPlan.md`, `Backlog.md`).

## `Movement` needs an identity — `TravelLeg`

`ScheduleTimeline.Movement` is `(fromCity, departure, toCity, arrival)` — no id and no kind, so
today a detected pair could not be named on a card or linked to a fix. It gains **one component**:

```java
sealed interface TravelLeg permits TravelLeg.Flight, TravelLeg.Train, TravelLeg.Transfer {
    String label();                       // "LH 402", "ICE 597", "BER → Hotel Adlon"
    record Flight(FlightId id, String label) implements TravelLeg {}
    record Train(TrainTripId id, String label) implements TravelLeg {}
    record Transfer(GroundTransferId id, String label) implements TravelLeg {}
}

record Movement(TravelLeg leg,
                String fromCity, ZonedTimestamp departure,
                String toCity,   ZonedTimestamp arrival)
```

**Sealed, with typed ids, rather than `(enum kind, String id)`.** The point is `ProblemFix`: a
`switch` over `TravelLeg` is exhaustive, so the day Cancel Flight ships the compiler asks what a
flight's fix link is, instead of a default arm quietly continuing to offer nothing. `ScheduleProblem`
already imports `HotelBookingId`, `GatheringId` and `ConferenceId`, so three more id types are in
keeping.

`label` is built in `ScheduleGapProjector`, where the event is in hand — a flight's
`airline + " " + flightNumber`, a train's `serviceId` (falling back to `from → to` when blank, which
is common), a transfer's `origin → destination`.

**Rejected: promoting `Movement` itself to a public sealed type carried straight on the problem.**
It is less code — one type instead of two, no duplicated components — but it puts the timeline's own
working type inside a read model, which `DuplicateHotel` deliberately refuses to do (it carries
`DuplicateStay`, never `ScheduleTimeline.Stay`). It would also drag `inTransitOvernight`,
`allLegs`, `computeContext` and every `put` branch in the projector into what should be an additive
slice. Settled 2026-09-06 (Ted).

## The problem record

```java
record OverlappingTravel(OverlappingLeg first, OverlappingLeg second) implements ScheduleProblem
record OverlappingLeg(TravelLeg leg, String fromCity, String toCity,
                      ZonedTimestamp departure, ZonedTimestamp arrival)
```

The problem carries its **own** record, built for the card — the `DuplicateHotel` / `DuplicateStay`
shape exactly. Its five components duplicate `Movement`'s five; that duplication is the price of the
boundary, and it is the price `DuplicateStay` already pays.

Named to pair with `MissingTravel` — the card reads *"Overlapping travel — ICE 597 · ICE 1043"*.
Not `DuplicateTravel`: the two legs need not be duplicates (Hamburg→Berlin overlapping
Hamburg→Munich is the same impossibility). `relevantUntil()` is the later of the two arrivals,
mirroring `SchedulingConflict`.

Each side keeps its **own** `ZonedTimestamp`s and cities, for `SchedulingConflict`'s stated reason:
two overlapping legs in different zones fall on different local dates, and showing one side's date
beside the other's times reads as wrong exactly when the instant-based detection has done its job.

## Where detection lives

`ScheduleTimeline.overlappingTravel()`, beside `duplicateHotels()` — it already holds `movements`,
and `ScheduleGapProjector.computeProblems` gains one line. A pairwise double loop, as
`overlappingOccupancies` does; the leg count is tens, not thousands.

**`allLegs()` must become a total order.** It sorts by `departure().utc()` alone today, so two legs
departing at the same instant have unspecified relative order. Add the leg's own path as a
tiebreaker.

**But state the reason accurately — the obvious one is wrong, and this plan first got it wrong.**
The draft said an unstable order would make `ProblemKey` flip between recomputations and stale every
open fix link. Measured 2026-09-06: a `ConcurrentHashMap` over a **fixed key set iterates
deterministically**, so the same events already produce the same order with or without a tiebreaker,
and a "stable across recomputes" test can never fail — it would pin nothing. What the tiebreaker
actually buys is that the order is **ours** rather than an artefact of how UUIDs happen to hash, so
changing the map type, the fold, or how legs are collected cannot silently renumber every open fix
link. The test therefore asserts the **ordering rule** (same-instant legs order on their own path,
over many id pairs) rather than stability.

## The five sites a new variant touches — four are compiler-forced, one is not

1. `ProblemKey.of` — exhaustive switch. Key: `join("legs", firstKindAndId, secondKindAndId)` in the
   detector's order, which the tiebreaker above makes stable.

   **Shipped as `detailsPath()` instead, and corrected in review the same day.** A path is not an
   id: every ground transfer's page is `/itinerary`, so *every* transfer-vs-train overlap keyed
   `legs|/itinerary|/booked-trains/X` and two of them collided — a fix link on the second resolved
   to the first one's banner and fixes. Two transfers overlapping each other collided with every
   other such pair. The same substitution made the leg sort not a total order, which the tiebreaker
   exists to be. `TravelLeg.identity()` is the plan's `kindAndId`, and is what both now use.
2. `ProblemFix.fixesFor` — exhaustive switch. **One "Cancel …" link per side**, pointing at the
   page slice 1 built. See "The fix link is the payoff" below — this is the whole reason the slices
   are in this order, and it needs its own decision about wording.
3. `ProblemBand.from` — exhaustive switch. A new `Marker.TRAVEL_OVERLAP` in the **existing**
   `Lane.TRAVEL`; `Lane.CLASH` already carries two markers for precisely this "one concern, two
   kinds" reason, and bands are all amber regardless.
4. `ScheduleProblemsRenderer.render` — **not a switch.** It partitions with five `instanceof`
   filters into five explicit sections, so a sixth variant renders **nowhere** and **nothing fails**.
   Fix this as part of the slice: replace the five filters with one exhaustive `switch` that appends
   into per-kind lists, so the compiler catches the next one too. Then add the section — its own
   card colour is fine here (the list view keeps per-kind colours; it is the *calendar* that was
   flattened to amber, because there a problem sits among non-problems).
5. `ProblemContextFragmentConventionTest.TEMPLATE_FOR_PATH` — the fix links now land on
   `/booked-trains/{id}/cancel` and `/ground-transfers/{id}/cancel`, so both must be listed **and**
   both templates must carry the banner fragment. `cancel-hotel.html` is already a fix target and
   already carries it, so the precedent is settled: a cancel page reached from the report *does* get
   the banner, and that is not in tension with "a recording surface needs no decision-support
   information" — the banner says why you are here, which is the standing condition on every fix
   target. Both routes are already OWNER-only, which is the third standing condition.

`ScheduleContext` needs nothing: `ScheduleContext.Travel` already covers legs, so the banner's
context works out of the box.

## The fix link is the payoff — and its wording is an open decision

**Yes: a reported pair offers one "Cancel …" link per side**, each pointing straight at
`/booked-trains/{id}/cancel` — the confirmation page slice 1 shipped. This is why the slices are in
this order, and it is `cancelEachStay`'s shape exactly:

> *"One link per stay, never a single 'cancel the redundant one': which room to keep is Ted's call,
> and the booking intent shown beside them is what informs it."*

Same here. The report never decides which of the two legs is the real one, and it never POSTs — the
link navigates to the gated confirmation, which is where the cancellation actually happens.

**But the label needs deciding, because `cancelEachStay`'s pattern does not survive the move.** A
hotel's link reads `Cancel "Grand Hotel"`. The train analogue is the service id, and the production
log says that fails in both directions:

- **Never blank there, but blank is reachable** — all 12 stored trips carry one, yet the form
  accepts an empty service id and `BookedTrainsRenderer` already renders that case. Two *duplicate*
  legs with no service id fall back to the route, which is **identical on both sides**: two links
  reading `Cancel Hamburg → Berlin`, pointing at different ids. That is the exact situation this
  detector exists for, so it is not a corner.
- **And they are long.** The longest in production is **51 characters** —
  `S9 (35945 to Hanau Hbf) & ICE 1653 (to Dresden Hbf)` — with four more over 30. A chip cannot
  carry that, and `ProblemFix` labels sit in a row of up to three.

**Recommendation: the label carries the departure time first, then as much name as fits** —
`Cancel 9:00 AM · ICE 597`. The time is always present, always short, and is what differs in the
reported case ("especially if overlapping times"). The detail belongs on the **card**, which shows
both legs in full the way the duplicate-hotel card shows both stays; the link only has to point at
one of them.

**The one case with no discriminator at all** is a true exact duplicate — same route, same times,
same service id — where both links read identically. That is honest rather than broken: the two
entries are genuinely interchangeable and it does not matter which one goes. Worth *not* inventing
"Cancel the first"/"Cancel the second" for it, which would imply an order the report does not have.

## The awkward part: a duplicate **flight** has no fix

After slice 1, a train and a ground transfer can be cancelled; a flight cannot. So an overlapping
flight × flight pair renders with **no fix links** — the greyed "no fix yet" chip that
`SchedulingConflict` already shows, and no band anchor.

That is honest but unsatisfying, and it was the one place this plan might have been wrong-sized.
**Settled 2026-09-06 by measurement (see below): Cancel Flight does not ride along.** Production
holds no overlapping flight pair, so building it now would be building for a case that does not
exist. Ship the detector fix-less for flights — the greyed "no fix yet" chip is an existing,
understood vocabulary — and let the first real overlapping flight pair pull Cancel Flight in.

A third option was rejected in advance: **detect flights, offer "Edit this flight"**. Editing does
not remove a duplicate, and a link that cannot fix the problem it is attached to is worse than a
greyed one.

## The rule, validated against production

CLAUDE.md's standing practice, from the `EnteredLocation` word list: *"Validate a proposed rule
against the production backup before shipping it"*, and *"when a rule misfires, shorten the list —
do not weaken the rule."*

Run against `jittertravel-backup-production-2026-09-06T140455Z.json` (113 events → **32 live legs**:
16 flights, 12 trains, 4 ground transfers, folding `*Changed` over `*Booked`):

| | |
|---|---|
| **Overlapping pairs found** | **0** |
| Zero-length legs (`dep == arr`) | 0 |
| Exactly-touching pairs (`arrive == depart`, must **not** fire) | 0 |
| Tightest real connection | **33 min** — Eurostar 9123 into London 10:57Z, GWR out of London 11:30Z |
| Same route twice on one day | none |

**No false positives, and 33 minutes of headroom.** The specific risk this check existed to measure
was **ground transfers, whose times are approximate by design** (`ItineraryRenderer`: *"both ends
and the (approximate) times"*) — a taxi entered 09:45–10:30 against a flight landing at 10:00 would
fire, and a report that cries wolf is a report Ted stops reading. Of the four transfers, exactly
one sits next to another leg: the **Aschaffenberg taxi, 36 minutes** after the ICE from Aachen. So
the risk is real but not currently firing, and it is the pair to watch — round that train's arrival
up by 40 minutes, or enter the taxi as leaving at 10:00, and it fires.

**If it ever does misfire, exclude a pair kind** (transfer × train, say) rather than adding a fudge
tolerance to the overlap test — shorten the list, do not weaken the rule.

**One thing this measurement does *not* establish**, and it should be said plainly: zero hits means
the rule is clean, not that it catches anything. The two-trains-on-one-day situation is **not in the
production log**, and both `TrainChanged` histories there are ordinary corrections (a service-ID
refinement; a date moved from the 11th to the 9th), not a duplicate being edited away.

**Where it came from, confirmed by Ted 2026-09-06: a local instance, entered while testing.** That
is the honest provenance, and **it strengthens both slices** — including the one it first appeared
to weaken.

- **It strengthens slice 1.** Ted entered data he did not want and had **no way to remove it** —
  which is the "a wrong entry with no way out" argument that put Cancel before Edit for private
  events, met first-hand. Cancel Train is also the tool that makes local testing cleanable, which
  is where junk data actually accumulates.
- **It strengthens slice 2, and this plan's first reading of it was wrong.** The draft argued that
  "never occurred in real data" lowered the priority. That measured the wrong thing: **zero
  production incidence was the outcome of Ted's own process, not evidence of low risk.** Ted,
  2026-09-06: *"i almost entered the wrong data in production and would have if i didn't test
  locally first, so the missing problem detection is a real concern."* The local instance was the
  **control that caught it**, and the whole point of `/schedule-problems` is to be the control that
  works when there is no other one. A safety net is not judged by how often it has been needed, and
  a clean production log is also exactly what it looks like when the net is absent and nothing has
  fallen through yet. **The near miss is the evidence** — and it is precisely the evidence a report
  of already-happened problems can never produce about itself.

**So: build slice 2 after slice 1**, as originally sequenced. The 33-minute headroom measured above
is not reassurance either; it is how close *carefully entered* data already sits to a rule that
today fires on nothing at all.

**One finding does not wait for slice 2, though**, because it is a hole *today*:
`ScheduleProblemsRenderer` partitions problems with five `instanceof` filters rather than a switch,
so **any** sixth variant renders nowhere and nothing fails. That is independent of this feature and
is logged in `Cleanup_Tasks.md`.

## Tests

`ScheduleTimelineTest` (the predicate, including the back-to-back non-case and the different-zone
case), `ScheduleGapProjectorTest` (all four fixtures from the table at the top, plus the
mixed-kind one), `ScheduleProblemTest` (`relevantUntil`), `ProblemKeyTest` (stability across two
recomputations, and that the tiebreaker makes same-instant legs deterministic), `ProblemFixTest`
(two links for train × train, one for train × flight, none for flight × flight),
`ProblemBandTest`, `ProblemCalendarRendererTest`, `ScheduleProblemsRendererTest`,
`ProblemContextFragmentConventionTest`, `ScheduleProblemsAcceptanceTest`.

Mutation-verify all of it, and in particular mutate the overlap predicate's `<` to `<=` — that is
the mutation that proves the back-to-back connection case is really pinned.

## How it shipped

1. **The `allLegs()` tiebreaker's stated reason was wrong, and the first test for it pinned
   nothing.** The plan said an unstable order would flip `ProblemKey` between recomputes and stale
   every fix link, and the test asserted "same events, two projectors, same pair order". Mutating
   the tiebreaker away failed it **zero times in five runs** — because a `ConcurrentHashMap` over a
   fixed key set iterates deterministically, so that assertion can never fail. The test now asserts
   the **ordering rule** (same-instant legs order on their own path, over 25 id pairs), which the
   same mutation fails every time. The tiebreaker stays, on the honest reason: the order is *ours*
   rather than an artefact of UUID hashing, so changing the map type or the fold cannot silently
   renumber every open fix link. Javadoc and the section above both corrected.
2. **A too-loose assertion again, in the same shape as slice 1's.**
   `doesNotContain("column-heading--overlap")` failed because the class name is in the
   always-rendered CSS block; the real claim is `doesNotContain(">Overlapping Travel</p>")`. That
   is twice in two slices — the CSS-in-the-same-string trap is the standing hazard of these
   renderer tests, exactly as CLAUDE.md's precise-HTML rule says.
3. **j2html emits `href` before `class`.** `<a href="…" class="overlap-leg-link">`, not the other
   way round. Assertions on whole anchors have to match its order.

**Mutation-verified in three rounds.** Widening the half-open overlap predicate to `<=` failed
exactly `aConnectionIsNotAConflict` — the case that keeps an ordinary changeover from being reported
as a clash. Dropping the tiebreaker failed the ordering test in three of three runs. Removing the
renderer's section call failed three renderer cases.

**Verified visually** at 1024px and at 820px (the iPad's portrait width): the amber card, the
time-first fix chips, and leg links underlined **at rest** rather than on hover. Production's
longest service id — `S9 (35945 to Hanau Hbf) & ICE 1653 (to Dresden Hbf)` — still fits its chip at
820 with no horizontal scroll, which was the open worry about leading labels with the name.

**One thing deliberately left as it is:** three mutually overlapping legs produce **three** rows,
one per pair, not one row naming all three. Each pair is its own decision about which to keep — the
same reasoning that gives `DuplicateHotel` a row per run rather than per booking.

---

# Slice 3 — enforcement at data entry — **SHIPPED 2026-09-06**

Built as designed. Suite green both tiers: **2340 unit + 78 js**. See "How it shipped" at the end.


Asked for by Ted 2026-09-06, immediately after slice 1 shipped: *"when can we add enforcement of
overlap during data entry?"* **Designed to here; the mechanism is the one open decision.**

**Why it is worth doing, in his own case:** slice 2's report catches an overlap *if Ted visits the
page*. Entry-time catches it *when it happens* — which is what the near miss that prompted all of
this actually needed. The two are not alternatives; the report is the net for everything already in
the log, and this is the net at the moment of the mistake.

**Why it comes after slice 2, not before.** The overlap predicate must exist once. Slice 2 puts it
in `ScheduleTimeline`, where the legs already live; a write-path check needs *the same rule* against
a different source of legs. Building refusal first means writing the predicate twice, and two copies
of a rule are two chances to disagree about what an overlap is.

## Scope — flights and trains (settled 2026-09-06, Ted)

Four commands: `BookTrainCommand`, `ChangeTrainCommand`, and the two flight equivalents. The check
is **across kinds**, not within one — a flight overlapping a train is the same impossibility — so
both contexts fold the same set of scheduled legs.

**Ground transfers are excluded, on the measurement rather than on taste.** Their times are
approximate by design (`ItineraryRenderer`: *"both ends and the (approximate) times"*), and the
production sweep found the Aschaffenberg taxi sitting **36 minutes** from its ICE. That is where a
false block would land, and a rule that blocks a legitimate entry is worse here than one that misses
one — the report still catches what this does not.

## The overlapping entry is a link (settled 2026-09-06, Ted)

Wherever the existing leg is named — in a form message, in a warning, on the problem card — **it
links to that entry's details page, or to its edit page when no details page exists.**

Today that means the **edit page** for both kinds: `/booked-flights/{id}` and `/booked-trains/{id}`
are `ChangeFlightController` / `ChangeTrainController` GETs, and neither kind has a read-only detail
page. If one ever ships — the conference precedent, where Ted was *"never happy about reusing edit
pages as a substitute for a real details page"* — these links move there and nothing else changes.

Two notes on it. It is **not** the pencil, so the "an icon means one thing" rule is untouched: this
is a text link on a message, and it may point at an edit page without teaching that pencils are
unreliable. And every surface it appears on is **OWNER-only** already, so it discloses nothing.

**Apply the same rule to slice 2's card** for consistency: the two legs named there should link the
same way, beside their `Cancel …` fixes.

## Two traps to write down now

1. **`ChangeTrainCommand` must exclude the trip being changed**, or every edit overlaps itself and
   no booked trip can ever be corrected. The fold is "every live leg other than this one".
2. **The flight forms do not yet have field-level errors.** CLAUDE.md's four "a rejected form
   reports everything it can see" rules (2026-09-06) are implemented for the **two train forms
   only**; flights still print one undifferentiated banner sentence, and are named there as the
   obvious next target. So covering flights means either extending that work first or shipping two
   different error experiences for the same rule. Decide which before starting.

## The decision context carries a collection — and that is the point

`BookTrainContext` is one `Instant` today. Judging an overlap needs the existing legs, folded from
the **event stream** (R1 — never from `ScheduleGapProjector`, which is a projection). Nine
application services already fold that way, so the mechanism is established; this is the first
*context* carrying a collection rather than a scalar.

**That is a good thing, not a cost** (Ted, 2026-09-06) — *"it's a pattern that will become more
common as UI tasks become more complex"*. A decision that depends on what else is on the schedule
can only be made honestly by a context that carries what else is on the schedule; the alternative
is a command reaching for a read model, which R1 forbids for exactly this reason. So build the fold
as something worth copying rather than as a special case:

- **Fold once into a shared value**, not twice into near-identical flight and train folds. Both
  contexts want the same thing — every live scheduled leg — and the check is across kinds anyway.
- **Fold live legs**, applying `TrainCancelled` and the flight equivalent, so a cancelled trip
  cannot block a booking. Slice 1's fold in `CancelTrain` is the shape to copy.
- **Keep the shared value in the domain**, since it states a fact about travel and depends on
  nothing outside `java.*` — `DomainIsPureTest` will say so either way.

## Settled: refuse outright (Ted, 2026-09-06)

An overlapping entry is **rejected at the command**, the way `EnteredLocation` rejects a station
pasted into a city field: a field-level message on the form, counted in the banner, nothing written.
No new form pattern — it reuses the error vocabulary CLAUDE.md settled on 2026-09-06.

**The objection raised against it, and why it does not survive Ted's other answer.** Refusal was
argued to break the natural correction workflow — *enter the new trip, then cancel the old* — by
forcing cancel-first. Two things dissolve that, and they are both already in hand:

- **Cancelling is now cheap.** Slice 1 shipped Cancel Train; it is one link from `/booked-trains`,
  from the itinerary card and from the calendar entry. Cancel-first was expensive when the only way
  to unmake a wrong trip was to edit it into something else.
- **The refusal names the thing in the way, as a link** (Ted's rule above). So the error is not a
  dead end: it says which leg blocks this one and puts Ted one click from the page where he deals
  with it. "You cannot book this, and here is what is in the way" is a complete instruction.

**Warn-and-proceed and save-then-report are both rejected**, and for the same reason: this is
Ted's own near miss, where the wrong data very nearly reached production. A warning that can be
clicked through, and a report that only fires if he lands on the right page, both leave the bad
entry writable. Refusal is the only one of the three that makes it impossible.


## How it shipped

**Four new domain types, and the fold is the interesting one.** `ScheduledLegId` (sealed over
flight and train, carrying `path()`), `ScheduledLeg` (id + window, with the half-open `overlaps`),
`ScheduledLegs` (the context value, with `overlapping(self, …)`), and `OverlappingLegRefused`.
`LiveScheduledLegs` folds the stream once for all four write paths, keyed by leg so a `*Changed`
replaces rather than doubles, with `TrainCancelled` applied.

**The link is not in the error message, and could not be.** Thymeleaf escapes `th:errors`, so an
anchor written into a rejection renders as literal markup. The message is plain text under the
departure-time input — the value this form can change — and `OverlappingLegNotice` rides beside it
as a model attribute the template renders as its own `<a>`. One `.overlap-link` rule in `site.css`
rather than four inline copies: the two flight templates have no `<style>` block at all, which is
what surfaced that.

**Three findings.**

1. **A real test-isolation gap, exposed rather than caused.** `BackupRestoreRoundTripTest` began
   failing because `persister.truncateAllTables()` clears the *database* but not the in-memory
   event list that `eventsForDecision()` folds from — so one case's flight was still visible to the
   next, and a shared window made the second booking a collision. **Every earlier fold asked about a
   specific id** ("does *this* trip exist?"), so stale events from another case were harmless; this
   is the first *cross-aggregate* question in the codebase and therefore the first to notice. Fixed
   by staggering the fixtures, with the reason written into the helper. Not a production bug: the
   same divergence after a truncate is why a restore requires a restart.
2. **`NoFullyQualifiedClassReferencesTest` earned its keep**, catching an FQCN left in a test
   helper's return type.
3. **The dependency this plan warned about was overstated.** It said the flight forms "print one
   undifferentiated banner sentence"; in fact `book-flight.html` and `change-flight.html` already
   render `th:errors` per field and the controllers already call `rejectValue`. What they lack is
   the four *reporting* rules (every problem in one response, the count banner), not the channel —
   so the refusal lands correctly on all four forms today.

**Mutation-verified in three rounds**, one per load-bearing rule: removing the self-exclusion failed
4 cases (it is what makes any booked leg correctable at all), closing the half-open comparison
failed 2 (a connection is not a conflict), and dropping the `TrainCancelled` arm from the fold
failed 1 (a cancelled trip must stop blocking).

**One process note, because it happened twice.** Restoring a mutation with `git checkout` reverted
a file to a *stale index* the first time and silently deleted a line from an *untracked* file the
second. While work is staged, restore mutations from a copy — never from git.

---

## Known residual, deliberately out of scope

**A leg that departs a city the walk did not put him in reports as a missing journey.** Row 4 and
row 5 of the opening table: Ted sees *"No travel — Berlin → Hamburg"* and is invited to book a
flight for a trip he never meant to take. The signal is right, the sentence is wrong, and the
overlap rule does not reach it because the times do not overlap. It is the same underlying condition
seen from the other side, and it wants its own think — probably a distinction in the walk between
"a gap you should book" and "a leg that contradicts the one before it". Logged here rather than
folded in.

---

## Questions, all settled 2026-09-06

- **Q1 — tombstone or hard removal on `/booked-trains`?** **Hard removal** (D1). The hotel's greyed
  row exists because Cancel Hotel has no undo; a train has no deadline and no money story, and slice
  2 links here to *remove a duplicate*, which a tombstone would preserve.
- **Q2 — "Cancel Train", or a second verb for "this entry was a mistake"?** **One verb** (D4). Two
  would need Ted to classify at the moment he least wants to. Taken as the default.
- **Q3 — what replaces the `Ref` suffix, and what shape is the leg identity?** **`ProblemKey` and
  `TravelLeg`, different names because they are different things** — one is a content-derived string
  that survives in a URL, the other a typed in-memory identity; `Ref` was vague precisely because it
  was stretched over both. The leg identity is a **component on `Movement`**, with the problem
  carrying its own `OverlappingLeg` record, following `DuplicateHotel`/`DuplicateStay`. Promoting
  `Movement` to a public sealed type was rejected: it puts the timeline's working type inside a read
  model.
- **Q4 — does Cancel Flight ride along as slice 1b?** **No** — settled by measurement. Production
  holds zero overlapping pairs of any kind, flights included, so ship the detector fix-less for
  flights and let the first real pair pull Cancel Flight in.
- **Q5 — should the calendar's train entry carry both pencil and bin?** **Yes**, the end state
  `ChangePrivateEventPlan.md` already named. Taken as the default.

**Provenance, answered by Ted 2026-09-06:** the two-trains case was **local, entered while testing**
— not production data. See "The rule, validated against production" for what that changes:
slice 1 gets stronger (Ted met "a wrong entry with no way out" himself), slice 2 loses its urgency,
and the recommendation is to ship slice 1 and leave slice 2 planned but not next.
