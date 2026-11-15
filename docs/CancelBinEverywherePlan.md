# Cancel Bin Everywhere Plan

Status: **built** (2026-11-15), uncommitted at time of writing. The guard was written first and
failed on exactly the three gaps below (hotel × calendar, hotel × itinerary, private event ×
calendar), then the fix turned it green.

### As built — differences from the plan below

- **The guard's fixtures are real events through the real projectors** (`CalendarAggregator` over
  all seven calendar projectors; `ItineraryProjector`), not hand-built `CalendarEntry`s, so it
  proves the projector fills `cancelPath` in as well as the renderer drawing it. Mutation (e) below
  is the one that shows the difference.
- **No separate hotel-anonymous redaction case was needed:** `anonymousUserDoesNotSeeHotelEditLinkOrMapLink`
  already asserts `doesNotContain("/booked-hotels/")`, which covers the cancel path. The private
  event's anonymous case gained `doesNotContain("/planned-private-events/")`.
- `CalendarViewBuilder` gained a `bin(cancelPath, label)` helper shared by ground transfer and
  private event, mirroring `pencil(...)`.
- **Mutation-verified, each reverted:** (a) drop the itinerary hotel `cancelBin` → LODGING on
  /itinerary fails; (b) `Lodging` back to `pencil(...)` → LODGING on /calendar fails; (c) a stray
  `@GetMapping("/planned-mutants/{mutantId}/cancel")` → the routes test fails naming it; (d) drop
  the `NOT_ONE_ENTRY` exemption → the routes test fails naming `/booked-itineraries/…`;
  (e) `HotelCalendarProjector` passes `null` for `cancelPath` → the guard *and*
  `HotelCalendarProjectorTest.calendarEntryCarriesOwnerCancelPath` fail.

## The problem

A booked hotel can be canceled (`/booked-hotels/{id}/cancel`, since 2026-08-07), but the calendar
and the itinerary show it only a pencil. Flights and trains show pencil + bin; a ground transfer
shows a bin alone.

### How it happened

1. **2026-08-07 `655b64a`** — Cancel Hotel ships as a section *on the change-hotel page*. No kind
   has a bin yet; the pencil on the calendar and itinerary *is* the way to cancel. Nothing missing.
2. **2026-08-13 `f5971ef`, 2026-08-17 `a0f9085`** — cancel gets its own page and the section is
   removed from the edit page. The commit reasons "Cancel is reached from the per-row Cancel link
   on /booked-hotels … nothing is stranded" — true of `/booked-hotels`, never asked of the calendar
   and itinerary, whose route to cancel had been through that pencil. **This is where it broke.**
3. **2026-08-20 → 09-30** — bins arrive kind by kind, each in the slice that built that kind's
   cancel: ground transfer `77f430f`, train `0e0a0c7`, flight `9f11cc0`, then shared in
   `ActionIcons` (`a02d414`, `9a51d70`). Hotel cancel already existed, so no slice owned it.

**Why nothing failed:** nothing connects "this kind can be canceled" to "its entries carry a bin".
It is the same shape as the private event missing from the day menu for eleven days, which
`CalendarDayMenuTest` now guards — but that guard only fires when an `EntryKind` is *added*. Here
the kind already existed; it was the *cancel* that was added (or rather, moved). A guard keyed on
`EntryKind` alone would not have caught this.

## Audit — every kind, both surfaces

| Kind | Cancel route | Calendar | Itinerary |
|---|---|---|---|
| `FLIGHT` | `/booked-flights/{id}/cancel` | pencil + bin | pencil + bin |
| `TRAIN` | `/booked-trains/{id}/cancel` | pencil + bin | pencil + bin |
| `GROUND_TRANSFER` | `/ground-transfers/{id}/cancel` | bin | bin |
| `LODGING` | `/booked-hotels/{id}/cancel` | **pencil only** ✗ | **pencil only** ✗ |
| `PRIVATE_EVENT` | `/planned-private-events/{id}/cancel` | **nothing** ✗ | bin |
| `GATHERING` | none — no `GatheringCancelled` exists (out of scope in `archived/ChangeGatheringPlan.md`; Backlog `ConferenceCancelled` row) | pencil | pencil |
| `CONFERENCE` | `/conferences/{id}/decline` — a state-machine move, done from `/conferences` by decision (`CalendarViewBuilder.ownerActions`) | nothing | — |

Not tied to one entry: `/booked-itineraries/{id}/cancel` cancels a whole multi-leg itinerary and is
reached from `/booked-flights`; individual legs already carry their own bin.

**Gatherings are not a gap** — there is nothing to cancel. When a gathering cancel is built, the
guard below makes it bring its bin.

**The private event's calendar is a second, smaller gap.** `docs/Cleanup_Tasks.md` ("Change Private
Event") says the edit flow "brings the pencil *and* the calendar's bin with it" — but the bin does
not depend on the edit flow (a ground transfer has a bin and no pencil). See Q1.

## The fix

### Hotel (calendar)

- `EntryDetails.Lodging(mapsUrl, editPath)` → `Lodging(mapsUrl, editPath, cancelPath)`, javadoc
  updated to match `Flight`/`Train` (both paths OWNER-only).
