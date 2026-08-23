# Booking Provenance — how a hotel, flight, or train was booked

**Status: `open` — requirement captured 2026-08-19 (Ted), nothing designed, nothing built.**
This is a requirement record, not an implementation plan. The open questions below are genuinely
open; do not treat any sketch here as a decision.

## The requirement

Every booked hotel, flight, and train should record **how it was booked** — the channel it came
through, not just the thing that was booked. Ted's own examples:

| kind | booked direct | booked through a third party |
|---|---|---|
| hotel | the hotel's own site | Booking.com |
| flight | the airline's site | Expedia.com |
| train | Deutsche Bahn directly | a reseller such as railfinder |

## Why it matters

Two reasons, both operational rather than informational:

1. **Where the ticket is.** The confirmation lives in whichever account made the booking. Knowing
   the channel is what turns "I have a train on Tuesday" into "the ticket is in the DB app" versus
   "the PDF is in the reseller's email".
2. **How to cancel or change it** — and *who* is allowed to. A Booking.com reservation is not
   cancelled by phoning the hotel; a reseller's rail ticket may not be changeable at the operator's
   counter; an airline will often decline to touch a ticket issued by an agency. The channel decides
   which door to knock on, and it is the thing you least want to work out under time pressure.

This pairs directly with the **cancel-by deadline** already on `HotelBooked` and the iCal alarms
built from it (`archived/CalendarSubscriptionFeedPlan.md`): the alarm says *when* to act, and provenance says
*where and how*. The obvious first surface is the cancel-hotel page, which today tells Ted the
deadline and nothing about the channel.

## Open questions — all of them

1. **Shape.** A provider is open-ended (Booking.com, Expedia, the hotel, the airline, DB, Trainline,
   a reseller), so a closed enum will not hold. Likely a small value type carrying a channel
   (direct vs third party) *and* a provider name, possibly with a booking reference and a
   "manage this booking" URL — but whether reference and URL belong here or are their own concern
   is undecided. Note the no-null-Strings rule: absent means `""`.
2. **Where it lives.** New fields on `HotelBooked` / `FlightBooked` / `TrainBooked` (plus the
   `*Changed` events), which means a **schema bump per event type with an upcaster increment**,
   exactly like `ConferenceFormat` on `ConferencePlanned` — or a separate "how it was booked" event
   that can be recorded after the fact. The second is friendlier to backfill; the first keeps one
   fact in one place. Undecided.
3. **Backfill.** Existing bookings have no provenance. The conference precedent says a one-off pass
   through the real UI recording the end state, never a reconstruction — the same approach probably
   applies, via the existing change/edit forms.
4. **Surfaces.** `/itinerary` entries and the `/booked-hotels`, `/booked-flights`, `/booked-trains`
   rows are candidates; the **cancel-hotel page** is where it earns its keep. Whether it appears in
   the iCal feed's event description is a separate question.

## Redaction — settled before it starts

Provenance is **OWNER-only**, no discussion needed: CLAUDE.md already classes booking references and
any URL that resolves to a booking as private, and the channel names the vendor holding Ted's
reservation. It must **never** enter `CalendarEntry`. The safe pattern is the one slice 2 of
`archived/ConferenceSubmissionTrackingPlan.md` used for `AttendanceBasis` — keep it out of the calendar's
read model entirely rather than carrying it and stripping it in the redactor. If it ever reaches a
view, it needs a `CalendarRedactionSecurityTest` case asserting its absence for anonymous viewers.
