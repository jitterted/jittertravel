# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`. For an index of every
plan doc and its status — including these items — see `Backlog.md`.

Three sections: **Open** is work that is wanted, **Deferred (until needed)** is work whose shape is
known but whose need has not arrived — each item names the trigger that would promote it — and
**Done** is the record.

Some items below were **lifted out of a shipped plan doc** when that doc moved to `docs/archived/`
(2026-08-21). The archived doc keeps the reasoning and is still worth reading before starting;
this list is what makes the item findable, because an archived doc is history and nobody scans it
for open work.

## Open

- [x] **`/calendar` never names the month except on the 1st.** **Done 2026-08-31.** The month tint is too faint to answer
      "which month am I looking at", and the only text naming one is the day label on the 1st — so
      any week not containing a 1st leaves a reader counting. Ted, 2026-08-31: *"i completely lose
      what month it is for weeks that have entries."* Note **weeks that have entries**: this is the
      ordinary case, not a side effect of collapsing anything.
      **Built and then reverted the same day**, only because it rode in on the quiet-week-runs branch
      (`archived/QuietWeekRunsPlan.md` D5b) — Ted's verdict on it was *"i like the months"*. The
      shape: a sticky `.calendar-month-header` band, one per month, parked under the weekday header
      (`top: var(--calendar-weekday-header-height)`, `z-index` below the header's 10). A week is
      filed under the month its **Sunday** falls in, so a straddling week belongs to one month and
      not both; the alternative puts two bands between two adjacent weeks. One test —
      `everyMonthGetsOneBandAboveTheFirstWeekThatStartsInIt` — mutation-verified by filing a week
      under its Saturday instead.
      Re-landed on its own, as intended — then **removed again 2026-09-01** with the year overview
      (`archived/YearOverviewPlan.md`). Two reasons, and the second is why it cannot come back in
      this shape: they were built for orientation *while scrolling* to find a month, which the jump
      replaces; and a week is filed under its **Sunday**, so Sep 1–5 rendered under a band reading
      "AUGUST 2026", and a `gridEnd` on the 1st–5th left a month with no band at all. That is also
      why they could not be the jump anchors — those are the month-start day cells, whose set is
      complete by construction. The 1st still names its month in its own day label.
- [x] **Nothing enforced that the public calendar handles the same removal events the owner's does.**
      Raised by review of the S2 refactor 2026-08-21; **done the same day** — Ted chose
      lifecycle-propagation scenarios over a source-scan convention test, the option that fits the
      standing preference (sealing `Event` was rejected). `CalendarRemovalPropagationTest` drives one
      event stream into **both** read models and asserts the entry is in both, then gone from both,
      for all four removal events (`HotelBookingCancelled`, `GroundTransferCancelled`,
      `ConferenceCancelled`, `ConferenceAttendanceDeclined`) plus the confirmed-then-cancelled
      conference, whose entry both projectors have already rewritten once. The presence assertion is
      what makes it meaningful: without it a creation event one side ignored would leave that side
      empty from the start and the removal assertion would pass for the wrong reason.
      **It found a real hole on the way in:** `GroundTransferCancelled`'s branch in
      `GroundTransferCalendarProjector` had **no test at all** — deleting it left the entire suite
      green. Now caught.
      **Still true, and deliberately so:** adding a *fifth* removal event needs a new row in that
      test, and nothing forces it. That is the known cost of the scenario-test approach. The
      source-scan alternative (compare the two switches' matched event types, in the style of
      `PublicCalendarBuildsOnlyPublishableEntriesTest`) stays available if this ever proves too easy
      to forget.
- [x] **No test covered the `PublicCalendarProjector` bean's registration.** From the 2026-08-21
      review; **done the same day**. Every test that renders `/calendar` supplies it as a
      `@MockitoBean`, so reducing `bootstrapper.register(...)` to a bare `new PublicCalendarProjector()`
      would have left the projector neither subscribed nor replayed — **a permanently empty calendar
      for every anonymous visitor**, with the whole suite green.
      Fixed generally rather than for the one bean, as noted: `EveryProjectorBeanIsRegisteredTest`
      asserts that **every `@Bean` returning an `EventStreamConsumer`** calls
      `bootstrapper.register(...)` — 23 beans today. The set is derived by **reflection** over
      `EventSourcingConfig`, so a new projector bean is covered the day it is written and there is no
      fixture to forget; only "does it call register" is answered by reading source, that call being
      the whole of what there is to check. A second test pins the guard against its own rot (the
      config class moving, or the `bootstrapper` parameter being renamed), since either would
      silently reduce it to checking nothing.
- [ ] **A gap *into* home is dated by the wrong end — the mirror image of D14.** Lifted from
      `archived/ScheduleProblemsRewritePlan.md` (its whole "Open" section) when that plan was
      archived 2026-08-21. D14 fixed one direction: a gap *out of* home is now dated by the day
      Ted must be away, not by the landing. The other direction was never done — a gap *into* home
      still spans every day from the last away fact to the next home departure, so it reads as far
      longer than it is. **Why this is not a symmetric edit:** collapsing it moves
      `relevantUntil()` earlier, which changes when the problem drops out of the FUTURE filter, so
      it needs a decision about what the gap's *end* means before any code moves. Read D13 and D14
      in the archived plan first — they set the vocabulary this has to fit.
- [ ] **Change Private Event (the edit flow) — now owned by `ChangePrivateEventPlan.md`
      (2026-08-24), not by this list.** It outgrew a cleanup line: the plan puts **Cancel** in
      front of the edit as slice 1, because a private event has no booking (so the common
      correction is removing a wrong entry) and a wrong one is a false presence fact in
      `ScheduleGapProjector`. **Slice 1 shipped 2026-08-24** — cancel is live, linked from the
      **itinerary**, OWNER-only; the edit flow itself (slice 2) is what is still open here, and it
      brings the pencil *and* the calendar's bin with it. Read the plan, not this bullet. Lifted
      here from
      `archived/PrivateSocialEventPlan.md` 2026-08-21; the third item on that list, the itinerary
      entry, **shipped** — `PrivateEventItineraryEntry` is live.
- [ ] **Extract the venue-event request seam — BLOCKED until Change Private Event slice 2 ships**,
      which writes the fourth copy that makes it worth doing. Full costs-and-gains in
      `ChangePrivateEventPlan.md` **A4** (revised 2026-08-24); the short version:
      - **What duplicates:** a one-line `getLocation()` in `PlanGatheringRequest`,
        `ChangeGatheringRequest` and `PlanPrivateEventRequest` (four after slice 2), and the
        handler preamble `getLocation()` → `venueZone.resolve(getZone(), location)` → two
        `ZonedTimestamp.fromLocal(getDate().atTime(…), zone)` across the matching handlers.
      - **Extract (C), not the bare interface (B).** A `VenueEventRequest` on its own is a type with
        no client — abstraction in anticipation. Extract a helper that takes one and returns
        `(location, startsAt, endsAt)`, extending the `VenueZone` seam by one step; the interface
        then earns its keep as that helper's parameter type, which is how `HotelStayRequest` earns
        its own.
      - **The four handlers cannot merge into one.** Unlike `HotelHandler` (book+change of one
        kind), these span two kinds whose commands differ in type, id type and trailing fields.
        Only the inputs are shareable.
      - **`PlanGroundTransferHandler` stays out** — same date+times shape, but its zone comes from
        two endpoints rather than one address.
      - While you are there: `PlanPrivateEventRequest`'s Javadoc still says the shared interface is
        "deferred pending Ted's call". It is not; point it at A4.
- [x] **`/planned-private-events` list view — SHIPPED 2026-08-24**, and it outgrew this list on
      the way: it is now owned by `PlannedPrivateEventsListPlan.md`, not by this bullet. Read the
      plan, not this line. The reason it was worth more than "one more list": a private event's
      `street`, `region` and `postalCode` were carried by `PrivateEventPlanned` and read by **no
      view at all**, so the address Ted typed into the plan form went to the log and never came
      back. Lifted here from `archived/PrivateSocialEventPlan.md` 2026-08-21.
- [x] **The private-event nav card's placeholder icon — SETTLED 2026-08-24.** Ted's answer ("🍴")
      picked FA Pro `utensils`, which is the SVG the `/plan-private-event` card was already
      carrying — the placeholder turned out to be the right icon. The new `/planned-private-events`
      card uses the same one. From `archived/PrivateSocialEventPlan.md`.
- [ ] **Full travel calendar in the subscription feed (Phase 2).** Lifted from
      `archived/CalendarSubscriptionFeedPlan.md` 2026-08-21, which shipped Phase 1
      (cancel-deadline reminders) and named this as "left open (not built now)": flights, trains,
      hotel stays, gatherings and conferences served from the same feed. The assembler seam is
      already there — today it is just "the assembler returns a `List<ICalEvent>`", and per
      "no abstraction before the second user" the `ICalEventSource` interface waits for this, the
      actual second contributor. **Decide feed shape with it**, also deferred to this work: one
      feed with everything, or scoped feeds (`…/deadlines.ics` vs `…/all.ics`). Watch the token:
      the feed URL is the only credential, so widening what it serves widens what one leaked URL
      exposes.
- [ ] **One open question on the problem calendar**, lifted from `archived/ProblemCalendarPlan.md`
      2026-08-21 (slices 1–5 all shipped):
      - Should a **day number link to `/itinerary?date=`**? It is a fix link, so it belongs with the
        slice-5 vocabulary — but as its own step: the day cell is not interactive today, so this
        changes the grid rather than a band.

      Answered 2026-08-21: **the calendar is now the default view**, `ProblemView.fromParam`
      falling back to `CALENDAR` for an absent or unrecognized `?view=`. The list held the default
      only to keep pre-calendar links showing the page they always had, which is a migration
      concern spent once; with the same day's colour and affordance fixes the calendar is the view
      that answers when a problem is wrong and how it sits against the trip. The list is one click
      away on the toggle.
- [ ] **Conferences have no `locationForMatching` — but ask whether that is still a problem
      (Ted, 2026-08-20).** `PlanConferenceRequest:130` always passes `null` for the venue
      `Address`, so the compact constructor falls back to the city and a conference can only ever
      match on that. A conference in **Lone Tree, CO** therefore never matches the **Denver** that
      `StaticAirportCityResolver` gives for a `DEN` flight, and `/schedule-problems` reported
      missing travel between them (found 2026-08-20). Hotels, gatherings and private events all
      expose the field on their forms; conferences do not, and there is no Change Conference flow
      to correct it after the fact.

      **Ground transfer may have removed the reason to build this.** The motivating case is now
      answered honestly: there *is* a journey from DEN to a Lone Tree venue, and
      `archived/GroundTransferPlan.md` lets Ted record it, which closes the gap. Setting the conference's
      `locationForMatching` to "Denver" would instead have **hidden a real hop** — the field
      silences the gap rather than answering it, and a silenced gap is indistinguishable from one
      that was never there. That cuts both ways: the same objection applies to using
      `locationForMatching` on a *hotel* to paper over an airport-to-suburb drive.

      What might still justify it: two nearby addresses entered under different city names with
      **no journey between them at all** — a venue and its hotel in one complex, straddling a
      municipal boundary. That is narrow, and it is not the case that raised this item.

      **So decide before building.** If it goes ahead, the shape is unchanged: add the input to the
      conference venue address (the `fragments/address-paste.html` pattern) and pass it through. If
      it does not, delete this item and say why in `Backlog.md`, so the Lone Tree case does not
      re-raise it in six months. See the item below: the same reasoning may retire the field from
      hotels too, in which case adding it to conferences would be building the thing we are removing.
- [ ] **`locationForMatching` may be droppable from hotels — and from the forms generally
      (Ted, 2026-08-20).** Raised alongside the conference item above, and the evidence is stronger
      than expected. Four facts, all checked 2026-08-20:

      1. **`ScheduleGapProjector` is its only reader.** Nothing else in `src/main` consults it; the
         controllers merely pass it through. It exists to feed one matcher.
      2. **The geocoder writes it equal to the city.** `AddressParseService:89-95` returns
         `coalesce(locality)` for *both* `city` and `locationForMatching`, so every address filled
         by the paste-and-parse widget already has the two identical.
      3. **`Address`'s compact constructor falls back to `city` when it is blank.** So an absent
         value and a geocoded value produce the same match.
      4. Therefore it only ever *does* anything when **hand-edited** — and the hand-edit is exactly
         the widen-a-suburb-to-its-metro move (Lone Tree → Denver) that ground transfer now
         answers honestly, and that hides a real journey when used.

      **What would still be lost:** normalizing two spellings of one place — "Frankfurt" vs
      "Frankfurt am Main" — which is a different job from widening, and a legitimate one. The paste
      widget already handles it by giving both addresses the same locality; only a **hand-typed**
      address can still disagree with itself. Judge whether that is worth a field.

      **Two very different scopes, and only the first is cheap:**

      - **Stop offering the input** on the five forms that expose it (`book-hotel`, `change-hotel`,
        `plan-gathering`, `change-gathering`, `plan-private-event`) and stop the fragment filling
        it. No event-schema change at all: the field stays in the payload and simply always equals
        the city. **Reversible** — put the input back and it works again.
      - **Remove the field from `Address`.** An event-schema change (R6): an upcaster, every
        golden sample, and **backup-file compatibility** — every exported backup carries it, so
        this is the kind of change to warn about before making. The field costs nothing at rest,
        so there is little to gain and a compatibility commitment to lose.

      Recommendation: do the first, live with it, and treat the second as probably-never.
- [ ] **Itinerary: add-entry day dropdown** (like the calendar's). The `/calendar` future-day
      disclosure menu lets the owner add an entry for a specific day; the itinerary has no such
      affordance. Add the same per-day "add an entry" dropdown to the itinerary so a day can be
      populated directly from that surface (OWNER-only; reuse the `DAY_MENU` pattern).
- [ ] **Action affordances that still move (general rule: they must not).** The standing rule and
      its state-vs-authorization split are in CLAUDE.md.
      **`/conferences` no longer follows it, deliberately (Ted, 2026-08-22).** The two-slot fix of
      2026-08-19 — greyed, non-interactive `Confirm` text so Decline could not move — was removed
      with slice 4 of the conference plan, which made the row's actions a *state machine*: what a
      conference offers depends on where its talk stands, and most of those moves are not
      unavailable-for-now but meaningless (`Accepted` on a conference nothing was submitted to
      names an event that could never be true). Carrying nine greyed labels on every row to hold
      positions fixed would say less, not more. That is the nuance Ted agreed to: **the state
      machine wins where the two rules disagree**, and the disable-don't-hide rule keeps its force
      wherever an action is genuinely the same action, merely not available yet. Two other places
      still have the plain moving defect and were left alone:
      - `PlannedGatheringsRenderer.actionsCell` (`PlannedGatheringsRenderer.java:157`) stacks an
        optional `Event page →` above an always-present `Edit` in a column flex
        (`.gathering-actions`, CSS at `:57`), so **Edit sits on the first line on gatherings with no
        info URL and the second line on those with one** — the link's vertical position changes row
        to row. Needs a reserved slot rather than the conferences fix (the shift is vertical, and
        this list stacks on narrow viewports).
      - `ItineraryRenderer` train card (`ItineraryRenderer.java:163`) puts the OWNER edit pencil
        after an optional service-ID span, so the **pencil slides to the start of the line on trains
        with no service id**. The neighbour is text rather than an action, but the pencil is the
        thing being aimed at.
- [ ] **Surface "restart needed" after a truncate, next to the read-only banner.** `PostgresPersister
      .truncateAllTables()` (via `/admin/database/truncate`, `AdminController.java:128`) empties the
      tables, but `EventStore`'s in-memory list and every projector keep the old data — the app goes
      on serving read models for events that no longer exist, and only a restart clears it. That is
      the known stale-after-truncate bug: the live `reset()`/`rebuildFromPersistence()` rebuild was
      built and then **reverted** for the email-sender hazard (`archived/EventOrientedBackupRestorePlan.md`),
      so a restart is the fix and the app should say so. It bites Ted's standard wipe-then-import
      workflow every time. Detection looks cheap and derivable: the persisted event count (or max
      sequence) being **lower** than what `EventStore` holds in memory can only mean the tables were
      emptied underneath it. Render it like the existing read-only banner (`index.html:312`,
      `role="alert"`, model attribute from `GeneralController:62`) rather than as a post-deploy task
      — it says the data on screen is wrong *now*. Split out of `PostDeployTaskBannerPlan.md`
      (decision 4, 2026-08-19), which deliberately excludes it.
- [ ] **Extract a shared admin nav bar.** Every admin page hand-rolls its own: `admin-tasks` uses
      one shape (as did `admin-migrate-conferences`, retired in `4b9d9d4`), `admin-eventlog` another
      (`<nav><h3><a href="/admin">Admin</a> · <a href="/">JitterTravel</a></h3></nav>`), and
      `migrate-legacy-events` / `database` / `zone-audit` a third — each with its own CSS, and each
      needing the same edit when a link changes (as on 2026-08-19, when four pages had to gain a
      home link one at a time). Three pages — `admin-calendar-feed`, `admin-restore`,
      `admin-restore-success` — still have **no nav at all** and are dead ends. Extract one fragment
      (a Thymeleaf `th:replace` fragment, since these are all Thymeleaf pages) taking the current
      page's label, and apply it everywhere including the three with none. Compare
      `Page.viewNav(NavAudience, activePath)`, which already does exactly this job for the eight
      j2html view pages.
- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] **Read-only mode redirect is untested on the conference action controllers.** Both
      `ConfirmConferenceAttendanceController` and `DeclineConferenceController` catch
      `ReadOnlyModeException` and return `redirect:/read-only`, and neither slice test exercises
      that branch — the `catch` could be deleted and both suites stay green. Add a case to each
      (`willThrow(new ReadOnlyModeException(...))` on the application service, assert the redirect).
      Noticed 2026-08-19 reviewing the commitment slice; the same gap predates it on the decline
      side. Worth checking whether the other write controllers have the same hole.
- [ ] **A malformed conference id on the GET of `/conferences/{id}/confirm` (and `/decline`) is
      untested.** `lookup(...)` catches the `IllegalArgumentException` from `UUID.fromString` and
      redirects to `/conferences`; only the POST path has a `malformedConferenceIdRedirects...`
      test, so the GET-side catch is unpinned.
- [ ] Add event-type filtering to `/admin/eventlog` (the command-log filter is already done).
- [ ] `/admin/commandlog`'s "Out of order" badge only detects divergence *within* a page.
      `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` to `Long.MIN_VALUE` on every
      call (`PostgresPersister.java:291`), so a command whose event sequence numbers interleave
      with those of a command on the *previous* page is silently unflagged — the first entry of
      any page can never be marked. Fix means seeding `runningMaxSeq` from the max event
      sequence of all commands before the page's window rather than starting fresh. Pre-existing
      behaviour, untouched by the newest-first paging fix (`PageWindow`), which only changed
      *which* window is fetched, not how it's scanned.
- [ ] **Retire the `setState` shims on `BookHotelRequest` / `ChangeHotelRequest`.** Lifted from
      `archived/CuratedResolversToDomainPlan.md` 2026-08-23, which named it as the one thing left
      open after the `Address` alias retirement. Both are one line —
      `public void setState(String state) { this.region = state; }`, commented "backward compat for
      old exports" (`BookHotelRequest.java:45`, `ChangeHotelRequest.java:46`) — and both exist for
      the **command-export** format, which today's `BackupService` cannot read at all. The same
      measurement that retired `Address`'s `@JsonAlias("state")` applies one layer up: no restorable
      artifact carries the old spelling, and nothing in `src/main`, the templates, or the tests
      binds `state` on either request. Delete both setters and the `region` field is the only
      spelling left. **Not to be confused with** `AddressParseService.java:85`, which reads `"state"`
      out of a *geocoder* response — that is an external wire format we do not control and it stays.
- [ ] **`LocationAuditProjector` never sees a private event.** It handles `GatheringPlanned` and
      `GatheringChanged` — records of exactly the same shape — but not `PrivateEventPlanned`, so a
      private event's city and country never appear on `/admin/zone-audit`. Noticed 2026-08-30 while
      chasing the trailing-space bug above. **Probably harmless today**, which is why this is a
      cleanup and not a fix: a private event's zone is resolved at plan time and stored on its
      `ZonedTimestamp`s, and it has no legacy payload shape needing read-time resolution, so the
      audit has nothing to warn about. It is an inconsistency waiting for the day one of those
      stops being true; add the branch if you are in that file anyway.
- [ ] **`PlanGroundTransferHandler` compares two addresses with `equals`.** `:30` rejects a transfer
      whose origin equals its destination by comparing whole `Address` records, so the record's
      generated (case-sensitive) `equals` decides — "Hamburg" and "hamburg" are two different
      places to it, and the guard lets that transfer through. Since 2026-08-30 both sides are at
      least trimmed. The comparison every other call site uses is `Place.matches`; this is the one
      that does not. Small, and nothing has hit it — the endpoints are usually picked, not typed.
- [ ] **The booked-flight change history displays the store's envelope timestamp, at UTC, unlabelled.**
      Noticed 2026-08-27 while reading the history rendering; deferred by Ted the same day. Two
      faults in `BookedFlightsProjector`, both in the string the row shows:
      `bookingEntry`/`changeEntry` (`:85`, `:90`) format `storedEvent.timestamp()` — the **event
      store's envelope** — into `ChangeEntry.displayText` ("Booked on 2026-05-20 12:22PM"), which is
      exactly what **R11** in `EventSourcingRulesHeuristics.md` forbids: a displayed time is a
      payload field, never the envelope. And `toLocal` (`:99`) converts at `ZoneOffset.UTC`, so the
      reader gets a UTC clock reading with **no zone label** — every other time on that page goes
      through `ZonedTimeTag` and carries its zone.
      The renderer is not the problem: `BookedFlightsRenderer.renderFlightCard` (`:153`) just prints
      `entry.displayText()` into the `<details>` list, so the whole fix is in the projector plus
      whatever payload field ends up carrying the occurrence time.
      **Check `EventOccurrenceTimestampsPlan.md` before starting.** If that plan puts an occurrence
      timestamp on the payloads, this is one of its consumers and fixing it standalone means doing
      the work twice.

## Deferred (until needed)

Items with a known shape and a named trigger, deliberately **not** queued: the cost of carrying
them is a paragraph, and building either one now would be work ahead of a need. Move an item up to
**Open** when its trigger fires — do not treat this section as a backlog to work down.

- [ ] **The near-term empty lane band, 120px → 80px.** Lifted from `archived/QuietWeekRunsPlan.md`
      2026-08-31. Ted proposed it there and it was neither taken nor refused; the plan it belonged to
      was then reverted, so this is now a standalone question about the linear calendar's density.
      Against it: that window is where Ted still plans by tapping a day, and
      `--calendar-empty-band-min-height` exists so an empty week reads as open space rather than a
      thin strip of dates.
      **Trigger:** the linear calendar feeling too airy once the year overview is carrying the
      "sense of things" job. One token in `CalendarRenderer`.

- [ ] **No way to change a conference.** Lifted from `archived/ConferenceSubmissionTrackingPlan.md`
      2026-08-23, when that plan was archived — it names this gap and nothing else tracked it.
      There is no `ChangeConferenceController` to match `ChangeGathering`, so a conference's name,
      dates, venue and **`infoUrl`** are set once at plan time and cannot be corrected. Known and
      accepted when `infoUrl` was placed on `ConferencePlanned` (slice 4b, 2026-08-22): the CFP half
      has a repair path (`/conferences/{id}/cfp`, which re-records and replaces), and the conference
      half has none. Related: the `locationForMatching` item above says the same thing from the
      other end — a conference cannot be corrected after the fact either way.
      **Trigger:** Ted needing to fix a conference he has already entered — a moved venue, a
      corrected date, or an `infoUrl` he did not have when he planned it. Cancel-and-re-enter is not
      the workaround it is for a ground transfer, because a conference carries a CFP, a talk
      pipeline and a commitment that would all have to be re-recorded.
- [ ] **The hotel zone divergence on the transfer submit path.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23, which named it and deliberately
      left it alone. `GroundTransferEndpointResolver.hotelEndpoint` calls
      `locationZones.resolve(hotel.address())` at submit time although `HotelBooked.checkIn()`
      already carries the zone resolved at booking: two sources for one fact, and the **only** reason
      `ZoneResolutionException` is reachable on this path at all. Slice 3 showed what the fixed shape
      looks like — the `train:` branch reads the zone off the trip's own `ZonedTimestamp` and cannot
      fail — so this is now the odd one out rather than the norm. Doing it means (a) an audit of the
      kind `LocationZoneAudit` already models, asserting the two agree for every hotel in the log,
      then (b) deciding whether `PlanGroundTransferController`'s now-unreachable `catch` and its "fix
      the hotel's address first" copy get deleted. **Trigger:** a transfer whose hotel end is stamped
      in a zone that disagrees with the stay beside it, or the next time that `catch` has to be
      reasoned about. Related: `HotelDetailsView` drops the zone the same way `TrainDetailsView` used
      to, which is the actual mechanism.
- [ ] **Conference and gathering venues as ground-transfer endpoints.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23. `GroundTransferEndpointChoices`
      documented the hole as two things — "a train station, a conference venue" — and slice 3 closed
      the first half only. Same shape as the train work: a `TransferEnd` pair, arms in
      `TransferEndpointProjector`, a `venue:` branch in the resolver, optgroups. **Do the record
      reshape first** (below) if this one is taken up. **Trigger:** a missing-travel gap that starts
      or ends at a venue Ted has to be driven to. A venue name is *public* where a hotel's and a
      station's are private, so the redaction question is the opposite one and worth asking before
      building.
- [ ] **Reshape `GroundTransferEndpointChoices` around `TransferEnd`.** Noticed 2026-08-23 while
      shipping slice 3: the record is six positional `List`s, and adding trains meant editing its
      construction in five test files. A `nothing()` factory and a test-local helper absorbed that
      round, but a third kind would do it again. The only thing holding the current shape is that
      `plan-ground-transfer.html` reads the lists by name (`endpointChoices.trainArrivals`), so the
      reshape is a template change too. **Trigger:** the venue item above, or any third endpoint
      kind — not worth doing for its own sake at two kinds.
- [ ] **Carry `Place` through `ScheduleProblem` and the renderers.** Lifted from
      `archived/GroundTransferEndpointReadModelPlan.md` 2026-08-23 (its D2). `MissingTravel.fromCity()`
      / `toCity()` and `ScheduleTimeline.Movement`/`Stay`/`Occupancy` still hold plain strings, so
      `GroundTransferEndpointChoices` re-wraps them (`new Place(option.city())`) to compare. D2's
      reasoning still holds and is why this is deferred rather than open: the thing that had to agree
      is *which field of which event becomes the place*, and that is now written once, so threading
      the type deeper touches `ProblemRef`, `ProblemBand`, `ScheduleProblemsRenderer`,
      `ProblemCalendarViewBuilder` and the itinerary for no additional guarantee. **Trigger:** a
      second comparison that has to case-fold by hand, or a bug traced to one of those strings being
      compared with `equals`.
- [ ] **Ground-transfer endpoint prefill from a fix link.** Lifted from
      `archived/ProblemContextOnFixPagesPlan.md` 2026-08-23, when that doc was archived — it was
      the one piece of future work that doc still named, and nothing else tracked it. Today
      `/plan-ground-transfer` receives only `?date=` from a fix link, because the gap knows
      **cities** while the form takes **endpoint tokens** (`airport:DEN`, `hotel:<bookingId>`), and
      one city maps to zero, one or many of them. **Why this is not a wasted click if it guesses
      wrong:** preselecting the wrong endpoint writes a `GroundTransferPlanned` event that *removes
      the very gap it was entered to close* — the failure hides itself. The safe shape is named in
      both docs: preselect only on an unambiguous single match, group the candidates when there are
      several, say so when there are none. Reasoning is D13 in `archived/GroundTransferPlan.md`.
      **Trigger:** Ted following a travel-gap fix link to `/plan-ground-transfer` often enough that
      re-picking both ends annoys — most likely alongside the Change-a-ground-transfer item above.
- [ ] **Private events in `DifferentCityConflict`** — tabled by Ted 2026-08-20, and it outlived the
      slice it was pencilled into: it was to ride along with problem-calendar slice 4, but slice 4
      shipped 2026-08-20 as clash *markers* only, so this now has no home but this list.
      **What is wrong:** detection is already indifferent to what kind of thing a conflicting entry
      is, but the *clearing* event types its subject as a `GatheringId`, so a private event in the
      wrong city cannot be reported (it would be unclearable, and an unclearable row is worse than
      no row). **The work is additive:** a `PrivateEventCityConflictCleared` event, or a one-of
      subject on the existing one, plus the detector branch — no schema change to anything stored.
      **Trigger:** Ted plans a private event on a day a conference runs elsewhere and wants the
      clash surfaced. Until then `/schedule-problems` is quietly incomplete in one direction only,
      which is the safe direction — it under-reports rather than reporting something he cannot act
      on. See `archived/ScheduleProblemsRewritePlan.md` and `archived/PrivateSocialEventPlan.md`.
- [ ] **No logout affordance, and `GET /logout` is a 404** — **deferred by Ted 2026-08-21: incognito
      is sufficient.** Not a bug to chase; recorded so the next person to notice it stops here
      instead of re-deriving it.
      **What is true today** (probed 2026-08-21, not inferred): `POST /logout` works and redirects
      to `/` — the `.logout(logout -> logout.logoutSuccessUrl("/"))` config has been correct since
      `e6f4b33`. `GET /logout` returns **404**, and no page anywhere links to logout.
      **Why it went:** `.formLogin(form -> form.loginPage("/login"))` arrived in `0435623` with the
      custom login page. A custom login page makes Spring Security drop
      `DefaultLoginPageGeneratingFilter`, and the *same* configurer registers
      `DefaultLogoutPageGeneratingFilter` — which is what used to serve `GET /logout` as a generated
      "are you sure?" form that POSTed back with the CSRF token. With CSRF on, `LogoutFilter` matches
      **POST only**, so losing the generated page left `GET /logout` unmapped. Nothing was
      misconfigured; the way to *reach* logout was collateral.
      **A real inconsistency rides along:** `login.html:78` renders a `th:if="${param.logout}"`
      notice and `LoginControllerTest:49` pins that `/login?logout` shows it — but `logoutSuccessUrl`
      sends a successful logout to `/`, so in production that notice is **unreachable**. The test
      passes only because it requests the URL directly. The two disagree; whichever is wrong, fixing
      one without the other keeps them disagreeing.
      **Do not suggest driving `POST /logout` from the console as a workaround:** the CSRF cookie is
      deliberately `httpOnly(true)`, so page scripts cannot read a token to submit and `CsrfFilter`
      rejects it. That is the cookie working as designed.
      **The work, if it ever lands:** a POST form (Thymeleaf, for CSRF — j2html renderers stay
      uncoupled from Spring MVC's CSRF per the standing split), rendered only for authenticated
      viewers and **nothing at all** for anonymous ones per the affordances rule, plus pointing
      `logoutSuccessUrl` at `/login?logout` so the existing notice stops being dead code. Restoring
      `GET /logout` itself would mean writing a confirm page — the generated one is not coming back
      while the login page is custom.
      **Trigger:** a second person needs an account, or Ted wants to switch roles on a device where
      a private window is awkward (the iPad).
- [ ] **Change a ground transfer** — the other half of D11 in `archived/GroundTransferPlan.md`. Cancel
      shipped 2026-08-20 and took the urgency with it: correcting a transfer is now
      cancel-then-enter, two forms instead of one, and **nothing is lost in the round trip** —
      both ends are snapshots in the event by design, so there is no live reference an edit would
      preserve. **The work, if it ever lands:** a `GroundTransferChanged` full-snapshot event
      (mirroring `HotelChanged`/`TrainChanged`), a change command + handler reusing
      `GroundTransferEndpointResolver` wholesale, a form that is the plan form with its fields
      hydrated, and `put`-branches in the four projectors that already handle `Planned`.
      **Trigger:** Ted finds himself re-entering the same transfer often enough to notice — most
      likely if a mode/notes field ever arrives (D7), since that is the kind of detail you edit
      rather than re-type. Not before.

## Done

- [x] **A city typed with a trailing space was a different city** (2026-08-30). Ted planned a
      private event in Hamburg from an iPhone; the space bar that committed an autocorrect
      suggestion left `"Hamburg "` in `city`, `country` and `locationForMatching` (production event
      92). Nothing between the keyboard and the event log removes it — `type="text"` submits its
      value verbatim — and the schedule's comparison is exact apart from case, so
      `/schedule-problems` reported **"No travel — Hamburg → Hamburg"** plus a missing hotel for two
      nights the Hamburg booking already covered. HTML collapses the space, so both ends of the
      phantom gap rendered as the same word.
      **Fixed by normalizing where the string lands, not where it is typed:** `Address` and
      `TrainStationAddress` trim (and null-guard) every field in their compact constructors, and
      `Place` trims as the last net. Because Jackson binds stored payloads through those
      constructors, event 92 reads clean on every replay — **no migration, no data re-entry, and
      the stored JSON is untouched**, so backup files stay byte-compatible. The two comparisons
      that were exact-but-untrimmed were fixed too: `HomeCities.sameLocation` (whose neighbour
      `includes` had trimmed all along — that inconsistency *was* the bug) and the raw
      `equalsIgnoreCase` in `ScheduleGapProjector.differentCityConflicts`.
      Pinned by `AddressTest`, `TrainStationAddressTest`, additions to `PlaceTest` and
      `HomeCitiesTest`, a golden sample carrying the real dirty payload
      (`GoldenEventDeserializationTest.addressFieldsAreTrimmedWhenAStoredPayloadIsRead`), and the
      regression where it was visible — `ScheduleGapProjectorTest.LocationsTypedWithStrayWhitespace`.
      **What this does not catch:** `trim()` leaves U+00A0 non-breaking space alone, and nothing
      sees homoglyphs — the SoCraTes UK venue's street starts with a **Cyrillic М**
      (`"Мilton Hill, Steventon, "`, event 5). Streets are never matched on, so nothing depends on
      it; a normalizer for one event was not worth building.

- [x] **The same space, on every other field: trimmed at the boundary** (2026-08-30, same day).
      Ted's question after the fix above — what about a hotel name, a venue name, the fields nothing
      compares *yet*? An audit of all 19 form requests and every event's `String` components found
      no comparison outside the places already fixed (airport codes and zones fail loudly instead;
      every enum `fromParam` already trims), so the exposure was cosmetic — but "nothing compares
      it" is a property that changes silently, and bad text outlives the form in every backup.
      `TrimTypedTextAdvice` is a `@ControllerAdvice` registering `StringTrimmerEditor(false)`: every
      `String` bound from a form or query parameter is trimmed, on every controller, **including
      forms nobody has written yet** — which is the point, versus normalizing a dozen event records
      one field at a time. `false` keeps `""` as `""`, so the no-null-Strings rule is untouched.
      Pinned by `TrimmedTypedTextConventionTest` through two real controllers, one `@ModelAttribute`
      and one `@RequestParam`, because the advice is invisible at every call site — the arrangement
      `ProblemContextAdvice`/`ProblemContextFragmentConventionTest` already uses.
      **Two knock-ons, both agreed:** `" DELETE "` now opens the Danger Zone (the word proves intent,
      not typing precision — CLAUDE.md says so at the gate), and the three renderers that guarded
      optional text with `isEmpty()` now use `isBlank()`, so whitespace-only text reads as absent
      instead of rendering an empty chip or a link to `" "` (`BookedTrainsRenderer` ×2,
      `ScheduleProblemsRenderer`). Two others in that family — `ItineraryRenderer:302`,
      `ConferencesRenderer:561` — were fixed for free by the `Address` change above.
      **The division of labour this settles:** the boundary trims everything *typed*; a record
      normalizes everything *compared*. Both are needed, because a restore binds stored JSON through
      Jackson and never passes the boundary — which is exactly how event 92 gets repaired.

- [x] **Itinerary: where he is on an eventless day** (2026-08-21). A stay produces only Check-In
      and Check-Out entries, so every night in between rendered as "Nothing scheduled" — the
      itinerary went blank precisely on the days Ted is somewhere. A day with **no entries at
      all** now renders a `.whereabouts` row in that slot instead, on a tint far lighter than the
      lodging card's (`#f0fdf4` against `#dcfce7`) and with no left border, so it reads as context
      rather than as something scheduled. Two shapes:
      - **In a stay** — two lines, hotel icon: `In Frankfurt, DE` over `Grand Hotel Frankfurt`.
        (Shipped first as a one-liner joined with a `·`; it wrapped in a third-of-a-column, so
        Ted asked for the compact 2-liner. `OngoingStay.label()` became `locationLabel()` and the
        hotel name moved to its own line.)
      - **Away with no bed** — two lines and a fix link, in **amber**: `In Denver` over
        `No hotel booked`, then `Book hotel →`. Amber because a schedule problem to look at is
        work waiting and recoverable; green is for a night that is sorted. (`/schedule-problems`
        colours its own cards by problem *kind* — blue for hotel — but there every card is a
        problem, while here the row has to stand out from the settled days beside it.)
      - **At home** — one line, house icon in the same lodging green: `You’re Home`.

      Shape: `OngoingStay(hotelName, city, country)` view record;
      `ItineraryProjector.ongoingStayOn(date)` derived from the hotel entries already held (no new
      state — the check-in entry of every stay whose check-in day is *before* and check-out day
      *after* the date, earliest check-in winning if stays overlap);
      `ScheduleGapProjector.missingHotelOn(date)` and `.atHomeOn(date)` for the other two.
      `ItineraryDay` carries all three as separate fields, because two of them can hold at once (a
      hotel booked in a home city is both a stay and a night at home); the renderer picks, most
      specific first: **stay → no-bed → home → nothing scheduled**.

      **The no-bed row is answered from the `MissingHotel` read model, not from a raw location
      lookup.** That is what makes it say exactly what `/schedule-problems` says, with the same
      dates behind the same link: the fix links come from `ProblemFix.forProblem`, whose whole
      point is being the one mapping every view reads, so the itinerary is now its third reader
      (after the problems list and the problem calendar). Rendered as plain links rather than the
      report's disclosure menu — a missing hotel has exactly one fix, and a menu holding one item
      is a worse door — and **OWNER-only**, since `/book-hotel` is a form family could never
      submit. It also inherits the night sweep's two exclusions for free: a night at home and a
      night in transit demand no bed, so neither is ever mislabelled. The run is half-open,
      `[checkIn, checkOut)`: the checkout day is the morning he leaves.

      **It cost both problem views their one-item menus, and the calendar its blue.** Ted spotted
      that the "a menu holding one item is a worse door" argument condemned `Fix ▾` on the two
      single-answer cards, then stated the rule behind it — *a dropdown only above three choices,
      or where space is constrained; if unsure, ask* — and, reviewing the calendar, two more
      problems with it. All three are now in CLAUDE.md and shipped the same day:
      - `ScheduleProblemsRenderer.fixSlot` renders **up to three fixes as links**, above three a
        menu. That leaves one menu on the page: a hotel booked four ways.
      - **Calendar bands are all amber now.** Ted missed a run of missing hotels because they were
        blue while travel gaps were amber; every band shares `--pc-problem-bg` and keeps its kind
        only as the 4px left edge. This also retired the last red *fill* on that view, which the
        colour law reserves for the irreversible.
      - **Calendar bands say they are clickable.** One answer makes the whole band a link; several
        keep the menu (a week-grid cell is genuinely constrained); either way a `.pc-band-fix` chip
        names the action or announces the menu. The whole band being a silent click target was a
        hidden affordance — "knowing I have to click" is not one.

      F9 in `archived/ProblemCalendarPlan.md` is annotated as superseded for both views, with the
      colour reversal spelled out against F1. `ProblemFixMenuJsTest` now builds its fixtures from
      the two cases that still *have* menus — a four-way duplicate on the list, a travel gap's
      three answers on a band.

      Found by Ted's own worked scenario (2026-08-21): flight out Sep 21, ExploreDDD in Denver
      Sep 23–25, flight home Sep 28, **no hotel at all**. Before this third source, Sep 22, 26 and
      27 all read "Nothing scheduled" while `/calendar` striped them away and `/schedule-problems`
      was already demanding a bed in Denver for every one of those nights — three surfaces, and
      the itinerary the only one with nothing to say. That scenario is now a test.

      **The home claim needs positive evidence, and "not in `awayDays()`" is not evidence.** The
      away band is built from nights the walk fills *between* points, so a trip with no return
      booked yet — flown out, no hotel entered, nothing after it — bands **nothing**, and trusting
      the band alone would announce Ted is home while he is in Amsterdam (exactly what he asked
      us to prevent). So a fourth cached read model, `ScheduleTimeline.homeByLastFactOfDay()`,
      records whether each day's *last* fact left him in a home city, and `atHomeOn` requires
      **both** that and the absence of an away band. Consequences, all deliberate: no home cities
      configured ⇒ never home (rather than always); no facts at all, or a date before the first
      fact ⇒ never home; the travel-home day itself ⇒ not home, so the row and the calendar's band
      always agree; an old fact that placed him home keeps holding, since he is home by choice and
      no news is the normal state of being there.

      Scoped deliberately (Ted's call): **only fully empty days** — a mid-stay day that has a
      conference or gathering on it is left exactly as it was. Check-in and check-out days are
      excluded because their own entries already say where he is, and more precisely. Both booking
      intents count: the itinerary draws a tentative stay like a final one. No redaction impact —
      `/itinerary` is OWNER/FAMILY only, and none of this reaches `/calendar`. Mutation-verified
      thirteen ways across the three rounds (both stay date bounds, the stay row suppressed, the
      stay row forced onto a day with entries, the hotel name dropped, home winning over a stay,
      the away guard dropped, the home-evidence check dropped, the tint darkened, the two
      controller wirings cut, both no-bed range bounds shifted, the booking link shown to family).
      Caught twice in passing: a CSS comment quoting "Nothing scheduled", then a
      `doesNotContain("whereabouts-detail")` matching its own CSS rule — the bare-word assertion
      trap CLAUDE.md warns about, working as intended both times.
- [x] **Cancel ground transfer** (2026-08-20), the day after the slice it followed. D11 in
      `archived/GroundTransferPlan.md` shipped that slice without cancel, so a mistyped transfer could not be
      removed from inside the app: it stayed on the calendar and kept feeding a false presence fact
      into `/schedule-problems`, where it masked a real missing-travel gap. Built as the task
      described: a `GroundTransferCancelled` event (the id alone — no reason, since a transfer has
      no booking to explain away), command + context + application service folding existence from
      the event stream, `/ground-transfers/{id}/cancel` GET-confirm + POST with its own matcher and
      matrix row, and removal branches in the calendar, itinerary, gap and new details-view
      projectors — the gap the transfer was closing correctly returns, which
      `GroundTransferCancellationPropagationTest` pins. Amber, plain confirm, no typed word, as
      specified. Reachable from **both** the itinerary card and the calendar entry (Ted's call): a
      `.cancel-bin` in the edit pencil's slot, OWNER-only, via a new `CalendarEntry.cancelPath` that
      the redactor drops. Details in `archived/GroundTransferPlan.md` → "Cancel, as built".
- [x] **Retired `/admin/migrate-conferences`** (2026-08-19). The one-off conference→gathering
      migration had served its purpose — Ted ran it — so the whole write path went: both
      `AdminController` handlers, `ConferenceMigrationService`, the `MigrateConferenceToGathering`
      command record, `admin-migrate-conferences.html`, the admin-home card, the bean in
      `EventSourcingConfig`, and `ConferenceProjector.migratableViews()`, which existed only to feed
      that page. Two things deliberately stayed: the **events** it emitted (`GatheringPlanned` /
      `ConferenceCancelled`) are ordinary history and keep replaying, and the **command_log rows**
      still resolve — command payloads are stored and rendered as raw JSON
      (`PostgresPersister` reads `payloadJson` as a string), never deserialized back into the record
      class, so deleting the class cannot break the command log.

- [x] **Rename `ConfirmedCalendar*` → `Calendar*`** (2026-08-19). "Confirmed" distinguished nothing:
      the calendar is *the* calendar, the route has been `/calendar` all along, and the adjective
      survived only in class names. `ConfirmedCalendarRenderer` → `CalendarRenderer` (plus its single
      call site in `CalendarController`), and the three tests → `CalendarRendererTest`,
      `CalendarDayMenuJsTest`, `CalendarToggleJsTest`. Pure rename, 27 usages: **no** route, template,
      CSS class, event, or stored-data impact, and no name collision (`CalendarViewBuilder` is a
      different thing, and no `CalendarRenderer` existed). Docs naming the class swept — including a
      few code-fence references the IDE's rename doesn't reach. 962 unit + 36 js green, with both
      renamed js-tier tests discovered and running under their new names.
- [x] **Login "have to sign in twice"** (2026-08-18). The custom sign-in form's CSRF token was
      session-bound (default `HttpSessionCsrfTokenRepository`), so it died whenever the in-memory
      session did — every redeploy, every local devtools restart, every idle timeout. A login page
      rendered before that death then submitted a token with no session to match; the `CsrfFilter`
      rejected it and the custom `accessDeniedHandler` bounced it **silently to `/`**, looking
      exactly like "not logged in". The retry rendered a fresh `/login` with a matching token and
      worked — hence "log in twice, then it sticks". Two changes in `SecurityConfig`: (1) CSRF token
      now lives in an **HttpOnly cookie** (`CookieCsrfTokenRepository`), not the session, so it
      outlives restarts/timeouts and needs no server session — the first login after a restart
      validates (kept HttpOnly: the form gets its token server-side, so JS never reads it, no weaker
      than a session token against XSS); (2) a rejected CSRF token (`CsrfException`) now routes to
      `/login?expired` with a notice instead of silently to `/`, where an expired login is
      indistinguishable from never having signed in — authenticated-but-wrong-role denials still go
      to `/`. `login.html` shows the `?expired` notice. New mutation-verified test
      `SecurityAuthorizationTest.staleCsrfTokenReturnsToLoginWithExpiredNotice`; reproduced and
      verified end-to-end against the running app; full suite green (938 + 36 js). **Single-instance
      only fixes CSRF** — going multi-instance would also need the auth `HttpSession` externalized
      (Spring Session JDBC on the existing Postgres), **deferred until actually scaling**.
- [x] **Lateral nav across the read-only view pages** (2026-08-17). The old "nav" was the same
      two-link `JitterTravel · Calendar` breadcrumb on every page (and inconsistently built — the
      calendar's was an inline-styled indigo link, trains wrapped it in `<h3>`), so from any view
      you could only reach `/` and `/calendar` — every other view page was a lateral dead-end.
      Replaced it with a single shared `Page.viewNav(NavAudience, activePath)`: a flex-**wrapping**
      bar (no horizontal scroll) that links each view page to the others the viewer may reach, with
      the current page rendered as a non-link `<span class="active" aria-current="page">`. Applied
      to all eight j2html view renderers — `/itinerary`, `/calendar`, `/booked-flights`,
      `/booked-trains`, `/booked-hotels`, `/planned-gatherings`, `/tentative-conferences`,
      `/schedule-problems`. **Tier-gated (deny-by-default), matching `SecurityConfig`:** OWNER sees
      all eight; FAMILY sees only Itinerary + Calendar (the pages it can open); anonymous on the
      public calendar sees only the home link — a link to a page the viewer would 403 on is both a
      papercut and a hint the page exists, so it's never rendered. `NavAudience.of(isPublicUser,
      isOwner)` derives the tier from the flags controllers already hold. Base `.view-nav` styling
      in `site.css`; the calendar scopes a `4rem` horizontal margin so the bar aligns with its
      `.calendar-outer` body. New `PageTest` (link sets per tier, active-span, `NavAudience.of`) and
      two new `CalendarRedactionSecurityTest` cases (anonymous exposes no owner/family surface;
      owner links to the other views) — all mutation-verified (anonymous-leak, missing active-span,
      owner-missing-links). **The Schedule Problems nav link is unconditional (OWNER only).** A
      state-aware version was built and then **reverted on 2026-08-18**: making the link appear only
      when `ScheduleGapProjector.problems(now)` was non-empty meant threading a
      `hasScheduleProblems` flag through `Page.viewNav` and all eight view renderers *and* injecting
      `ScheduleGapProjector` into seven view controllers that otherwise have no interest in it. Ted's
      call: that's a large increase in coupling, test setup, and constructor noise for a small UX
      gain, so the bar now reflects only the viewer's tier and the report page renders its own empty
      state when the schedule is clean. The **home card** on `index.html` stays state-aware —
      `GeneralController` legitimately depends on the projector for its count (see the entry below).
      Full suite green at 936. **Deliberately scoped this session (Ted's
      call):** *view pages only* — the Thymeleaf **form** templates keep their existing breadcrumb
      (nav matters less there, and forms are reached from the lists / the calendar's add-entry
      dropdown), and **no footer** (the calendar dropdown covers adding entries). Admin pages left
      out too. This retires the "many pages are dead-ends" cleanup item as far as the view surfaces
      are concerned.
- [x] **Consistent edit affordances on calendar and itinerary entries** (2026-08-17). Every editable
      entry kind now exposes the same OWNER-only edit pencil from **both** surfaces. Before this,
      the calendar showed a pencil only for flights and trains (`editPath` set), and the itinerary
      showed one for flights/trains/hotels but not gatherings. Filled the two gaps: `HotelCalendarProjector`
      and `GatheringCalendarProjector` now set `editPath` (`/booked-hotels/{id}`,
      `/planned-gatherings/{id}`), so the calendar renders the pencil for hotels and gatherings too;
      `ItineraryRenderer.renderGathering` now takes `isOwner` and appends the pencil (needed a new
      `gatheringId` field on `GatheringItineraryEntry`, threaded from `ItineraryProjector`). The
      pencil is OWNER-only on both surfaces — the calendar redactor drops `editPath` and the
      renderers gate on `isOwner`; a new `CalendarRedactionSecurityTest` case plus the strengthened
      lodging redactor test prove the hotel deep link never reaches anonymous eyes (full-stack
      mutation-verified). **Conference and private event are intentionally excluded** — they have no
      edit flow yet (`ChangeConference` / `ChangePrivateEvent` are separate unbuilt features), so
      there is nothing to link to; when each edit page ships it should set `editPath` / take `isOwner`
      the same way and inherit the affordance. Projector + renderer + both-tier redaction tests, all
      mutation-verified.
- [x] **Split every page combining an Edit and a Cancel form** (2026-08-17). No page hosts a
      second cancel *form* — the only entry cancel form, `cancel-hotel.html`, has been its own page
      since `f5971ef`, and every other edit page hosts a single edit form (`change-flight.html`'s
      second form is the flight-number **lookup**, not a cancel; `change-train.html` /
      `change-gathering.html` have one form each; there is no `change-private-event` page). The
      other entry kinds (flight, train, gathering, conference, private event) have **no cancel
      action at all** yet — separate, still-open features (`ConferenceCancelled` organizer cancel;
      gathering cancellation, out of scope in `archived/ChangeGatheringPlan.md`) — and when each ships it
      must land on its own page from the start, per the "errors render on the form page" convention.
      **Correction (same day):** the first pass called this done on the form-vs-link technicality
      and left the "Cancel this booking" **section** (a `.danger-zone` heading + hint + link to the
      cancel page) sitting on the `change-hotel` edit page — which still reads as edit-and-cancel
      combined. Removed that whole section (and its now-dead `.danger-zone`/`.danger-link` CSS), so
      the change page is genuinely edit-only; cancel is reached from the per-row **Cancel** link on
      `/booked-hotels` (which already exists next to Edit). `ChangeHotelControllerTest`'s GET render
      test now asserts the page `doesNotContain` "Cancel this booking" or "/cancel" (mutation-verified),
      and the explanatory note on the template is a Thymeleaf parser comment so it isn't rendered.
- [x] **Calendar day-number popup now dismisses** (2026-08-17). The owner future-day disclosure
      menu on `/calendar` is a native `<details class="day-menu">`, which on its own never
      dismisses — clicking away left it open, Escape did nothing, and opening a second day left the
      first open so the absolutely-positioned menus stacked and overlapped. Added `DAY_MENU_SCRIPT`
      in `CalendarRenderer` giving the three behaviors a popup is expected to have: only
      one open at a time (a `toggle` listener closes the others when one opens), close on
      outside-click (document `click` where the target isn't inside a `.day-menu`), and close on
      Escape (document `keydown`). Harmless when no day menus are present (owner-only render).
      Covered by `CalendarDayMenuJsTest` (`@Tag("js")`, `page.setContent`, no server) —
      three cases (outside-click, Escape, no-stacking), each mutation-verified. **Note:** while
      doing this I found `CalendarToggleJsTest` was **pre-existing broken** (failed without
      any of my changes) — the 2026-08-16 "default `from` = one week before today" change
      (`0435623`) shrank the rendered range so the tests' expected collapsed-week counts no longer
      held; the `js` tier is opt-in (excluded from the default build), so it shipped invisibly.
      **Fixed 2026-08-17** (green, 5/5), and the native pre-push MUST-PASS gate now runs the `js`
      tier too so a broken js test can no longer ship unseen.
- [x] **Add a private social event type** (2026-08-13). Shipped as its own entry kind — see
      `archived/PrivateSocialEventPlan.md` (done) and the Backlog row. `EntryKind.PRIVATE_EVENT` with a
      `PlanPrivateEvent` command / `PrivateEventPlanned` event / context, `PrivateEventCalendarProjector`,
      a plan form + controller + nav card, and a `PrivateEventItineraryEntry` on `/itinerary`. The
      redactor gets its own `PRIVATE_EVENT` branch: an anonymous viewer sees `Busy`, a zone-labelled
      time range, and city+country — never the title — via the plain-text `SubtitleLine.FixedRange`
      (no `<time datetime>` leak). Both tiers of redaction test plus command/projector/controller/golden.
      Retires the "private dinner can only be a public GATHERING" leak. Follow-ups still open (edit
      flow, `/planned-private-events` list) live in the plan doc.
- [x] **Eager-migrate legacy events + per-event schema-version stamp** (2026-08-16). Owning doc
      `archived/LegacyEventEagerMigrationPlan.md` (now `built`). Added an `event_log.schema_version` column
      (nullable; per-type version in `EventTypes`, the nine `ZonedTimestamp` types = 2, others = 1);
      the append path stamps new rows, restore carries it verbatim, backup format bumped to **v3**
      (restores v2 and v3, so old backups aren't orphaned). `LegacyEventMigration` +
      `PostgresPersister.migrateEventPayloads` rewrite each stale row's payload and stamp in one
      transaction (identity columns untouched); idempotent (already-current rows skipped),
      validate-then-apply (one unbindable/unresolvable row aborts with zero writes), read-only-guarded.
      OWNER-only `/admin/migrate-legacy-events` (GET preview + POST run) with an `AuthorizationMatrixTest`
      row and an admin nav card. Decided with Ted: **column not payload-key**, accept the backup bump,
      in-place admin UPDATE, FQCN→logical `type` normalization **deferred** (that pass is now **built**,
      2026-08-19 — see `archived/EventTypeColumnNormalizationPlan.md`; the same `UPDATE` now writes `type`),
      versioning *framework* deferred (stamp only for now). Every new/changed test mutation-verified. Retirements still gated
      on old backups leaving rotation: the upcaster's legacy timezone rungs (the `*TimeZoneUpcaster`
      classes — see `EventPayloadUpcasterDesign.md`), the FQCN mapping, and the Antwerp-style resolver
      hacks.
- [x] **Boot-replay preflight (pre-deploy)** (2026-08-16). `BootReplayPreflightTest`, a
      `@Tag("replay-preflight")` tier excluded from the default build (`mvn test -Preplay-preflight
      -Dpreflight.dump=/path/to/backup.json`; no dump ⇒ skips). Restores a production backup into a
      scratch Testcontainer DB — whose validate pass runs the exact `upcast → classFor → bind` boot
      uses, incl. zone resolution — then drives `loadAllEvents()` over the loaded rows, failing with
      the offending row named on anything that would abort boot. Verified end-to-end against a clean
      dump (passes) and a bad one (fails, naming the unresolvable row) — the 2026-08-16 Morocco/Antwerp
      failure mode. This is the tool that certifies each retirement the eager migration unlocks;
      `/admin/zone-audit` is not a substitute (runtime-only, went stale).
- [x] **Startup-failure (read-only) warning banner on the home page** (2026-08-16). When
      `EventStore.isReadOnly()` is true — a failed boot replay or a failed save flipped the app to
      read-only — the home page renders a prominent red `role="alert"` banner across the top
      ("Read-only mode — a startup or save error occurred, so changes are disabled and some data may
      be missing"), shown to **every** viewer (the banner reveals no travel detail, and an anonymous
      visitor seeing a degraded site is honest). `GeneralController` now injects `EventStore` and
      exposes a `readOnly` model flag; the banner sits above the local badge and pending banner in
      `index.html`. Two `@WebMvcTest` cases (shown when read-only, hidden when writable),
      mutation-verified by hardcoding the flag to `false`. `EventStore` mock also fed to the two
      `GeneralController` auth slices (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
      Motivated by the 2026-08-16 deploy, where a replay bug dropped the app to read-only with empty
      projections and nothing on the page said so.
- [x] **"Schedule problems" nav card is state-aware** (2026-08-15). `GeneralController` surfaces
      `scheduleProblemCount` (OWNER-only) from `ScheduleGapProjector.problems().size()`; the card in
      `index.html` is amber only when there are problems, a green tint otherwise, and its subtitle
      shows the count ("3 problems" / "1 problem" / "No problems"). Three `@WebMvcTest` cases
      (amber+count, green+none, singular), all mutation-verified. Also fed the new dependency to the
      other `GeneralController` web-slice tests (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
- [x] **GET stale-link not-found no longer attaches a dead flash** (2026-08-15). The four edit-page
      GET handlers (`ChangeHotel`/`ChangeFlight`/`ChangeTrain`/`ChangeGathering`) redirected a
      not-found id to their view-only j2html list with a `notFoundMessage` flash the list can't
      render. Dropped the flash (and the now-unused `RedirectAttributes` param/import) so they
      navigate to the list silently. `CancelHotelController` has the same dead-flash pattern but is
      out of scope here and has a test asserting the flash — left as a separate item.
- [x] **`CancelHotelController` dead flash removed** (2026-08-16). The follow-up to the item above.
      All three `notFoundMessage` flashes (GET stale link, POST malformed id, POST already-cancelled)
      redirected to the view-only `/booked-hotels` j2html list, which can't render a flash, so each
      was silently dropped. Removed all three (and the now-unused `RedirectAttributes` param/import
      from both handlers) so they navigate silently. The one test asserting the flash
      (`unknownBookingRedirectsWithAFlashMessage`) was renamed to
      `alreadyGoneBookingRedirectsToListInsteadOfThrowing` and now asserts only the redirect;
      mutation-verified. All 7 controller-slice tests green.
- [x] **Schedule-problem day-boundary anchored to "Anywhere on Earth" instead of UTC** (2026-08-16).
      The two `LocalDate`-only `ScheduleProblem` variants (`MissingHotel`, `DifferentCityConflict`)
      anchored `relevantUntil()` at `ZoneOffset.UTC`, so west of UTC — the owner's whole realistic
      range (SFO home is UTC-7/-8, all US travel) — a problem dropped off FUTURE during the *previous*
      local afternoon: a missing hotel with checkout tomorrow vanished right as the owner arrived for
      the last night. Now both anchor at `ANYWHERE_ON_EARTH` (`ZoneOffset.ofHours(-12)`), keeping a
      day-granularity problem live until its date has passed everywhere the owner could be. The
      boundary only ever moves *later* (12h) than a UTC anchor, never earlier, so the fix is strictly
      surfacing-safe — it cannot hide a problem the old code showed; the only cost is a moot problem
      lingering up to ~12h longer (accepted papercut for a safety-net view). Deep boundary coverage in
      new `ScheduleProblemTest` (exact instants, never-earlier-than-UTC guard, SFO/Hawaii last-night
      surfacing, inclusive-boundary + one-second-past exclusion, east-of-UTC lingering, and guards
      that the two instant-backed variants stay anchored to their endpoint instant). Both mutants
      (AoE→UTC per record) verified; full suite green at 814.
- [x] **Responsive, no-horizontal-scroll treatment on the other list views** (2026-08-15).
      `/tentative-conferences` (`995caab`), `/booked-trains` (`86af549`, `9bb5245`, `bebf6e6`),
      `/booked-flights` (`b6c5f92`, `9bb5245`, `370c33a`, `c67444f`, `1027144`, `46e39e6` superseded)
      and `/planned-gatherings` (`375761e`, `8c558ac`, `0755edd`). Dropped every `.page`/container
      `max-width` cap and the `overflow-x` scroller; date/time uses
      `ZonedTimeTag.renderDateTimeStacking`. The grid views (flights, trains, conferences,
      gatherings) were unified on **one grid + `grid-template-columns: subgrid`** so columns align
      across rows with min-content floors (no drift, no overlap), collapsing to a single stacked
      column with per-column leg labels below 640px. Flights keep the change-history disclosure by
      putting the list inside the `<summary>` (so it spans all columns) and hiding it while closed.
      `/planned-gatherings` was additionally reshaped from cards into a
      When/Speaking/Gathering/Venue/actions table. Each renderer's exact-markup tests updated and
      mutation-verified. Note: the `minmax(fixed, fr)` and plain-`min-width:0` attempts were tried
      and rejected (overlap / drift) — subgrid is the chosen approach.
- [x] **`ClearConflictController` POST now has error handling** (2026-08-15, `6df5f63`).
      `clearConflictSubmit` wraps the parse + `clearConflict(...)` in a `try/catch`: a malformed id
      or generic append failure re-renders the `clear-conflict` form with a global error via
      `bindingResult` (never redirecting the error to the view-only `/schedule-problems`), and
      `ReadOnlyModeException` redirects to `/read-only`. The conflict-summary fields ride the POST as
      hidden inputs so a rejected submit re-renders the summary intact. Three `@WebMvcTest` cases
      (malformed id, service failure, read-only), all mutation-verified — including a hardened
      assertion matching the visible `<strong>` summary markup, since the value also rides a hidden
      input's `value=` and a bare substring check passed even with the visible summary broken.
- [x] **`/booked-hotels` now shows the booking's real `bookingIntent`** (2026-08-15, `6df5f63`).
      `BookedHotelsProjector.put` takes a `BookingIntent` and threads it from both
      `HotelBooked.bookingIntent()` and `HotelChanged.bookingIntent()` into the view instead of
      hardcoding `TENTATIVE`, so a FINAL booking no longer reads "Tentative" next to its own FINAL
      edit form. New projector test books a FINAL hotel and asserts the view status;
      `hotelChangedOverwritesBookingUnderSameId` was extended to assert the change to FINAL lands.
      Both mutation-verified by reinstating the hardcode. Promoted to a rule in
      `EventSourcingRulesHeuristics.md` (**R8**: a projector that derives a field must derive it
      from the events, never ignore relevant data the events carry).
- [x] **Dry-run validation for an import file** (2026-08-06). `CommandImporter.validateJson` runs
      pass one of the real import — deserialize every entry and recompute its events — and writes
      nothing, returning a `ValidationReport(validCount, errors)`. A "Validate only" button on
      `/admin/import` posts the textarea to `/admin/import/validate` (covered by the existing
      `/admin/**` OWNER matcher, so no `SecurityConfig` change) and re-renders the same page with
      either every problem found or "all N entries would import. Nothing was written." The
      textarea keeps its content so the file can be fixed in place. Written because
      `/admin/zone-audit` reads `event_log` — data that is *already imported* — which is backwards
      for a wipe-then-import workflow; it gave no warning before the 2026-08-06 production import
      failed on three venues. Mutation-verified: making `validateJson` call `apply` fails both
      dry-run tests. *(2026-08-11: `CommandImporter` was retired with the event-oriented
      backup/restore rework; this dry run now lives in `BackupService.validateJson`, posted from
      `/admin/restore` to `/admin/restore/validate`, and validates event payloads rather than
      recomputed command events.)*

- [x] **Every application service goes through `CommandExecutor`** (2026-08-05, with the conference
      UTC slice). `ConferencePlanning` was the last service injecting `EventStore` directly; it now
      uses `CommandExecutor` like the rest. The enforcement test landed in the same change as
      `ApplicationServicesUseCommandExecutorTest` — plain reflection over `application`-package
      constructors, **no ArchUnit dependency**, with `CommandExecutor` itself excluded as the
      authorized holder. This unblocks the conditional-append work in
      `CommandConsistencyEventStore.md`: no service can now bypass the guard that lives in
      `CommandExecutor`.