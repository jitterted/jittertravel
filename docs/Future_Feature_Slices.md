# Future Feature Slices

Deferred slices and features, captured here so they aren't forgotten.

---

## Undo Cancel Hotel Booking

**Event:** `HotelBookingCancellationUndone(hotelBookingId)` (name TBD), reinstating the stay.

Cancelling a hotel has no time gate (removed 2026-08-13 — see `HotelCancelReplacePlan.md`), on the
reasoning that the real-world cancellation happens with the hotel and telling JitterTravel is a
manual step that lags. What makes that safe is that a mistaken cancel must be *cheap to reverse*,
and today it isn't: you have to re-enter the booking by hand, which mints a new `HotelBookingId` and
loses the original's history.

Cancelled stays now survive on `/booked-hotels` as a greyed-out "Canceled" row with no actions —
that row is where the **Undo** link belongs, and the reason it renders no other action is that Undo
is the only one that makes sense on a cancelled booking.

**Shape to work out when building:**

- The event has to reinstate into *every* read model that dropped the booking — the calendar, the
  itinerary, the schedule-problems report, both tentative-hotel projectors, and the hotel details
  view that backs the edit page. `HotelCancellationPropagationTest` is the natural place to mirror
  each of those cases in reverse.
- Cancel already replays state from the event stream (`CancelHotel.contextFor`), so the undo
  context is the same fold with the answer inverted: refuse unless the booking exists *and* is
  currently cancelled.
- The reinstated stay is the booking as it stood at cancellation, not a fresh one — same
  `HotelBookingId`, so the history stays in one place.
- Needs the usual round-trip coverage: an import branch plus a case in
  `CommandExportImportRoundTripTest`, and a golden sample in `GoldenEventDeserializationTest`.

**When to build:** When a mis-entered cancellation actually costs Ted a re-entry — or alongside
Phase 3 (Replace Hotel) of `HotelCancelReplacePlan.md`, which needs the same reinstate-a-booking
machinery.

---

## ConferenceCancelled

**Event:** `ConferenceCancelled(conferenceId, reason: String)`

Conferences (and eventually gatherings) need a cancellation mechanism. Deferred because the immediate need (re-entering a handful of gatherings-as-conferences) was resolved by waiting them out — they all expire within the current month.

**When to build:** When the first real cancelled conference arises, or as a prerequisite to any slice that needs to remove/retract a booking.

---

## infoUrl on ConferenceTentativelyPlanned

Conferences have event-specific web pages just like gatherings. Add `infoUrl: String` (empty string when absent) to `ConferenceTentativelyPlanned` and the plan-conference form.

**When to build:** When conference detail views or itinerary links are needed.

---

## mapsUrl on ConferenceTentativelyPlanned

Conferences have a physical venue. Add `mapsUrl: String` to `ConferenceTentativelyPlanned`, auto-computed from venue name + address in compact constructor if blank, and expose it in calendar/itinerary views.

**When to build:** When conference venue links are needed in views.

---

## Ability to View Calendar and Itinerary in Time Zone of Viewer

Anonymous viewers and logged-in users may be in a different time zone OWNER, so should see all calendar and itinerary information in their time zone.
All date-time entries in the system are entered in local date-time for the location (city-region-country) that they take place in.
Time zone from the browser should be used to localize the date-times of entries.
To override the default time zone, a drop-down menu is available on the Calendar and Itinerary views to select the time zone, defaulting to the time zone from the viewer's browser.

