# Future Feature Slices

Deferred slices and features, captured here so they aren't forgotten.

---

## Time-ordered calendar days (abandoning swimlanes)

**Deferred by Ted, 2026-08-20**, on seeing a 3:55 PM flight render *above* the 1:00 PM ground
transfer that fed it. The lanes stay as they are for now; this records what changing it costs.

The calendar is not time-ordered within a day and cannot be made so without changing its layout
model. Each week is a CSS grid of fixed per-kind **lane bands**, laid out in `EntryKind` declaration
order (conference, gathering, private event, flight, train, ground transfer, lodging), with entries
stacked into sub-rows inside their own band. An entry therefore sits in its kind's band regardless
of when it happens — the same reason a 9 PM gathering renders above a 7 AM flight.

**Reordering `EntryKind` does not fix it.** A ground transfer runs both ways: put it above FLIGHT
and the taxi *to* the airport reads correctly while the taxi *from* the airport reads backwards.
No fixed ordering of kinds can be right for both, because the question is about time, not kind.

**The itinerary already does this** — `ItineraryProjector.entriesForDate` sorts across kinds by
`anchorTime`, pinned by `aTransferToTheAirportSortsAboveTheLaterFlightItFeeds`. So the time-ordered
view exists; it is the calendar that cannot join in.

**Shape to work out when building:**

- Lanes are what `CalendarViewBuilder.renderWeek` is built around: `byKind` grouping, per-kind
  sub-row allocation, `kindOffset` accumulation, and the `grid-row` each segment lands on. Ordering
  by time means allocating rows from one chronologically-sorted list instead, so most of that
  method goes.
- **Multi-day entries are the hard part**, and the reason lanes exist at all. A conference or hotel
  stay spans a week and currently occupies one continuous band across it; in a time-ordered layout
  a multi-day bar has no single row it can sit in without colliding with each day's own timed
  entries. Likely needs multi-day entries kept in a band of their own above the timed ones — i.e.
  a partial retreat to lanes, split by *duration* rather than by kind.
- The kind colours, the CSS class per kind (`entry--ground_transfer`), and the continuation
  arrows are all independent of row allocation and should survive untouched.
- `CalendarViewBuilderTest` has substantial coverage of sub-row stacking and lane offsets; expect
  to rewrite it rather than extend it.

**When to build:** when reading a day's real order off the calendar matters more than seeing kinds
grouped — most likely alongside the `EntryDetails` refactor in
`RendererVsProjectorResponsibilities.md`, which is already going to touch `CalendarEntry` and the
builder. Not urgent: the itinerary answers "what order did this day go in" today.

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
- Needs a golden sample in `GoldenEventDeserializationTest` for the new event (standard practice
  for every new event). No import/round-trip branch: backup/restore is event-oriented now
  (`archived/EventOrientedBackupRestorePlan.md`), so a new event is stored and restored verbatim — the old
  `CommandExportImportRoundTripTest` and per-command import branches were retired with it.

**When to build:** When a mis-entered cancellation actually costs Ted a re-entry — or alongside
Phase 3 (Replace Hotel) of `HotelCancelReplacePlan.md`, which needs the same reinstate-a-booking
machinery.

---

## ConferenceCancelled

**Event:** `ConferenceCancelled(conferenceId, reason: String)`

**Partly built.** The `ConferenceCancelled` event record and the projector branches that drop the
conference already exist — `MigrateConferenceToGathering` emits it. What's missing is an
**owner-facing cancel action** (a route + form to raise it directly). Note this means *the
organizers cancelled the conference*, which is a different fact from Ted deciding not to attend —
that decline slice shipped 2026-08-16 (`ConferenceAttendanceDeclined`, `c08896a`).

**When to build:** When the first conference the organizers cancel needs recording, or as a
prerequisite to any slice that needs to remove/retract a booking.

---

## infoUrl on ConferencePlanned — **BUILT 2026-08-22**

Conferences have event-specific web pages just like gatherings. Add `infoUrl: String` (empty string when absent) to `ConferencePlanned` and the plan-conference form.

