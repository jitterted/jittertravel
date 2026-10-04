# Flight itineraries: Cancel Flight, then paste, cancel and schedule-change a whole trip

Planned 2026-09-29 (Ted). **Part 0 (Cancel Flight) shipped 2026-09-29. Part 1 (YOW) and Part 2
slices (a), (b) and (e) — paste, preview, book, unknown-airport zone picker — shipped 2026-09-30.
Slice (c), cancel itinerary, shipped 2026-10-02 (see "Slice (c) as built" under Part 2).**
**Slice (d), schedule change by paste, shipped 2026-10-04 (see "Slice (d) as built"); Ted checked the
diff colours on the iPad the same day. Every slice of this plan is now done.** Still open, in the order
worth taking them: (1) reinstating a leg the airline dropped and later restored, which slice (d) refuses
(see "Still open after slice (d)"); (2) from Part 0 the `ProblemFix` link for a flight in an overlap
(deferred on purpose, below).

Prompted by Ted, 2026-09-29: *"how hard would it be to retrieve flight bookings from United
airlines using my booking confirmation code?"* The answer was that no API exists for that, so the
realistic path is pasting the confirmation email. That led to this plan. The sample itinerary had four
legs (SFO→ORD→YOW and back). Ted then asked for **schedule changes** too, and for **an event that
groups the legs into an itinerary**, because *"changing/canceling an entire itinerary is painful
now, has to be done for each leg"*. Designing that showed there is no Cancel Flight at all, and Ted
asked for it now: *"i need the cancel flight feature now, can you do that as the first independent
slice"*, kept *"to the minimum required for flight cancellation"*.

**Naming, settled 2026-09-30 (Ted): `FlightItineraryBooked`.** He first said
`FlightItineraryCreated`; his own rule is "past-tense facts, never CRUD", and what happened is that
the trip was booked.

---

## Part 0: Cancel Flight (first, independent, ships on its own)

There is no way to cancel a flight today. `CancelTrainAndOverlappingLegsPlan.md` deferred it until
the first real overlapping flight pair. This part copies Cancel Train's slice 1 with the types
swapped. Paths are under `src/main/java/dev/ted/jittertravel/`.

**Scope (Ted, 2026-09-29): the minimum for cancellation.** Cancel is reachable from three
surfaces: `/booked-flights`, the calendar's owner bin and the itinerary's owner bin. **Deferred:**
the `ProblemFix` "Cancel …" link for a flight in an overlapping-travel problem. The
`TravelLeg.Flight` arm keeps returning nothing, and its comment is updated to say the cancel page
now exists and the link is simply not wired yet.

**Domain / application**
- `domain/FlightCancelled.java`: `record FlightCancelled(FlightId flightId, String reason) implements
  Event`, whose compact constructor turns a null reason into `""`. It's a hard removal, for the
  reasons given in `TrainCancelled`'s javadoc. Registered in `infrastructure/EventTypes.java` at
  schema_version 1: additive, no upcaster, no migration, backup stays v3.
  `EventTypesTest.everyDomainEventIsRegistered` forces the registration.
- `domain/CancelFlightCommand.java` and `domain/CancelFlightContext.java(boolean flightExists)`,
  reusing `FlightNotFound`. No time gate: a past flight can be cancelled, as with trains.
- `application/CancelFlight.java`: copies `CancelTrain`. Existence is folded from
  `eventsForDecision()` (a `FlightBooked` arm sets true, `FlightCancelled` sets false,
  `FlightChanged` is ignored), with the explicit loop.
- `web/CancelFlightRequest.java`: `record(UUID flightId, String reason)`.
- Bean in `infrastructure/EventSourcingConfig.java` next to `cancelTrainApplicationService`.

**Removal branches: 8 consumers.** `LocationAuditProjector` is deliberately left out, because
`FlightTimeZoneUpcaster` still resolves the airports on every replay.
- `LiveScheduledLegs`
- `PublicCalendarProjector`, `ItineraryProjector`, `TransferEndpointProjector` (both flight
  endpoint rows), `ScheduleGapProjector`
- `BookedFlightsProjector`, `FlightCalendarProjector`, `FlightDetailsViewProjector`

**Web**
- `web/CancelFlightController.java`: a copy of `CancelTrainController`. GET/POST
  `/booked-flights/{flightId}/cancel`, looking the flight up via `FlightDetailsViewProjector`.
