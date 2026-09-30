# Flight itineraries: Cancel Flight, then paste, cancel and schedule-change a whole trip

Planned 2026-09-29 (Ted). **Part 0 (Cancel Flight) shipped 2026-09-29; Parts 1 and 2 are not
started.** Still open from Part 0: the `ProblemFix` link for a flight in an overlap (deferred on
purpose, below).

Prompted by Ted, 2026-09-29: *"how hard would it be to retrieve flight bookings from United
airlines using my booking confirmation code?"* The answer was that no API exists for that, so the
realistic path is pasting the confirmation email. That led to this plan. The sample itinerary had four
legs (SFO→ORD→YOW and back). Ted then asked for **schedule changes** too, and for **an event that
groups the legs into an itinerary**, because *"changing/canceling an entire itinerary is painful
now, has to be done for each leg"*. Designing that showed there is no Cancel Flight at all, and Ted
asked for it now: *"i need the cancel flight feature now, can you do that as the first independent
slice"*, kept *"to the minimum required for flight cancellation"*.

**One naming question left for Ted:** he said `FlightItineraryCreated`. His own event-naming rule
is "past-tense facts, never CRUD" (`CfpOpened`, not `CfpWindowRecorded`), and "Created" is CRUD. What
happened is that the itinerary was **booked**, so this doc uses `FlightItineraryBooked` until he
decides.

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

## Part 1: add YOW to the curated airport tables (not started)

The sample email's Ottawa legs cannot resolve a zone. Ted: *"mostly a one-off, add YOW"*. The 09-18
production backup has no `YOW`, so no stored flight's replay can change.
- Add `"YOW"` to the `America/Toronto` line of `AirportZoneResolver`.
- Add `Map.entry("YOW", "Ottawa")` to `StaticAirportCityResolver`.
- One test case each, mutation-verified.

---

## Part 2: itineraries (design, not started)

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
    - (a) Parser + preview, read-only.
    - (b) Book itinerary.
    - (c) Cancel itinerary.
    - (d) Schedule change by paste.
    - (e) Unknown-airport zone picker.
13. **Open questions for Ted.**
    - `FlightItineraryCreated` vs `FlightItineraryBooked`.
    - Cancelling with flown legs: leave them as history (recommended), or refuse?
    - Other airlines' formats, or United only?
    - Where does the paste page live, and where does "Cancel itinerary" go?
