# Ground Transfer — Plan

**Status:** `done` — designed **and built** 2026-08-20; **D13 (flight-leg prefill), D14 (today-or-later
scoping) and D15 (state the journey once) added and built the same day**, after Ted used the form. A gap-review pass settled four more
decisions (D9–D12) before coding; **all of D1–D12 were settled** (Ted, 2026-08-20) and all were
built as written. Suite green at 1183 (default tier) + 43 (js tier). The one deliberate omission was
cancel/change, per D11; **cancel shipped 2026-08-20** as its named fast-follow (see "Cancel, as
built" below, suite green at 1246 + 48), and change stays deferred.

## As built — where it differs from the plan above

Four shapes were decided during implementation, plus three new decisions (D13, D14, D15) that Ted
asked for once he had used the form and read the result. Everything else is the plan's intent made concrete.

- **Endpoint resolution is two collaborators, not three separate ones on the handler.** D8 cleared
  a budget of "two on the handler, one on the controller". Resolving a token actually needs four
  lookups (hotel address, airport city, airport zone, location zone), so they are held by one
  `GroundTransferEndpointResolver` — the handler's *single* new collaborator — and the option lists
  by `GroundTransferEndpointOptions`, the controller's single one. That is inside the approved
  budget, not beyond it.
- **Each end resolves its own zone**, and the transfer is then timestamped in the *origin's*
  (unchanged from "Zone" above). Resolving both is what catches an unresolvable **destination**;
  resolving only the origin would have let a stale destination through.
- **D15 (Ted, 2026-08-20): the owner view states the journey once.** The first cut put the
  publishable route in the owner's *subtitle* (so the redactor had something to keep), which meant
  every entry said the same hop twice — `DEN → Denver Marriott South at Park Meadows` above
  `DEN → Lone Tree, Colorado, United States`. Ted: *"I only need the first line ... don't need the
  second line, keep the time."* So the owner's calendar entry is now **title + times**, and the
  itinerary card is **both ends + times** with its cities line gone.
  - The publishable route still has to exist — the redactor cannot derive a city from a hotel name
    — so it moved to `CalendarEntry.publicRoute`, a field **no renderer reads**. It is the same
    shape of kind-specific field as `commitment` (CONFERENCE-only) and another passenger for the
    `EntryDetails` refactor. Adding it made the redactor's other five branches declare it
    explicitly, which is deny-by-default working as intended.
  - The redactor now *builds* the anonymous subtitle from that field rather than filtering the
    owner's, so a line added to the owner's subtitle later cannot ride along at all.
- **D13 (Ted, 2026-08-20): a flight endpoint prefills its own date and time**, and the two ends
  offer **different** legs. "From" lists only flight **arrivals**, "To" only flight **departures** —
  you never travel away from an airport you are departing, nor toward one you have just landed at.
  Each option carries the leg's moment (`DEN — Denver · arrive Mon Sep 14, 11:30 AM (UA 59)`), and
  choosing it fills in the date plus the time for *that* end; the far end is nudged by 45 minutes
  only when the pair would otherwise invert. Raised by Ted: *"otherwise I have to remember that
  information or open up another browser tab."*
  - **This reverses the "no JavaScript" note** under "Endpoints" below. Prefill on `change` has no
    server-side equivalent that isn't a round-trip, and the codebase already has a first-class `js`
    tier for exactly this (`docs/JS-Behavior-Tests.md`), which the calendar day menu already uses.
    `GroundTransferPrefillJsTest` lifts the script **verbatim out of the shipped template** so there
    is no second copy to drift.
  - **The token is unchanged** — still `airport:<CODE>`. A transfer is between *places*, not between
    flights, so the command, the event, the resolver and D3/D12 are all untouched; the times ride on
    the `<option>` as `data-date`/`data-time`, which is presentation only. Two arrivals into the same
    airport are therefore two options with one shared value, which is correct: they resolve
    identically and differ only in what they prefill.
  - **Leg scoping sharpens D10** rather than replacing it. Each leg is FUTURE-scoped by *its own*
    moment instead of by the flight's departure, so a flight already in the air still offers the
    airport it is about to land at — the trip-already-under-way case D6 exists for. Hotels keep the
    shared FUTURE filter, and still prefill nothing: a check-in time is not when a taxi runs.
- **D14 (Ted, 2026-08-20): the endpoint lists run from *today* onward, not from *now*.** D10's
  "`relevantUntil` not yet past" was right about having no date window but too tight to the minute
  for how a transfer is actually entered: you land at 11:30 and write the taxi up that evening, by
  which time the arrival was "past" and the airport had vanished from the form. So an endpoint is
  offered while its own local day is **today or later**, judged in *that endpoint's* zone — the zone
  of the airport you just landed at is the zone you are standing in. Yesterday still drops off.
  - **Applied to hotels as well as flight legs**, which Ted did not ask for but which is the same
    hole in the most common transfer there is: check out at 11:00, ride to the airport, and the
    hotel had already gone by the time you recorded it. Legs alone would have fixed one end of that
    trip and not the other.
  - Day granularity is what the rest of the app already reasons in, and it needs no viewer-zone
    plumbing — the endpoint's own zone answers the question.
- **Two boundary rejections got named types**: `UnknownTransferEndpoint` (malformed token, or a
  hotel booking cancelled between GET and POST) and `SameTransferEndpoints`. An airport code the
  table does not know surfaces as the existing `ZoneResolutionException` — it *is* one. All four
  error paths re-render the form, as specified.

## Why

A trip contains short hops that no entry kind can record: the taxi from the airport to the hotel,
the subway to the venue, the shuttle back. They have no service id, no seat, and no booking. You
catch the next one that comes.

The gap became visible on 2026-08-20. A conference sits in **Lone Tree, CO**; the flight lands at
**DEN**. `StaticAirportCityResolver` maps `DEN` to `Denver`, the conference matches on its city
`Lone Tree`, and the two do not match, so `/schedule-problems` reports missing travel. Two separate
things are wrong there:

1. A conference has no `locationForMatching` (`PlanConferenceRequest:130` always passes `null`), so
   the two names cannot be reconciled. That is a **separate** fix, tracked in `../Cleanup_Tasks.md`.
2. Even with the names reconciled, **there really is a journey** between the airport and the venue,
   and the app cannot record it. This plan is that record.

## Decisions (Ted, 2026-08-20)

- **D1 — A ground transfer carries a date and a start/end time.** The time is imprecise, and that
  is accepted: it is normally "just before" or "just after" a flight. This removes the whole
  ordering problem — a real `ZonedTimestamp` at each end drops straight into
  `ScheduleTimeline.Movement`, and `orderedPoints` needs no new role or rank.
- **D2 — It fills a missing-travel gap.** That is the entire purpose. It enters the timeline as a
  `Movement`, exactly as a flight or a train leg does.
- **D3 — Ted never types an address.** Each end is picked from something the app already knows: an
  airport, or a booked hotel whose address is copied. See "Endpoints" below. Sharpened by D12:
  there is **no free-text fallback at all** — a `custom` city/region/country option was considered
  in the first draft and dropped.
- **D4 — The anonymous view shows `Ground transfer` and each end as an airport code, or as
  city / state / country.** No street, no hotel name, no times.
- **D5 — Its own `EntryKind.GROUND_TRANSFER`.** Reusing `TRAIN` was considered and rejected. Four
  reasons, the first decisive:
  1. **Redaction.** The `TRAIN` branch of `CalendarEntryRedactor` (`:33`) keeps `mainTitle` and
     drops only the subtitles, because a train title is a public route. A transfer title is
     `DEN → Marriott Lone Tree`. The shared branch would publish the hotel name. CLAUDE.md already
     forbids reusing a branch for a kind with different privacy; `PRIVATE_EVENT` is the pattern.
  2. **Styling.** `CalendarViewBuilder:272` derives the CSS class from the kind name, so a taxi
     would render in the train colour with the train icon.
  3. **Lane order.** `EntryKind` declaration order sets the lane rows. A shared kind puts taxis and
     trains in one lane, where they compete for sub-rows on a busy day.
  4. **Itinerary and future filters.** `ItineraryEntry.kind()` picks the label and icon, and there
     would be no way to say "Ground transfer", nor to filter or count transfers later.

## Model

### The event

```java
public record GroundTransferPlanned(
        GroundTransferId groundTransferId,
        String originAirportCode,        // "DEN", or "" — public
        String originName,               // "Marriott Lone Tree", or "" — PRIVATE
        Address origin,                  // snapshot, carries locationForMatching
        String destinationAirportCode,
        String destinationName,
        Address destination,
        ZonedTimestamp departsAt,
        ZonedTimestamp arrivesAt
) implements Event
```

**Flat, not a sealed `TransferPoint` hierarchy.** A sealed `AirportPoint | PlacePoint` reads better,
but a polymorphic record inside an event payload needs Jackson type information in the stored JSON,
and every stored payload is a compatibility commitment (see `../Event_Serialization_Contract_Tests.md`).
Two `String`s and an `Address` per end cost nothing at rest. The redactor's rule is simple: **if the
airport code is non-blank, publish the code; otherwise publish city / region / country.** The name is
never published.

`Address` is normalized by its compact constructor: a blank `locationForMatching` falls back to
`city`. Empty `String`s, never `null` (standing rule).

### Endpoints — how they are filled (D3, D10, D12)

Each end is one `<select>`, whose value is a token:

| Token | Source | Resolves to |
|---|---|---|
| `airport:DEN` | the airports on FUTURE-scoped booked flights (`BookedFlightsProjector`), deduplicated | code `DEN`, city from `StaticAirportCityResolver` (`Denver`), `locationForMatching` = that city |
| `hotel:<bookingId>` | FUTURE-scoped, non-cancelled booked hotels (`BookedHotelsProjector` for the list, `HotelDetailsViewProjector` for the address) | name + the hotel's `Address`, copied verbatim, `locationForMatching` included |

**D10 (Ted, 2026-08-20): the option lists are FUTURE-scoped, with no date window.** "Near that
date" from the first draft is gone — it was undefined, and on a plain GET the server has no date to
be near. An ongoing trip is included (D6 exists precisely so Ted can add today's taxi mid-trip),
past trips drop off, and nothing depends on the form's date field. `?date=` **prefills the date
field only**; it never filters the options.

**Widened by D14** — see "As built" at the top. The original rule here, `relevantUntil` not yet
past, cut to the minute and so lost today's own endpoints part-way through today; the shipped rule
is "the endpoint's local day is today or later, in the endpoint's own zone".

**D12 (Ted, 2026-08-20): no `custom` free-text token.** Endpoints are strictly an airport or a
booked hotel. A transfer whose end is a bare venue (subway to the conference venue, no hotel or
airport at that end) **cannot be recorded in this slice** — deferred, see below.

**Resolution happens server-side, in `PlanGroundTransferHandler`, at submit time.** No prefill
round-trip. (The "no JavaScript at all" this originally said was **reversed by D13** — see "As
built" at the top: choosing a flight leg fills in its date and time client-side, covered in the
`js` tier. Resolution itself is still entirely server-side.) The address is **snapshotted into the
event**, never referenced live: changing the hotel later must not silently rewrite a past transfer.
`GroundTransferId` is created in the controller — external inputs (ids, `now`) come from the
boundary, per the standing rule.

> **D8 — Dependency gate — cleared (Ted, 2026-08-20).** The handler gains **two** new collaborators (a
> hotel-address lookup and the airport city/zone resolver), and the controller gains one (the
> option lists). The standing rule is that two or more new dependencies on one class needs Ted's
> agreement first; he gave it. No further approval is needed for these three.

### Zone

One zone for both ends, resolved from the origin: `AirportZoneResolver` when the origin is an
airport, otherwise (a hotel origin) `LocationZoneResolver` on the hotel's address through the
shared `VenueZone`, as `PlanPrivateEventHandler` does. A transfer that crosses a zone boundary is
out of scope; the timeline compares **cities**, not zones, so nothing downstream is harmed.

### Command rules

- `arrivesAt` must be after `departsAt` → `InvalidGroundTransferTimeRange`.
- **D6 (Ted, 2026-08-20): no future-date rule.** `PlanPrivateEventCommand` and
  `PlanGatheringCommand` both demand a future *date*. A transfer is different: Ted will often add
  today's airport taxi to a trip already under way, exactly to clear a problem the trip already
  raised. **Any date is accepted, past or same-day. The range rule is the only rule.** So the
  command needs no `now` in its decision context at all, so `PlanGroundTransferContext` is an
  **empty record** implementing `DecisionContext`. It exists only because
  `CommandExecutor.execute` takes one. Do **not** carry an unused `now` "for later" — that is a
  speculative field, and adding it when a rule needs it is a one-line change.

### Form validation and the error path

Every failure **re-renders `plan-ground-transfer.html` with the error and writes nothing** —
errors render on the page hosting the form, never a redirect to a page that cannot show them
(standing rule). The cases:

- **Unknown or stale token** — a token that is not `airport:` or `hotel:`, an airport code the
  resolver does not know, or a `bookingId` that no longer resolves (the hotel was cancelled
  between GET and POST). Rejected in the handler.
- **Origin token equals destination token** — a transfer from a place to itself records nothing.
- **Zone resolution failure** — `ZoneResolutionException` from either resolver surfaces as a form
  error, not a 500.
- **`arrivesAt` not after `departsAt`** — the domain rule above; `InvalidGroundTransferTimeRange`
  is caught at the controller and shown on the form like the rest.

### Presence fact

One `case GroundTransferPlanned` in `ScheduleGapProjector`, building
`Movement(origin.locationForMatching(), departsAt, destination.locationForMatching(), arrivesAt)`.
This is the same shape as the `TrainBooked` case (`:68`). It is also **mandatory**:
`LocatedEventsReachScheduleProblemsTest` fails the build for any event carrying an `Address` that
the projector does not handle.

### Calendar

One entry, `EntryKind.GROUND_TRANSFER`, spanning `departsAt`→`arrivesAt` (normally one day column).
Lane position: **after `TRAIN`, before `LODGING`**, since declaration order is the lane order.

Owner title: `🚕 DEN → Marriott Lone Tree` — the taxi leads it as ✈️ leads a flight's and 🚄 a
train's (Ted, 2026-08-20), and the redacted title is `🚕 Ground transfer`. The icon leads the
*title* only; a subtitle route carries none, as on a flight. Owner title otherwise, with a `SubtitleLine.Range(departsAt, arrivesAt)` so the
owner sees the times, as on a train — **and nothing else** (D15). Anonymous title: `Ground
transfer`, with a `SubtitleLine.Text` reading `DEN → Lone Tree, CO, US`, built by the redactor from
`CalendarEntry.publicRoute`. **No `ZonedTimestamp` survives redaction** —
redaction rule 2, the `datetime` attribute leaks a clock time even when the visible text does not.

**Styling gets real CSS, not the default.** `CalendarViewBuilder:272` lowercases the kind name, so
the class is `entry--ground_transfer` (underscore and all). The lane colours live in
`CalendarRenderer`: two vars (`--entry-ground_transfer-bg: #fef9c3; --entry-ground_transfer-fg:
#854d0e` — taxi yellow, distinct from the train's orange) beside the others at `:35`, and a
`.entry--ground_transfer` rule beside `.entry--train` at `:180`. Note that `PRIVATE_EVENT` never
got a rule and renders default-styled; do not copy that omission — a distinct colour was reason 2
for D5.

**D9 (Ted, 2026-08-20): the calendar day menu gets the link.** One
`dayMenuItem("Add ground transfer", "/plan-ground-transfer?date=" + iso)` beside the five existing
"Add …" items at `CalendarViewBuilder:252-256`. (Private events chose nav-card-only; a transfer is
always tied to a travel day, so day-menu entry fits, and it supplies the `?date=` prefill.)

### D7 (Ted, 2026-08-20): no `mode`

The feature was raised as "Taxi, Mass Transit", and a `GroundTransferMode { TAXI, TRANSIT,
RIDESHARE, SHUTTLE, CAR }` would let the owner view say *Taxi* instead of *Ground transfer*.
**Rejected for now** ("at least not yet"). Every view says *Ground transfer*, owner and anonymous
alike.

Adding it later is additive and cheap: one enum, one form field, one label, and an upcaster default
for the events already stored. Nothing in this slice should be shaped around the possibility.

## Files

### Create — domain (5)

- `GroundTransferId`
- `PlanGroundTransferCommand implements DomainCommand<PlanGroundTransferContext>`
- `PlanGroundTransferContext` — empty record (D6)
- `GroundTransferPlanned implements Event`
- `InvalidGroundTransferTimeRange`

That is 5 files, not 6: D6 removes the `…DateNotInFuture` rejection entirely.

### Create — application (4)

- `GroundTransferPlanning` — application service; `CommandExecutor`, never `EventStore`
- `PlanGroundTransferHandler` — form → command, endpoint-token resolution, zone resolution
- `GroundTransferCalendarProjector`
- `GroundTransferItineraryEntry`

### Create — web (3)

- `PlanGroundTransferRequest`
- `PlanGroundTransferController`
- `templates/plan-ground-transfer.html`

### Edit — application (6)

- `EntryKind` — add `GROUND_TRANSFER` after `TRAIN`
- `ItineraryEntry` — add to `permits`
- `ItineraryProjector` — build the entry
- `CalendarAggregator` — take the new projector
- `CalendarEntryRedactor` — **its own branch**, every field named explicitly
- `ScheduleGapProjector` — the `Movement` case

### Edit — web / infrastructure (7)

- `ItineraryRenderer` — card, kind label, two CSS rules
- `CalendarRenderer` — the two `--entry-ground_transfer-*` vars and the `.entry--ground_transfer`
  rule (see "Calendar" above)
- `CalendarViewBuilder` — the day-menu "Add ground transfer" item (D9); the anonymous subtitle
  needs no new `SubtitleLine` shape
- `index.html` — nav card, Font Awesome Pro fill SVG from the travel-icons row
- `SecurityConfig` — `/plan-ground-transfer`, `/plan-ground-transfer/**`
- `EventTypes` — `register("GroundTransferPlanned", …)`
- `EventSourcingConfig` — service bean + projector bean + aggregator wiring

Backup and restore need **no** work: the format is event-verbatim.

## Tests

New (7):

- `PlanGroundTransferCommandTest` — the range rule; **and a yesterday-dated transfer is accepted**
  (D6), which is the case that would regress if someone copies the private-event command wholesale
- `PlanGroundTransferHandlerTest` — each endpoint token resolves to the right `Address`, hotel
  address is copied including `locationForMatching`; an unknown token and a stale `bookingId` are
  rejected (D12 — there is no `custom` branch to fall back to)
- `GroundTransferCalendarProjectorTest`
- `GroundTransferEndpointOptionsTest` — the direction split (arrivals as origins, departures as
  destinations), each option's prefill date/time, two trips through one airport staying two
  choices, and the D14 day rule from both sides: a flight and a checkout **earlier today** are
  still offered, yesterday's are gone
- `GroundTransferPrefillJsTest` (`js` tier, D13) — an arrival fills the date + departure time, a
  departure fills the date + arrival time, the far end moves only when the range would invert, a
  time already valid is left alone, and a hotel changes nothing
- `PlanGroundTransferControllerTest` (`@WebMvcTest`, `@WithMockUser`, `.with(csrf())`) — happy
  path, **and each error-path case re-renders the form with the error in the body** (bad token,
  identical endpoints, zone failure, inverted range), asserted on body text
- `PlanGroundTransferWebIntegrationTest` — the Thymeleaf template renders
- redaction unit case in `CalendarEntryRedactorTest` — hotel name and street are **gone**
- redaction case in `CalendarRedactionSecurityTest` — the rendered anonymous body
  `doesNotContain` the hotel name, through the real security chain

Edited (7): `CalendarAggregatorTest`, `ItineraryProjectorTest`, `ItineraryRendererTest`,
`CalendarRendererTest` (the whole `.entry--ground_transfer { … }` rule and the whole
`<a href="/plan-ground-transfer?date=…">` day-menu element), `AuthorizationMatrixTest`
(`/plan-ground-transfer`, `OK / DENIED_HOME / LOGIN`), `GoldenEventDeserializationTest` (a golden
sample is required for every new event), `ScheduleProblemsAcceptanceTest` (an itinerary where a
transfer closes the gap).

Assertions name whole elements and whole attributes, never bare words. Every new or changed test is
mutation-verified: break the production string, watch it go red, revert.

## Build order

1. Domain: id, command, context, event, rejection. Command test first.
2. `ScheduleGapProjector` case + acceptance itinerary. **This is the point of the feature**, and
   the arch test enforces it — do it before any rendering.
3. Handler + application service + endpoint resolution (**after** the dependency gate above).
4. Form, controller (id creation, error path), security matcher, authorization matrix row.
5. Calendar projector, aggregator, `EntryKind`, redactor branch + **both** redaction test tiers.
6. Calendar rendering: CSS vars + rule in `CalendarRenderer`, day-menu item in
   `CalendarViewBuilder` (D9).
7. Itinerary entry + renderer card.
8. Nav card in `index.html`.
9. Flight-leg options + prefill script (D13), with its `js`-tier test.
10. `./mvnw test` **and** `./mvnw test -Pjs-tests` before any push.

## Size

About **17 new files and 20 edited files**, tests included — the `PrivateEvent` slice plus the
endpoint resolution, minus the future-date rejection D6 removes. One focused session.

## Deferred (not in this slice unless Ted says so)

- ~~**Cancel and change flows — D11 (Ted, 2026-08-20): accepted, with the cost named.**~~ **Cancel
  shipped 2026-08-20**, the day after, as the named fast-follow — see "Cancel, as built" below.
  **Change is still deferred**, and now costs little: correcting a transfer is cancel-then-enter,
  which is two forms rather than one and loses nothing, since both ends are snapshots anyway. It is
  tracked in `../Cleanup_Tasks.md` under **Deferred (until needed)** — not queued, with the trigger
  named: re-entering transfers often enough to notice, most likely once a `mode` field lands (D7).
- **Time ordering on the calendar.** Ted saw a 3:55 PM flight render above the 1:00 PM transfer
  that fed it (2026-08-20) and chose to **leave the lanes as they are**. This is not a ground-
  transfer bug: the calendar lays every kind out in a fixed lane band, so no entry is time-ordered
  against another kind, and reordering `EntryKind` cannot fix it because a transfer runs both ways.
  The itinerary already sorts across kinds by time. Fixing the calendar means abandoning lanes —
  captured, with the multi-day problem that makes it hard, in `../Future_Feature_Slices.md`
  ("Time-ordered calendar days").
- A transfer with a **venue endpoint** — no hotel or airport at one end (D12 dropped the free-text
  token). Until then the venue-side hop is recorded hotel-to-hotel or not at all.
- A `/ground-transfers` list page with the FUTURE/ALL toggle (`TemporalView` + `TimeFilterToggle`).
- A transfer that **crosses a time zone**, and one that **crosses midnight** — the form takes one
  date and two times, so an arrival past 00:00 cannot be entered; same bucket, same rarity.
- Any link between a transfer and the flight or hotel it serves. The endpoints are snapshots by
  design (see "Endpoints"), and a live reference is a different feature.
- Fix-link prefill beyond `?date=` (the `ProblemCalendarPlan` slice-5 `Fix ▾` menu wants to
  preselect an endpoint when exactly one option resolves to the gap's city) — that plan's slice,
  compatible with D10 since options are not date-filtered.

## Cancel, as built (2026-08-20)

The D11 fast-follow, built the day after the slice. A wrong transfer was permanent, and a permanent
wrong transfer is worse than a wrong row: it keeps asserting a movement, so it **masks** the
missing-travel gap it was entered to close. That is the failure the flow exists to undo, and it is
the case the propagation test pins.

- **`GroundTransferCancelled(groundTransferId)` — the id alone, no reason.** A hotel's cancellation
  reason records something that happened in the real world with a booking; a transfer has no
  booking to explain away, and "the entry was wrong" is the usual reason (Ted, 2026-08-20).
- **`CancelGroundTransferCommand` refuses only an unknown or already-cancelled transfer**
  (`GroundTransferNotFound`), folded from the event stream by `CancelGroundTransfer` exactly as
  `CancelHotel` does (R1 — never from a read model). **No time gate**, for the same reason D6 gave
  planning none: a past hop that never happened is precisely the entry most worth removing.
- **Hard removal in every read model**, guarded by `GroundTransferCancellationPropagationTest` —
  the calendar, the itinerary, the new details view, and `ScheduleGapProjector`, whose case asserts
  the Denver→Lone Tree gap *returns* once the transfer is gone.
- **`/ground-transfers/{id}/cancel`**, GET confirmation + POST, its own `SecurityConfig` matcher
  (a single `*` matches one segment, so a per-item action needs its own entry) and its own
  `AuthorizationMatrixTest` row. POST lands back on `/itinerary?date=` the transfer's own day, which
  is where the hop's absence is visible. A `GroundTransferDetailsViewProjector` serves the page,
  mirroring `HotelDetailsViewProjector`.
- **A plain confirm, no typed word** — removing a transfer is recoverable by entering it again, so
  the amber half of the destructive-action rule applies, and the button is amber rather than red.
- **Reachable from both surfaces** (Ted, 2026-08-20, asked): the itinerary card and the calendar
  entry. A transfer has nothing to edit, so the owner action in the pencil's slot is a **bin**
  (`.cancel-bin`, same size and weight as `.edit-pencil`, no red). On the calendar that needed a
  new `CalendarEntry.cancelPath` — a sibling of `editPath`, not a replacement, since an entry that
  can be edited is not thereby one that can be cancelled from the calendar. It is the third
  kind-specific passenger for the `EntryDetails` refactor, after `commitment` and `publicRoute`,
  and adding it made all seven redactor branches declare it: deny-by-default working again.
- **Anonymous and family viewers get nothing at all**, not a greyed control: the href would tell a
  stranger both that the surface exists and the transfer's internal id. Two independent barriers —
  the redactor nulls the field, the renderer gates on `isOwner` — each pinned by its own test, and
  the security-chain case goes red only when both are broken (verified by mutation).

## Open questions

None. D1–D5 (the model), D6 (any date), D7 (no `mode`), D8 (the two collaborators), the
review-pass decisions D9 (day-menu link), D10 (FUTURE-scoped option lists, no date window), D11
(no cancel yet, fast-follow tracked), D12 (no free-text endpoint), and D13 (direction-split flight
legs that prefill their own date and time), D14 (today-or-later scoping), and D15 (state the
journey once) were all settled by Ted on 2026-08-20, and the slice was
built in the order above the same day.

Still deferred, unchanged from the list above (cancel is no longer among them — it shipped
2026-08-20): change, a venue endpoint, a `/ground-transfers` list page, zone- and midnight-crossing transfers, a live link
between a transfer and the flight or hotel it serves, and fix-link prefill beyond `?date=` (which
belongs to `ProblemCalendarPlan.md` slice 5 — now unblocked, since ground transfer is the third
answer its `Fix ▾` menu was waiting on).