- `templates/cancel-flight.html`: a copy of `cancel-train.html`. It includes the problem-context
  fragment, identifies the flight, has an optional reason, and a **red** button with **no typed
  word**. Cancelling destroys nothing, but there is no undo: re-booking mints a new id.
- `SecurityConfig`: add `"/booked-flights/*/cancel"` to the OWNER per-item list, plus an
  `AuthorizationMatrixTest` row.

**Viewing cancelled flights (added 2026-09-29, Ted).** `/booked-flights?cancelled=show` lists
them. `BookedFlightsProjector` is the one flight read model that keeps a cancelled flight, marking
the row instead of removing it; every other read model still drops it. The switch copies the
conferences' "Show dropped", including the count while hidden. A cancelled row is muted and shows
one grey box, "Cancelled <date>" with the reason written out inside it (when and why together; not
a tooltip, because the iPad has no hover, and Ted is fine with the extra height on a list he opted
into). Column widths use the grid's own track-sizing order: every column is `auto` except Route
(`1fr`), so all other columns reach one line before Route gets extra width, and nothing wraps while
another column has room. The box claims Route's width by content, not by a fixed width: its
"Cancelled <date>" line stays unbroken only when a container query (on the table's own width,
≥ 41rem) says it fits. Measured in headless Chrome from 500 to 1280px: no overflow at any width, no
wrapping from 1024px up. That measuring also found an overflow from 641 to ~715px, clipped by
`overflow: hidden`; it was partly there before, and made worse by the Cancel link. The fix was
removing the list's 48px side insets (Ted: wasted space), which gave the table back the width. The
stacking breakpoint stays at 640px, with the grid now fitting from 618px. The header reads
"Flight #" (Ted, 2026-09-30), and its Edit/Cancel are greyed spans in the same
slots (state, not permission). `FlightCancelled` gained `cancelledOn` (an `Instant` captured at the
boundary) before it ever shipped, because a displayed time is a payload field (R11). The date is
shown in the departure airport's zone. Why: a cancelled flight often leaves a travel credit or
refund to look up.

**Links** (Cancel after Edit, never moving Edit):
- `/booked-flights`: Edit and Cancel go in a `div.flight-actions`.
- `EntryDetails.Flight(editPath, cancelPath)`, drawn with `pencilAndBin`, whose `"Cancel train"`
  label becomes a parameter.
- The itinerary's flight gets a bin after its pencil, owner only.

**Tests:**
- Command, service and controller tests.
- `FlightCancellationPropagationTest`.
- A `CalendarRemovalPropagationTest` row.
- A `LiveScheduledLegsTest` case.
- Golden samples.
- `ProblemContextFragmentConventionTest`.
- Renderer tests for the three surfaces.

---

## Part 1: add YOW to the curated airport tables (shipped 2026-09-30)

The sample email's Ottawa legs cannot resolve a zone. Ted: *"mostly a one-off, add YOW"*. The 09-18
production backup has no `YOW`, so no stored flight's replay can change.
- Add `"YOW"` to the `America/Toronto` line of `AirportZoneResolver`.
- Add `Map.entry("YOW", "Ottawa")` to `StaticAirportCityResolver`.
- One test case each, mutation-verified.

---

## Part 2: itineraries (slices a, b, e shipped 2026-09-30; c shipped 2026-10-02; d shipped 2026-10-04)

**Slice (d) as built (2026-10-03, pushed 2026-10-04), and where it differs from section 7.**
- **Same page, no new route.** `/book-flight/itinerary` decides after parsing: the paste's code names a
  live itinerary (folded from the stream, `TripOnTheBooks`) ⇒ the preview is a diff and the button is
  "Apply schedule change". The form's minted id is the command id, as for a booking.
- **Decisions (Ted, 2026-10-03), each asked with an example:**
  1. A pasted leg that matches a leg **cancelled on its own earlier** is **refused** (`LegCancelledEarlier`),
     not re-added: cancelling cannot be undone, so Ted sorts it out by hand.
  2. A **flown** leg must be in the paste with the same times, else the whole change is refused
     (`FlownLegContradicted`: "times differ" or "left out"). History is not revised.
  3. A paste identical to what is booked says **"Nothing to change"** and shows no button; the command
     throws `FlightItineraryUnchanged` rather than record an empty change.
