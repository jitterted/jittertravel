# Flight Itinerary Rebooking Plan

Status: **decisions (D1–D10), mockup and email wording approved; D10's heading band built, the rest not** (2026-10-14).
**2026-11-14: building the rest as one change (Ted), with HE80PP waiting for it.** Its BER→FRA leg
(UA8786, Thu Nov 26) is to be recorded through the new page as "I rebooked". If the build is not
live before then, cancel the leg by hand instead.

Vocabulary as agreed in `terms.md`, "Agreed: flight vocabulary": *leg*, *journey*, *itinerary*,
*confirmation code*, *schedule change*, *rebooking*, *dropped*, *cancelled*.

## The problem

Ted rebooked the return of an itinerary himself: MD7LKB was SFO→ORD, ORD→YOW, YOW→ORD, ORD→SFO,
and the return is now YYZ→SFO, still under MD7LKB. Two things went wrong:

1. **Nothing on `/booked-flights` says how to do it.** Each row offers Edit, Cancel and Cancel
   trip. Changing an itinerary is only reachable by pasting the new email into **Book flight
   itinerary**, which quietly turns into a schedule change when it recognises the code.
2. **The only change it can record is the airline's.** `ChangeFlightItineraryCommand` writes the
   reason "Airline schedule change" on `FlightItineraryChanged` and on every `FlightChanged`, and
   marks dropped legs `FlightCancellationCause.AIRLINE_SCHEDULE_CHANGE`. A rebooking recorded that
   way is a false history.

## Decisions

### D1. A rebooking is its own event

`FlightItineraryRebooked`, same fields as `FlightItineraryChanged` (`itineraryId`, the full
membership snapshot `flightIds`, `reason`, `rebookedOn`). `FlightItineraryChanged` stays for
schedule changes only. Event names say what happened, and these are two facts (Ted, 2026-10-12).

The other option, an optional initiator field on `FlightItineraryChanged`, was rejected: the name
would stay neutral while its Javadoc says "airline", and every reader would branch on a field.

Cost, measured: a second case in the four consumers of `FlightItineraryChanged` —
`TripOnTheBooks`, `BookedItinerariesProjector`, `CancelFlightItinerary`,
`FamilyNotificationActuator` — plus `EventTypes` and a `GoldenEventDeserializationTest` sample.
The `FlightChanged`s it writes carry the reason "Rebooked".

### D2. A leg a rebooking drops gets a new cause, `REBOOKED`

Not `MANUAL`. `TripOnTheBooks:79` uses the cause to decide whether a later paste may book a leg
again: a *dropped* leg comes back, a *cancelled* one is refused (`LegCancelledEarlier`). A leg
dropped by Ted's own rebooking is still dropped, so changing back to the original return must
work. A new enum value is additive under R7; the check at `TripOnTheBooks:79` accepts both
`AIRLINE_SCHEDULE_CHANGE` and `REBOOKED`.

### D3. Changing an itinerary starts from the itinerary

- **`/booked-flights`**: *superseded by D10 below* — the itinerary's actions moved to its heading
  band and the leg row reads `Edit · Cancel`. Originally: the actions cell reads
  `Edit flight · Cancel flight` on its first line
  and `Change itinerary · Cancel itinerary` on its second (mockup option B, Ted 2026-10-13). The
  longer labels say which thing each action is about, now that a row carries actions for two
  things. At 820px the second line wraps "Cancel itinerary" onto a line of its own, with no
  horizontal scroll, which is as wanted. Change is always present so nothing moves; greyed with
  the reason when it cannot apply ("Itinerary cancelled", "All legs already flown"). An itinerary
  with some legs flown can still be changed — only its future legs move.
- **`/booked-itineraries/{id}/change`**, OWNER-only (`SecurityConfig` and the `policy()` matrix in
  `AuthorizationMatrixTest`, same change). A recording surface: the itinerary's current legs (which
  one is this), a required **"Who changed it?"** with no default — *the airline changed the
  schedule* or *I rebooked* — the paste box, the existing added/changed/dropped preview (what it
  will do), and Apply. No default because the email cannot tell: United's looks the same either
  way.

### D4. Book flight itinerary links to the change page instead of applying a change

When a pasted confirmation code is already booked, the preview says so and links to that
itinerary's change page. It no longer applies the change itself.

### D5. Family email on a change: journey ends and times only

A schedule change or a rebooking emails the family (today neither does) when, for any journey of
the itinerary, comparing before with after:

- **where it starts or ends changes** — a connection does not count, and two home cities are the
  same place (`HomeCities`): returning to OAK instead of SFO is no change;
- **when it starts or ends moves by more than 2 hours** — the first leg's departure, the last
  leg's arrival. A connection's times do not count.

Example: UA 512 SFO→ORD moving from 08:00 to 08:40, arriving ORD 1 h 15 later, with the Ottawa
arrival unchanged, sends nothing. MD7LKB's return moving from YOW to YYZ sends one.

