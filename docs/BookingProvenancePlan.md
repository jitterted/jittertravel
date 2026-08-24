# Booking Provenance — how a hotel, flight, or train was booked

**Status: `open` — requirement captured 2026-08-19 (Ted), designed 2026-08-24 (Ted), nothing built.**

The doc was reviewed against the tree on 2026-08-24 (`701f716`), which found four stale claims, and
Ted then answered every open question in the same session. What was a requirement record with five
open questions is now a **designed slice**: the model, the audience, the scope and the surfaces are
decided below, and what remains is writing it.

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

## Decisions (Ted, 2026-08-24)

### D1 — One free-text String: the provider name

Not an enum, and not a `channel` + `provider` pair. **Free text, one component per booking.**

The reasoning is Ted's, and it is not the usual free-text argument. The full provenance of a booking
is **rarely a short phrase** — there is a reservation number, usually a URL to the booking page, and
a *direct* booking has those just as much as an agency one, so "direct" is not the empty case an
enum would make it. But of everything that could be stored, **the provider name is the load-bearing
part, because it is the index into his email**: knowing it was Booking.com is what lets him find the
confirmation, the number and the link in thirty seconds. The rest is convenience on top of that.

This lands on the free-text side of the rule settled on 2026-08-23 (`ScheduledTransitTripPlan.md`
D6, and `GroundTransferPlanned.mode` shipping in `5aea118`): **enum where the value drives
something, free text where it only renders.** The provider name picks no icon and no branch — it
renders and stops — and the value set is open (Booking.com, Expedia, railfinder, Hilton, the airline,
DB). An enum would end in `OTHER` plus this same field.

Provisional name: `bookedThrough`. It is the one decision here that costs nothing to change.

### D2 — Slice 1 is the provider alone

**Reservation number and manage-booking URL are not in this slice.** They are the two fields that
would make a cancel page one-click actionable, and they are also six forms' worth of inputs that may
stay blank forever. Ship the provider, use it for a trip or two, and find out from real use whether
typing a confirmation number ever beats searching email for the provider name.

If they are ever wanted, they arrive as **further additive components** on the same events — no
reshaping, and still no schema bump, since blank stays legal (D3).

### D3 — Additive components on the six existing events; no schema bump

`HotelBooked`, `HotelChanged`, `FlightBooked`, `FlightChanged`, `TrainBooked`, `TrainChanged` each
gain the component, normalized `null → ""` in the compact constructor beside the existing
normalizations (`mapsUrl` on the hotel pair, `serviceId` on the train pair).

**No `schema_version` bump, no upcaster, no migration.** This is additive in the strict sense
`GoldenEventDeserializationTest` was built to allow: old JSON has no such property, so the component
arrives null and becomes `""` — unrecorded. `GroundTransferPlanned` did exactly this on 2026-08-23
and stayed at version 1; `ScheduledTransitTripPlan.md` plans the same for `TransitMode`. Backup files
at v2 and v3 keep restoring. (`ConferenceFormat` is the contrast, not the model — it is the one type
past v2, and an earlier draft of this doc wrongly held it up as what provenance would have to copy.)

Leave the existing golden samples untouched as the proof that a pre-provenance payload still reads,
and add one beside each carrying a provider — the pattern `5aea118` used.

**This closes the "separate after-the-fact event" alternative** rather than deciding against it on
its merits: its whole argument was that components meant a bump and an upcaster per type, and that
is no longer true. Backfill, its other argument, is handled by D6.

**The `*Changed` events are full snapshots, and that is the trap.** `HotelChanged`'s own javadoc
records that an edit omitting `cancelBy` *clears* it, so the edit form must round-trip the current
value. Provenance inherits that obligation on all three change forms — a test per form that edits an
unrelated field and asserts the provider survives.

### D4 — OWNER only

`/itinerary` is **out**, and so is the anonymous calendar. One rule, one audience, no per-field
splitting. Ted considered giving FAMILY the provider name on the itinerary — a family member who
knows the stay is on Booking.com can act in an emergency — and chose not to: it is account-shaped
data, and `/itinerary` is the page shared most widely.

Note this **reverses nothing and settles a contradiction**: the 2026-08-19 capture called provenance
OWNER-only while listing `/itinerary` as a candidate surface, and `/itinerary` is
`hasAnyRole("FAMILY", "OWNER")`. Both could not stand.

### D5 — Surfaces: the three list pages and the three detail/edit pages

- **A column on `/booked-hotels`, `/booked-flights`, `/booked-trains`.** Blank cell when unrecorded
  — no placeholder, nothing to grey out. On hotels it sits beside the existing Cancel-by column,
  which is the pairing the requirement describes: the deadline says *when*, the provider says *who*.
- **A field on `/booked-hotels/{id}`, `/booked-flights/{id}`, `/booked-trains/{id}`** (and the three
  book forms). Near-automatic, since the value has to be enterable somewhere — but listed explicitly
  because of the round-trip obligation in D3.