- `HotelCalendarProjector.put` sets `cancelPath = editPath + "/cancel"`.
- `CalendarViewBuilder.ownerActions`:
  `case EntryDetails.Lodging d -> pencilAndBin(d.editPath(), d.cancelPath(), "Cancel hotel");`
  Pencil stays first, so its position does not change (affordances never move).
- **Redaction:** `Lodging` is the owner record; `PublicCalendarProjector` builds its own public
  details and never sees it, so the allow-list is untouched. Still add one
  `CalendarRedactionSecurityTest` assertion that the anonymous body `doesNotContain`
  `/booked-hotels/<id>/cancel` — cheap, and it is a new private path on a page strangers see.

### Hotel (itinerary)

- `ItineraryRenderer.renderHotel`: after the `editPencil(...)` at line 312 add
  `cancelBin("/booked-hotels/" + id + "/cancel", "Cancel hotel")`, owner-only exactly as the pencil
  is. Same order as flights (line 236–237).

### Private event (calendar) — only if Q1 says yes

- `EntryDetails.PrivateEvent()` → `PrivateEvent(String cancelPath)`, set in
  `PrivateEventCalendarProjector`.
- `ownerActions`: move `PrivateEvent` out of the `List.of()` arm into the `GroundTransfer` shape
  (bin alone, no pencil — there is no edit page to point at).
- Same redaction assertion as the hotel. The public side collapses a private event to `Busy`, which
  has no slot for a path, so nothing can reach the anonymous page — the assertion proves it.

## The guard — `EveryCancelableKindShowsItsBinTest`

The claim: **every entry-level cancel route the app serves is reachable by a bin from that kind's
entries on both the calendar and the itinerary.** Two mechanisms, as `CalendarDayMenuTest` has, plus
a third because this failure came from the route side, not the kind side:

1. **Route discovery (the part that would have caught this).** ArchUnit imports
   `dev.ted.jittertravel.web`, collects every `@GetMapping` value ending `/cancel`. No hand-kept
   list of routes: a new cancel controller is a new discovered route with nobody adding it.
2. **An exhaustive switch over `EntryKind`** — `cancelRoute(kind)` returns the route template
   (`"/booked-hotels/{hotelBookingId}/cancel"`) or `Optional.empty()` with a comment saying why
   (`GATHERING`: no cancel exists; `CONFERENCE`: declined from `/conferences`). A new kind stops the
   class compiling until someone decides.
3. **Discovered = claimed.** The set from (1) must equal the set the switch claims plus a tiny,
   commented exemption set — today only `/booked-itineraries/{itineraryId}/cancel`. An unclaimed
   route fails *by name*: "`/booked-hotels/{hotelBookingId}/cancel` is served but no `EntryKind`
   claims it". A claimed route nobody serves fails too, so the switch cannot rot.

Then, **for every kind with a route**, parameterized from `EntryKind.values()`:

- build one owner `CalendarEntry` for that kind and render it through `CalendarViewBuilder` as the
  owner; assert the whole attribute `class="cancel-bin" href="<route with sample id>"`.
- build one `ItineraryEntry` for that kind and render it through `ItineraryRenderer` as the owner;
  same assertion.

The fixtures come from a second exhaustive switch (`sampleCalendarEntry(kind)`,
`sampleItineraryEntry(kind)`), so adding a kind forces a fixture too. Kinds with no route are
skipped *by the switch*, not by a filter in the test, so "no bin" is always a written-down answer.

**What it deliberately does not do:** check the owner/family split (each renderer's existing
"no bin for non-owner" tests own that), or the bin's position relative to the pencil (existing
per-kind tests own that). One claim per test.

**Known limits, so nobody assumes more cover:**
- It finds routes by the `/cancel` suffix. A cancel named otherwise (`/decline`, `/withdraw`) is
  invisible to it — the switch comment for `CONFERENCE` is where that is recorded.
- It covers the calendar and the itinerary only. `/schedule-problems` fix links are `ProblemFix`'s
  business and have their own exhaustive switch.

**Mutation-verify before commit** — done; see "As built" above.

## Tests that change

- `CalendarViewBuilderTest` / `ItineraryRendererTest`: hotel cases mirroring the train ones
  (bin after pencil for owner; no bin for non-owner, asserted on the whole attribute).
- `HotelCalendarProjectorTest`: `cancelPath` is the edit path + `/cancel`.
- Any test constructing `EntryDetails.Lodging(...)` gets the third argument.
- `CalendarRedactionSecurityTest`: the anonymous-absence assertion(s) above.
- PIT default run before commit; `-Ppit-spring` narrowed to the redaction test before push.

## Decisions (Ted, 2026-11-15)

- **Q1 — yes.** The private event's calendar bin ships in this change, bin alone, ahead of the edit
  flow. Update the "Change Private Event" bullet in `docs/Cleanup_Tasks.md` in the same change: the
  edit flow now brings only the pencil.
- **Q2 — `"Cancel hotel"`** on both surfaces, matching "Cancel flight" / "Cancel train".