- **Domain.** `ChangeFlightItineraryCommand(itineraryId, pastedLegs)` computes an `ItineraryChangePlan`
  (per leg: UNCHANGED / CHANGED / ADDED / REMOVED, each with its own refusal) that the preview shows and
  `execute` writes, so the page cannot promise what the command refuses. Matching: flight number + local
  departure day, then route, earliest booked leg wins a tie. Rules for a changed or added leg are the
  booking rules, except the itinerary's own old legs are not collisions, and the pasted legs must not
  overlap each other. All refusals are reported together.
- **Event.** `FlightItineraryChanged(itineraryId, flightIds, reason, changedOn)` (golden sample). The
  snapshot lists **every** flight in the trip in departure order, **including legs the change cancelled**,
  so `/booked-flights` keeps showing a cancelled leg as part of its trip ("leg 2 of 3") exactly as a leg
  cancelled on its own already is. `changedOn` was added to the plan's shape because a displayed time is a
  payload field (R11). No new private value, so no redaction case.
- **Consumers.** `BookedItinerariesProjector` (membership), `CancelFlightItinerary` (reads the latest
  membership). `FlightTrips` needed nothing.
- **Named limits.** (a) A leg the airline dropped in one change and **reinstates** in a later email is
  refused by decision 1, because the fold cannot tell "cancelled by a change" from "cancelled by hand". (b)
  A paste for a **cancelled** itinerary's code is now refused ("cancelled; it cannot be changed") where it
  used to book a new trip, per section 7. (c) The diff's look is Ted's pick of mockup B (2026-10-04), checked on the
  iPad: a tinted row with a 4px left edge, light yellow Moved, green Added, peach struck-through Removed
  (not pink, which the red refusal wash already is), muted Unchanged, and a moved leg's old value struck
  through above the new one in its own cell. A refused row keeps the red wash and takes a red edge.
- **Where it lives, for whoever picks this up.** Domain: `ChangeFlightItineraryCommand`,
  `ItineraryChangePlan`, `FlightItineraryChanged`. Application: `FlightItineraryBooking` (decides
  booking vs change), `TripOnTheBooks` (the fold). Web: `ItineraryPreview` (rows, `Before` for the struck
  old values), `book-flight-itinerary.html`. Tests: `ChangeFlightItineraryCommandTest`,
  `FlightItineraryChangeTest`, `ItineraryPreviewTest`, `CancelFlightItineraryTest`, plus the controller
  test. Mutation-checked 2026-10-04: the plain tier kills every mutant in the slice's classes, and
  `-Ppit-spring` (new, see CLAUDE.md) leaves only older survivors (`EventTypes.isRegistered`, the
  `newFlightId` lambda in `BookFlightItineraryController.submit`).

**Still open after slice (d).**
- **Reinstating a dropped leg (named limit (a)).** United drops UA512 in one email and puts it back in a
  later one: the later paste is refused as `LegCancelledEarlier`. To allow it the fold must tell a leg
  cancelled **by a schedule change** from one cancelled **by hand**. The likely shape is a marker on the
  cancellation (the change already writes `FlightCancelled` with reason "Airline schedule change", so
  matching on that reason is the cheap route but couples behaviour to a display string; a typed field on
  `FlightCancelled` is the honest one and needs a golden sample and an upcaster decision). Then a pasted
  leg matching a change-cancelled leg is re-booked (a new `FlightBooked`, since cancelling cannot be
  undone) and the diff shows it as Added. Decide the shape with Ted, with an example, before building.
- **The overlap fix link (Part 0).** Unchanged: deferred on purpose.
- **Not covered by any automated check:** the template's colours and layout (PIT mutates bytecode only);
  they were verified by eye on the iPad.

**Slice (c) as built (2026-10-02), and where it differs from the design below.**
- **Domain.** `FlightItineraryCancelled(itineraryId, reason, cancelledOn)`, registered at schema_version
  1 with golden samples. It carries no new private value, so it needed no redaction case:
  `PublicCalendarProjector` still never reads an itinerary event. `CancelFlightItineraryCommand` emits
  one `FlightCancelled` per live leg and then the itinerary event, in one append. Refusals are
  `FlightItineraryNotFound` (unknown or already cancelled) and `FlightItineraryHasDeparted`.
  "Departed" is `ZonedTimestamp.hasPassed(now)`: a leg departing at this very instant has left. The
  list's greyed link uses the same method, so link and command cannot disagree.
- **Decision facts** come from the stream (R1): liveness from the itinerary events, the live legs from
  `LiveScheduledLegs.fold()`, the one fold of "live" the write paths share. A leg cancelled on its own
  is not cancelled twice, and a departed leg that was cancelled on its own no longer blocks the rest.