**Not the cancel-hotel page, and the reason is a rule rather than a preference** (Ted, 2026-08-24).
It looks like the obvious home — the requirement's own rationale is "the moment you are about to
cancel with someone" — but that reads the page backwards. **Reaching `/booked-hotels/{id}/cancel`
means Ted has already cancelled with the provider in the real world and is recording that it
happened.** He needed the provider *before* he got there, which is exactly where D5 puts it: the
`/booked-hotels` column he was looking at when he decided to cancel.

Generalize it, because it decides more than this field: **a recording surface does not need
decision-support information.** JitterTravel is mostly a record of things that already happened
outside the app, so anything that would have helped Ted *decide or act* belongs on the surface he was
standing on then, not on the one where he reports the outcome. A recording page wants identification
(which booking is this?) and consequences (what this removes) — the cancel page's summary card and
warning, which is what it already shows. This is the same reasoning that removed the check-in gate
from Cancel Hotel (`HotelCancelReplacePlan.md`): telling the app is a separate manual step that
routinely lags, so "check-in has passed" only ever meant the data entry was late.

**Not the iCal feed either.** It would be one line in `HotelCancelDeadlineSource.deadlineDescription`;
it is unredacted OWNER data gated only by a URL token, and the string would then sit in the iOS
Calendar app. Note the deadline alarm is *not* a recording surface — it fires before Ted acts — so
the rule above does not rule it out; it simply was not wanted.

### D6 — Backfill: a declared task, recorded through the real forms

Existing bookings have no provenance. Declare a `OneOffTaskRegistry` entry — the registry is empty
today, which is its normal resting state — and let the OWNER-only banner and `/admin/tasks` carry it.
`PostDeployTaskBannerPlan.md` already names booking provenance as a future entry there.

Record the **end state through the existing change forms**, never a reconstruction. That is the
conference precedent, and it is a completed one rather than a proposal: the attendance backfill ran
in production on 2026-08-21 and the entry was deleted the same day.

### D7 — Coordinate with `ScheduledTransitTripPlan.md`

That plan adds `TransitMode` to `TrainBooked` and `TrainChanged` — the same two events, the same
compact-constructor normalization, the same book and change forms. Whichever lands second is cheaper
sequentially than concurrently.

## Redaction — settled, in the allow-list world

Provenance is **never public**, and per D4 never FAMILY either. CLAUDE.md already classes booking
references and any URL that resolves to a booking as private, and the provider names the vendor
holding Ted's reservation.

An earlier draft of this section said "it must never enter `CalendarEntry`, rather than carrying it
and stripping it in the redactor". Both halves are out of date: `CalendarEntryRedactor` was deleted
on 2026-08-21, and `CalendarEntry` is now the record **both** audiences use — what separates them is
the `EntryDetails` it carries. The rule that replaces it:

- **Provenance must never appear on an `EntryDetails.Publishable` record** (`PublicFlight`,
  `PublicTrain`, `PublicLodging`, `PublicGroundTransfer`, `PublicConference`, `PublicGathering`,
  `Busy`). Those records have no slot for a maps URL or a hotel name for exactly this reason, and
  `PublicCalendarProjector`'s `entry(...)` helper takes a `Publishable` as its last argument, so the
  compiler is the check.
- **`PublicCalendarProjector` must not read the component at all.** Deny-by-default means the default
  is "don't read it" — the same thing that keeps `GroundTransferPlanned.mode` and
  `CfpOpened.submissionUrl` off the public calendar.
- Per D5 the provider does not reach a calendar entry at all, owner or public — it lives on the
  booked-list views and the detail views. That is the strongest version of the rule: not carried and
  stripped, but never carried.
- The pattern to copy where a value *does* have to be read is the one slice 2 of
  `archived/ConferenceSubmissionTrackingPlan.md` used for `AttendanceBasis`: read it where the
  decision is made, discard it, publish only the conclusion.

**Both tiers of test regardless**, because the events themselves now carry it: a
`PublicCalendarProjectorTest` case asserting the provider is absent from what the projector emits,
and a `CalendarRedactionSecurityTest` case asserting the rendered anonymous body `doesNotContain` it
through the real security chain — with the anonymous fixture built by driving real events through a
real `PublicCalendarProjector`. Follow the `mode` precedent and give the redaction fixture bookings
that *have* a provider, so every other anonymous assertion runs against events with something to
leak.

## Corrections made on 2026-08-24

Recorded so a reader of the git history knows which claims were wrong rather than merely superseded:

1. **"the cancel-hotel page, which today tells Ted the deadline"** — it does not. `cancel-hotel.html`
   shows hotel name, city, country, check-in, check-out, the warning and the reason field. The
   deadline lives in the `/booked-hotels` Cancel-by column (`BookedHotelsRenderer.cancelByCell`) and
   in the feed's VEVENT (`HotelCancelDeadlineSource`).
2. **"a schema bump per event type with an upcaster increment, exactly like `ConferenceFormat`"** —
   no longer the rule; see D3.
3. **"keep it out of the calendar read model rather than stripping it in the redactor"** — the
   redactor was deleted 2026-08-21; see the redaction section.
4. **OWNER-only while listing `/itinerary`** — a contradiction, settled by D4.