A journey ends after more than 24 hours on the ground (`terms.md`). `domain/Journey`
finds them (built for D10's band); this email is its second user.

The email lists the itinerary's flights still to come, then "the old flights that were changed":
every dropped leg **and every moved leg, with its old times** (Ted, 2026-10-13). Without the moved
ones, a schedule change that only shifts times would say nothing about what it shifted from.

**The wording needs Ted's approval before commit** (CLAUDE.md, outbound text): a new template in
`src/main/resources/email/`, shown to him as the literal subject and body.

## Approved (Ted, 2026-10-13)

- **The mockup is the spec**: https://claude.ai/artifact/KWWdZy6RvCHVDuJuKEnT72 — list layout B
  (`RowsOneLine.dc.html`), the change page as opened, previewed and refused, the Book-page
  hand-off, and the after-apply screen. Option A (`Main.dc.html`) was not chosen. Approved with
  the four things it adds beyond D1–D5:
  1. the preview says whether the family will be emailed, and why (a consequence, so it belongs
     on this recording page);
  2. a confirmation banner on `/booked-flights` after applying — new, since that page shows no
     message today, so the result has to travel back to it (e.g. a query parameter);
  3. the Book page carries the pasted email to the change page, so it is not pasted twice;
  4. "Change itinerary" stays live on a dropped or cancelled leg's row while its itinerary is.
     *Moot since D10*: the band carries it, whatever state a leg is in.
- **The email wording**: `src/main/resources/email/trip-changed.txt` as it stands, including
  "These are the old flights that were changed:" (Ted's line) and "Was due to arrive".

## Decided in review (Ted, 2026-10-13)

### D6. A flown leg missing from the paste is left alone

Today `ChangeFlightItineraryCommand` refuses a paste that leaves out a leg already departed
("history is not revised", 2026-10-03). A rebooking email sent mid-trip may list only the flights
still to come, so that rule would refuse every mid-trip rebooking. Instead, a departed leg the paste
does not mention counts as **unchanged**, not dropped. A departed leg that *is* pasted with different
times is still refused: history is still not revised.

Example: SFO→ORD and ORD→YOW flown, the email lists only YYZ→SFO. The two flown legs stay as they
are, YOW→ORD and ORD→SFO are dropped, YYZ→SFO is added.

This is a guess, not a known fact: no real mid-trip email has been seen. Revisit when one arrives.

**One arrived, 2026-11-14 (HE80PP), and it lists the flown legs.** Ted rebooked mid-trip to fly
home from FRA directly. United's email after the rebooking lists all three remaining flights,
flown ones included: "Flight 1 of 3 UA1642 SFO→EWR" and "Flight 2 of 3 UA962 EWR→BER", both
already flown, plus "Flight 3 of 3 UA59 FRA→SFO". BER→FRA is missing. So D6's leniency was not
needed for United. D6 stays: it costs nothing when the flown legs are there, and another airline
may leave them out. The email numbers its legs among the *new* set ("1 of 3"), and its seat block
still names `BER-FRA` with no seat. The parser has to ignore that line rather than read it as a
leg; check this against the real paste, which is the first fixture for the change page.

### D7. D5 compares where and when travel starts and ends, not how journeys are counted

When the number of journeys changes (a connection crosses 24 hours either way) and nothing moves by
more than 2 hours, **no email**. Example: ORD 10:00 Monday, YOW flight 11:00 Tuesday (two journeys)
moves to 09:30 (one journey). Nothing the family care about changed. A split that comes from a big
move is caught by the 2-hour rule anyway (ORD→SFO moving 30 hours moves the arrival at SFO).

### D8. A change emails the family whether or not they were told about the booking

MD7LKB was booked before family email existed. A change that passes D5 is sent anyway: the email
lists every flight still to come, so it stands on its own. This differs from conferences, which
email only when the change moves something the family were told.

### D9. No free-text reason

The two fixed choices only. The event type already says who changed it, and a recording page
carries nothing extra (closes the question that was open here).

### D10. Each itinerary on `/booked-flights` gets a heading band, which carries its actions

Ted could not see where one trip ends and the next begins: only a 4px coloured edge and the code
chip marked it. Mockup: https://claude.ai/artifact/Xi2Li8WEQRkMzsSX6gEQff (option B of
https://claude.ai/artifact/4sr6NRo7LTzrtMo2HYyWQi, chosen over separate cards and a heavy rule).

- A band above each itinerary's legs: the code, where it goes (`SFO → YOW → SFO`), its dates, its
  leg count, tinted in the trip's hue — a light tint, `color-mix(in srgb, var(--trip-soft) 40%, #fff)`,
  since the full `--trip-soft` read as heavy (Ted, 2026-10-13). The leg's chip drops the code and keeps `leg 1 of 4`.
- A run of flights booked on their own gets a quiet `Booked on its own` band, or a lone flight
  after a trip reads as its last leg.
- **`Change itinerary · Cancel itinerary` live on the band**, in the legs' actions column so they
  line up, and leave every leg row. The leg row reads `Edit · Cancel` again (Ted, 2026-10-13).
- Greyed with the reason written under it, never removed: Cancel when legs have left
  ("2 legs already flown"), Change when all have ("All legs already flown"), both when the itinerary
  is cancelled ("Itinerary cancelled").
- The band's confirmation code is a **badge**: a solid pill in the trip's hue, white monospace
  text (Ted, 2026-10-13).
- **After a change, legs are numbered among the legs still flying** (Ted, 2026-10-13). MD7LKB
  after the rebooking reads leg 1 of 3, 2 of 3, 3 of 3; the band says "3 legs" and its route
  `SFO → YOW, YYZ → SFO`. Today `FlightTrips.tripOf` numbers by position in the membership
  snapshot, which keeps dropped legs, so it would print "leg 5 of 5" — that changes. A dropped
  leg, shown under "Show cancelled", reads "was leg 3" with "Dropped Oct 13 · Rebooked" under it,
  and an added one wears "Added Oct 13" beside its route (mockup, "After the MD7LKB rebooking").
- **Open for build**: grouping assumes an itinerary's legs are consecutive in departure order. A
  hand-entered flight that departs *between* two legs of a trip breaks that; decide then whether it
  sits inside the trip's group or splits it.
- **Built 2026-10-13**: the band, the badge, Cancel itinerary on
  the band with its three greyed reasons ("N legs already flown" — "left" until 2026-11-14, when Ted
  found it read as "remaining" — "Itinerary cancelled", "Every leg
  cancelled"), `Booked on its own`, split runs, `Edit · Cancel` on the legs, and numbering among
  flying legs (`ItinerarySummary`, `FlightTrips.forList`). Journeys are worked out by
  `domain/Journey`, its first user. **Not yet built:** the band's Change itinerary link (waits for
  D3's page) and the "Added"/"Dropped · Rebooked" labels, which need the cancellation's cause on
  `BookedFlightView` (D2) — a dropped leg still shows today's "Cancelled <date>" line.
- Can ship before the rest of the plan: the band's Change link is the first thing D3's change page
  needs, and until then the band holds only Cancel itinerary in its usual slot.

MD7LKB's YYZ→SFO return has **not** been recorded yet, so there is no false history to correct. Ted
records it with the change page once it ships.

## Decided before the build (Ted, 2026-11-14)

### D11. Book never changes an itinerary, and the old paste-to-change path is removed entirely

"Pasting a changed itinerary into Book flight is a completely non-intuitive experience" (Ted). D4
stands as the replacement: a paste whose confirmation code is already booked is refused, with the
mockup's `BookAlreadyBooked` hand-off. That is the sentence naming the itinerary, plus a
**Change itinerary <code>** button that carries the email to the change page by POST. Nothing on
the Book page ever records a change, and `FlightItineraryBooking`'s schedule-change branch goes.
The rule generalizes to any kind: a create surface creates, and an edit starts from the thing being
edited.

### D12. A hand-cancelled leg is refused for a rebooking too

`LegCancelledEarlier` holds whichever choice is picked. Example: Ted cancels UA8786 BER→FRA by hand,
then a pasted rebooking lists it again. That paste is refused, and getting back onto that flight is
done with Book flight. Only a leg *dropped* by a change (cause `AIRLINE_SCHEDULE_CHANGE` or
`REBOOKED`) can come back through a paste (D2).

### D13. A flight departing between two legs of a trip splits the trip's group

This settles D10's "open for build". `/booked-flights` stays in strict departure order. HE80PP with a
lone TXL→MUC on Nov 15 reads: the HE80PP band with legs 1–2, a `Booked on its own` band with the
Nov 15 flight, then HE80PP's band again with leg 3. D10's "split runs" may already do this; check
it against a test and pin it if it does.

## Implementation notes

- **The family email needs the itinerary before the change.** `FamilyNotificationActuator` sees only
  the appended batch, and `FlightChanged` carries only new values. Do it the conference way: the
  actuator passes the itinerary id, and `NotifyFamily` folds the stream to the itinerary as it stood
  before this batch and after it.
- **One rule, two callers.** The preview's "the family will (not) be emailed, because …" (approved
  item 1) and the actuator both call the same D5 comparison, as `ItineraryChangePlan` is shared by
  preview and command. It needs `HomeCities` and an airport-to-city lookup, since legs carry codes
  (OAK), not cities.
- **`FamilyNotificationActuator.notificationsIn`** must count `FlightItineraryRebooked` members as
  part of a trip, beside `FlightItineraryChanged` (line 156). Without it, a leg a rebooking adds
  goes out as its own "flight booked" email as well as the trip-changed one.
- **A new `NotifiedFact`** for an itinerary change.
- **The Book page hands the paste over by POST** (approved item 3), a hidden field on a form, never
  a query parameter: the email holds a booking reference and names, and a URL lands in history and
  logs. The after-apply banner (approved item 2) carries an itinerary id and an outcome key, never
  the sentence.
- **"Who changed it?"** gets no browser `required`: a blank choice is a field error with its own
  `<span class="error">`, a row in the controller's exception→field unit test, and a
  `@WebMvcTest` proving it renders.
- **Golden samples**: `FlightItineraryRebooked`, and `FlightCancelled` with cause `REBOOKED`.
- **D4 rewrites existing tests**: the Book page tests that pin "a known code applies the change"
  change meaning, they are not only added to.
