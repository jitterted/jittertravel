# Flight Itinerary Rebooking Plan

Status: **decisions, mockup and email wording approved; nothing built** (2026-10-13).

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

- **`/booked-flights`**: the actions cell reads `Edit flight · Cancel flight` on its first line
  and `Change itinerary · Cancel itinerary` on its second (mockup option B, Ted 2026-10-13). The
  longer labels say which thing each action is about, now that a row carries actions for two
  things. At 820px the second line wraps "Cancel itinerary" onto a line of its own, with no
  horizontal scroll, which is as wanted. Change is always present so nothing moves; greyed with
  the reason when it cannot apply ("Itinerary cancelled", "All legs already left"). An itinerary
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

A journey ends after more than 24 hours on the ground (`terms.md`). Nothing in the code finds
journeys yet; this is its first user.

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
- **The email wording**: `src/main/resources/email/trip-changed.txt` as it stands, including
  "These are the old flights that were changed:" (Ted's line) and "Was due to arrive".

## Open

- Whether the change page also offers a free-text reason, or the two fixed reasons are enough.
  The mockup has none.