- **Read models.** `BookedItinerariesProjector` (events alone, R12) and `FlightTrips`, a composer a
  layer above both it and `BookedFlightsProjector`. Membership is by flight id, never by dates, so an
  itinerary booked into the gap of another is two itineraries whose legs interleave and neither's
  cancel can touch the other's flights.
- **Cancel page** `/booked-itineraries/{id}/cancel`, OWNER-only (matcher plus matrix row), red button,
  no typed word. A departed leg is answered on the page, with a cancel link per remaining flight, not
  by navigating away. **"Stays booked"** (Ted, 2026-10-02): the page lists live flights falling between
  the itinerary's first and last live leg that it will *not* cancel, each with its own itinerary's code
  when it has one, so "will this take Toronto with it?" is answered at the click.
- **`/booked-flights`** (Ted picked option A, tinted: mockup at
  https://claude.ai/artifact/S1eWy3iixRNAJNDzTyXNX8 and the nested one at
  https://claude.ai/artifact/UwRe9zbq4mL5BPLYd47wW4). Each leg of an itinerary carries its code as a
  chip under the flight number with its place beneath ("leg 3 of 4", or "one-way"), and the row has a
  left edge in one of two hues, indigo or teal, handed out by order of first appearance so two trips
  side by side never share one. Never amber: that is for problems. "Cancel trip" is a second line in
  the actions cell, red. Where it cannot be used it is greyed with its reason written out ("2 legs
  already left", "Flight cancelled"), because the iPad has no hover and the filter may hide the legs
  that left. The leg count is the whole booking's, so a leg hidden by the date filter still counts.
  - **Named losses.** The chip's label sits *under* the code, not beside it, so it adds no width to
    the Flight # column (the table's floors are measured). The second line is reserved on every row
    only when some row on the page has a trip, so a list with no itineraries is unchanged.
  - **Not measured:** the 820px table was screenshotted in headless Chrome at 820, 700 and 660px
    without `site.css`; no overflow. It has not been looked at on the iPad, nor between 618 and 641px.
- **Still open from (c):** the flights list shows a cancelled itinerary's legs (behind
  `?cancelled=show`) with no itinerary-level cancel marker; `FlightItineraryCancelled` is only
  consumed by `BookedItinerariesProjector`.

**What shipped, and where it differs from the design below.**
- **Pages.** `/book-flight/itinerary`, reached from a link on `/book-flight` (Ted's pick). One page and
  one POST: `action=preview` evaluates and writes nothing, `action=book` runs the same evaluation and
  writes only when it is clean. "Book N flights" appears only after a clean preview, and booking always
  re-reads the text box. OWNER-only via the existing `/book-flight/**` matcher, pinned by a matrix row.
- **Code.**
  - `UnitedItineraryParser` (allow-list, all problems at once, tolerant of the two column layouts a
    copy can produce, and of the narrow no-break space Apple puts before AM/PM).
  - `FlightItineraryBooking` (parse, then zones, then a **dry run of the real command** against the
    live schedule, so the preview shows exactly what booking would refuse).
  - `BookFlightItineraryCommand` emits N `FlightBooked` plus `FlightItineraryBooked` in one append.
  - `ItineraryPreview` puts every problem where it is fixed: under the box, under an airport's zone
    picker, or on the leg's row, with a link to the flight or train it collides with.
- **The itinerary id is the command id**, minted when the form is first shown, so one form cannot
  book the same trip twice. This matches how `/book-flight` uses the flight id.
  **Booking an id twice is answered, not errored (2026-10-01).** The id doubles as the write-ahead
  log's primary key, so a resubmit used to be a "Failed to save command to WAL" error page, and
  only by luck was it refused earlier as a pile of overlaps with itself. `FlightItineraryBooking.book`
  now folds the stream first (R1): a `FlightItineraryBooked` with this id gives
  `ItineraryEvaluation.alreadyBooked()`, nothing is evaluated or written, and the controller
  redirects to `/booked-flights` as the first submit did. It holds after the flights are cancelled,
  which is the stale-tab case the overlap check cannot see. Only `book` checks; a stale Preview
  still shows overlaps, and the Book button cannot appear from one.
- **Picked zones ride along.** A pick whose picker has gone (the airport resolved) is posted back as
  a hidden `airportZones[CODE]`; without it Book re-evaluated, found the airport unknown again, and
  sent Ted back to the picker he had just used.
- **Tests.** Plain unit tests for parser, command, booking and preview; a real-executor
  integration test (`FlightItineraryBookingIntegrationTest`: one command, three events, the
  refused paste writes nothing, the projector shows the legs); PIT clean over the plain-test
  classes, and the controller and template mutated by hand because PIT skips `spring`-tagged tests.
- **Zone picker (slice e) came along**: it is the only way an unknown airport can be booked at all.
- **Not yet:** the confirmation code is stored and shown only on the paste page's preview (OWNER-only).
  `/booked-flights` showing it,
  and grouping the legs, belongs with slice (c), which needs the grouping anyway.
- **Privacy, as designed.** The command log holds parsed legs only (a test serializes the command
  with the log's own mapper). The form bean's `toString` omits the paste. Both redaction tiers pin
  that the code never reaches the anonymous calendar.

**Cancel-itinerary decision (Ted, 2026-09-30): refuse once any leg has departed.** Before the first
departure the whole trip cancels at once; afterwards legs are cancelled one by one with Cancel
Flight. This supersedes the "flown legs stay" recommendation in section 6.

1. **What it's for.** Paste the text of an airline confirmation email onto an OWNER-only page, see
   a preview of every leg, and book them all with one submit. United's layout first. Parse the
   copied *text*, which is what the iPad has, not the email's HTML. The parser is plain Java, no
   LLM.
2. **Mapping.** One "Flight N of M" block becomes one leg, i.e. one `FlightBooked`:
   - `flightNumber` verbatim (`UA2091`, matching the stored `UA608`);
   - `airline` from the prefix (`UA` → `United Airlines`, which is how all 24 United bookings in
     the backup read);
   - airports from the bracketed code;
   - the date and time read separately for **both ends, never inferred** (a red-eye prints its own
     arrival date);
   - zones via `AirportZoneResolver`, as `BookFlightHandler` / `FlightEndpointZone` do.

   "N of M" is a completeness check: a gap or a short paste is a parse error, never a partial
   booking.
3. **Allow-list parsing, and what's deliberately dropped.** The parser reads six fields per leg, the
   confirmation code, and nothing else. Named losses: cabin class, "Operated by", seats, eTicket
   number, frequent-flyer number, traveler name.
4. **The itinerary as a concept.** Its purpose is **operating on the trip as one unit**: cancel
   it, apply a schedule change to it. It is not there for atomicity (section 5). New events, each
   with a golden sample:
   - `FlightItineraryBooked(itineraryId, airline, confirmationCode, List<FlightId> flightIds)`.
   - `FlightItineraryChanged(itineraryId, List<FlightId> flightIds, reason)`: a **full snapshot**
     of membership, like `FlightChanged`.
   - `FlightItineraryCancelled(itineraryId, reason)`.
   - It reuses `FlightCancelled` from Part 0.
   - **The confirmation code is stored**, on `FlightItineraryBooked` only. It's a new private value
     (booking references are on the redaction list), so `PublicCalendarProjector` never reads the
     itinerary events, and both redaction tiers are added (`PublicCalendarProjectorTest` plus
     `CalendarRedactionSecurityTest` `doesNotContain` the code). It's shown on `/booked-flights` as
     identification (OWNER-only).
   - Flights entered by hand belong to no itinerary. Adopting them into one is out of scope.
5. **Book: one command, several events.** `BookFlightItineraryCommand` checks **every** leg and
   returns `FlightItineraryBooked` plus one `FlightBooked` per leg.
   Atomicity is already provided: `CommandExecutor.execute` collects the command's whole stream
   into one list and appends it in one transaction.
   The rules per leg are the existing ones (departure in the future, arrival after departure,
   overlap with the folded `ScheduledLegs`), plus one new one: **the legs of one itinerary must not
   overlap each other**.
   Every refusal is reported together, each under its own leg. That departs from `overlapping`'s
   "first rather than all".
6. **Cancel an itinerary.** `CancelFlightItineraryCommand` emits one `FlightCancelled` per **live,
   not-yet-departed** leg, plus `FlightItineraryCancelled`, in one append.
7. **Schedule change by paste.** A paste whose confirmation code matches a live itinerary is a
   change, not a booking. The preview shows a **diff** before anything is written: moved legs as
   old → new, removed legs, added legs. It's a decision-support surface.
   On submit, one `ChangeFlightItineraryCommand` emits, in one append:
   - `FlightChanged(…, reason = "Airline schedule change")` for each matched leg that differs;
   - `FlightBooked` for each new leg;
   - `FlightCancelled` for each old leg with no match;
   - `FlightItineraryChanged`.

   **Matching** happens within the itinerary only: by flight number + departure day first, then by
   route, and whatever is left is added or removed.
   A leg that has already departed must match unchanged, or the preview reports an error.
   Overlap checks exclude the itinerary's own legs, generalising the existing `self` exclusion.
   A code that matches no itinerary is a new booking. One that matches a cancelled itinerary is
   refused, with the reason given.
8. **Why not `FlightItineraryAdded` + an `ItineraryToLegProcessor` automation.** The itinerary
   **event** is kept, because grouping is a real modelling need. The **processor** is not.
   - **It doesn't remove the atomicity problem, it moves it.** The itinerary event is atomic, but
     each command the processor then issues can still be refused (an overlap, or a departure that
     has passed by the time it runs). That's the same partial booking, only it happens *after* the
     page said "done".
   - **A refusal needs somewhere to go.** That means an `ItineraryLegRefused` event, a todo-list
     read model of legs not yet booked, and a surface to show refused legs. None of that exists,
     and the preview page catches the same problems before anything is written.
   - **Replay safety.** A processor is a reactor with write side effects. Boot replay must not
     re-issue commands, so the processor needs idempotency folded from events (R1).
     `EventReactor`/`subscribeAsync` (slice 0 of `FamilyEmailNotificationsPlan.md`) is the seam it
     would use. It has no production caller yet, and this would be a harder first user than sending
     an email.
   - **Schedule changes make it worse still.** A diff split into several asynchronous commands can
     half-apply a reroute: the new leg booked, the old one not cancelled. That is the
     overlapping-legs state the entry-time check exists to refuse.
   - Everything above is avoided because **one command emits the itinerary event and the leg
     events together**, in one append.
9. **Privacy.** `command_log` stores the **command** (`persister.saveCommand(commandId, command)`),
   not the request. The request only appears in `refuseWhenReadOnly`'s exception message. So:
   - the command carries parsed legs only, never the paste;
   - the `request` argument must not be the raw paste either, or its `toString` puts the eTicket
     number into an exception message and the logs;
   - the paste is never stored.

   The routes are OWNER-only, in `SecurityConfig` **and** `AuthorizationMatrixTest`.
10. **Zones for unknown airports.** The preview asks for that leg's zone, as `/book-flight` does.
    **Rejected:** a database table or a live OpenFlights fetch.
    - `FlightTimeZoneUpcaster` calls the resolver during boot replay, so a live source makes boot
      depend on a third party.
    - A table that changes an airport's zone silently moves every v1 event for that airport to a
      different instant.
    - OpenFlights looks unmaintained and carries ODbL obligations.

    **Fallback**, if unknown airports keep coming up: a bundled dataset snapshot behind an
    `infrastructure` adapter, with the upcaster pinned to a frozen table.
    `AeroDataBoxClient` already returns zone ids for a flight number + date, a possible per-leg
    fallback that is unexplored.
11. **Tests.**
    - Parser: the sample email, a red-eye, a short paste, an unknown airport, a mangled time.
    - Command rules, including overlap between legs of the same itinerary, and all refusals
      reported together.
    - Diff/matching: time moved, flight number changed on the same route, one leg split into two,
      a dropped leg, a flown leg contradicted.
    - Golden samples.
    - Both redaction tiers for the confirmation code.
    - A controller `exception → leg/field` table.
    - `@WebMvcTest` for the preview.
    - The stored command JSON contains no eTicket or frequent-flyer strings.
12. **Slices, in order.**
    - (a) Parser + preview, read-only. **Shipped 2026-09-30.**
    - (b) Book itinerary. **Shipped 2026-09-30.**
    - (c) Cancel itinerary (refused once any leg has departed), plus showing and grouping the
      confirmation code on `/booked-flights`.
    - (d) Schedule change by paste.
    - (e) Unknown-airport zone picker. **Shipped 2026-09-30, with (a).**
13. **Open questions for Ted.**
    - Other airlines' formats, or United only? (United only so far; another carrier's legs on a
      United ticket keep their code as the airline, e.g. "LH".)
    - Where does "Cancel itinerary" go?
    - Settled 2026-09-30: `FlightItineraryBooked`; refuse cancelling once a leg has departed; the
      paste page is linked from `/book-flight`.