**Shipped as designed**, as slice 4b of `archived/ConferenceSubmissionTrackingPlan.md`: the empty-string-when-absent shape needed no schema bump and no upcaster (the compact constructor normalizes, as `GatheringPlanned` always has). **Public** — it reaches `EntryDetails.PublicConference` as well as `EntryDetails.Conference`, so the title links out on the anonymous calendar too, which is what CLAUDE.md's "conferences in full" already said. Rendered on both calendars, the itinerary and `/conferences`.

**Not built with it:** any way to change it afterwards — there is no change-conference flow, so it is plan-time only. The neighbouring `mapsUrl` item below is untouched.

---

## mapsUrl on ConferencePlanned

Conferences have a physical venue. Add `mapsUrl: String` to `ConferencePlanned`, auto-computed from venue name + address in compact constructor if blank, and expose it in calendar/itinerary views.

**When to build:** When conference venue links are needed in views.

---

## Ability to View Calendar and Itinerary in Time Zone of Viewer

Anonymous viewers and logged-in users may be in a different time zone OWNER, so should see all calendar and itinerary information in their time zone.
All date-time entries in the system are entered in local date-time for the location (city-region-country) that they take place in.
Time zone from the browser should be used to localize the date-times of entries.
To override the default time zone, a drop-down menu is available on the Calendar and Itinerary views to select the time zone, defaulting to the time zone from the viewer's browser.

---

## Near-name check on Plan Conference — warn and proceed

**Requested by Ted, 2026-09-07**, from "as the number of conferences grows, I need a quick way to
find if I already added one". That question has two halves and they landed in different places: the
*looking it up* half belongs on **`/calendar`**, not `/conferences` (there a browser page search is
enough), and is a separate open question. This slice is the other half — catching the duplicate at
the moment it would be created, so nothing has to be remembered.

**Nothing checks today.** `PlanConferenceCommand` validates only its dates; two identical
conferences can be planned, and they become two independent streams with two ids, two CFP records
and two calendar entries.

**Warn and proceed — never block.** The warning is information, not a gate:

- **A similar name is not a duplicate.** There are several regional Devoxx events: "Devoxx UK" does
  not duplicate "Devoxx Belgium", and a rule that refuses either is wrong. Same for the same
  conference in consecutive years.
- So the page **shows** what it found and lets Ted go ahead. The submit that follows the warning
  must succeed with the values already typed — see the open question below about how the second
  submit is distinguished from the first.

**Where:** `/plan-conference`. That is a decision-support surface (CLAUDE.md, "A recording surface
needs no decision-support information"), so it carries the context that makes the choice answerable
— the matches, with their dates, cities and a link to each.

**Scope of the search: every conference, ignoring both list filters.** Past *and* dropped. The
dropped ones matter most: a conference Ted declined is invisible on `/conferences` by default, which
makes it the one he is most likely to plan again — and re-adding it would silently resurrect
something he had already answered.

**The matching rule is the whole risk, and it gets validated before it ships.** Follow the
`EnteredLocation` precedent (CLAUDE.md, "A city that is really a station…"): write the rule, run it
over every conference pair in the most recent production backup, and count what it would have said.
**When a rule misfires, shorten it — do not weaken it.** Note what validation can and cannot answer:
it says whether the rule *misfires*, never whether the feature is worth building (there may be zero
duplicates in the log today because Ted has been checking by hand).

**Open questions, none of them decided:**

1. **What counts as "near".** Token overlap? Edit distance on a normalized name? Shared leading
   word? "Devoxx UK" vs "Devoxx Belgium" is the case that must *not* fire while still being shown,
   which suggests the answer is a **similarity score with a low bar and no threshold that hides
   anything** — show every plausible match rather than trying to be exact.
2. **Does the date matter?** Same name a year apart is normal and must not be flagged as a mistake;
   same name a week apart almost certainly is. Whether proximity strengthens the warning, or is only
   shown as a fact, is undecided.
3. **How the second submit is distinguished from the first.** A hidden acknowledged-matches field, a
   distinct button, or a re-post of the same values? Whatever it is must not lose the typed values
   (CLAUDE.md: a rejected form reports on the form page and keeps what was typed) and must not let a
   *later* edit slip past the check unnoticed.
4. **The other kinds.** Gatherings and private events have exactly the same exposure. Extending is a
   decision, not a chore — same as `EnteredLocation`, which is deliberately wired to trains and
   hotels only.

