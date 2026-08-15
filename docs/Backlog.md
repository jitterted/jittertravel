# Backlog — index of every plan doc and open item

**This is an index, not a backlog of its own.** Each row points at the doc that owns the detail;
the "What's left" column exists only so you can tell, without opening twenty files, whether
something is still live. When work lands, update the owning doc first and this table second.

Small cleanups keep living in `Cleanup_Tasks.md` — they are summarized here but not duplicated.
Decisions made during implementation that still need Ted's eye live in `DecisionsToReview.md` —
a review queue, not a backlog; work through it one entry at a time.

Status verified against the tree on **2026-08-14** (`a2b1845`).

Legend: `open` · `partial` · `done` · `exploration` (deliberately unbuilt design record)
· `decision` (waiting on Ted, not on code)

---

## Open features

| Item | Owning doc | What's left |
|---|---|---|
| **Private social event kind** | `PrivateSocialEventPlan.md` | `done 2026-08-13` — built + tested (MVP + itinerary entry). Own `EntryKind.PRIVATE_EVENT`, `PlanPrivateEvent` command / `PrivateEventPlanned` event / context, `PrivateEventCalendarProjector`, and a redacting `PRIVATE_EVENT` branch in `CalendarEntryRedactor`: anonymous viewers see `Busy / 7:00 PM → 10:00 PM EDT / Toronto, Canada` and nothing else — the one redacted output that keeps a zone-labelled time, via the new plain-text `SubtitleLine.FixedRange` (no `<time datetime>` leak). Plan form + controller + nav card (FA Pro icon placeholder pending Ted), and a `PrivateEventItineraryEntry` on `/itinerary`. Both tiers of redaction test (`CalendarEntryRedactorTest` + `CalendarRedactionSecurityTest`, mutation-verified) plus command / projector / controller / golden-sample tests; full suite green (790). **Rendering details resolved:** redacted time is a plain-text zone-labelled `FixedRange`; owner view reuses the shared `EventCalendarSubtitle`. **Deferred to follow-ups (see plan doc):** Change Private Event (edit flow) and a `/planned-private-events` list view with the FUTURE/ALL toggle. |
| **Event-oriented backup/restore** | `EventOrientedBackupRestorePlan.md` | `done 2026-08-11` — replaced command-replay export/import with event-**verbatim** backup/restore (commands kept as opaque history for future undo). Restore reuses stored event ids/sequences/timestamps; read models rebuild on **restart** via the boot replay — the live `reset()`/`rebuildFromPersistence()` rebuild was built then **reverted** (email-sender hazard), so the stale-after-truncate bug is unchanged (a restart clears it). **Backup-format break** (v2; old command-only files not read). Unblocked `DecisionContextQueryDesign.md` and cleared the private-social-event sequencing blocker. |
| **Conference submission tracking** | `ConferenceSubmissionTrackingPlan.md` | `open` — designed 2026-08-12, nothing built. Splits today's single "tentative" into **two dimensions**: attendance commitment (`WATCHING` / `GOING` / `NOT_GOING`) and a per-submission speaking pipeline (CFP pending → submitted → accepted / waitlisted / rejected / withdrawn, plus invited), because one conference takes several talk proposals. Status is **derived** in the projector, so new labels need no new events — most usefully rejected-but-undecided, which becomes a visible "decide". **Redaction (Ted, 2026-08-12): commitment is public, status is private.** Speculative conferences *do* render for anonymous viewers (that is what invites "I'll see you there"), but every speculative state collapses to one public "tentative" label, and talk titles, decisions, CFP dates, commitment *basis*, and all free-text reasons are OWNER-only and must never enter `CalendarEntry`. Adding `CalendarEntry.commitment` deliberately breaks the redactor's conference branch. Also pulls in `datesConfirmed` (held slots carry last year's guessed dates) and attendance filtering in `ScheduleGapProjector`. Cheap now that command-replay import is gone. **Decided 2026-08-12:** `SubmissionAccepted` **auto-commits** attendance (last decision wins, so a later decline overrides — submitting is opting in); `SpeakingInvitationReceived` does **not** (unsolicited offer, needs an explicit yes); a dropped conference leaves the calendar and itinerary entirely but stays on the OWNER-only `/tentative-conferences` list, hidden behind a **separate** `?dropped=` toggle orthogonal to the existing FUTURE/ALL one; **acceptance is public** (Ted announces it immediately), so there is no embargo state and no timing rule — but the commitment *basis* still stays OWNER-only, because publishing it makes its absence a free inference about submissions, and no public speaking marker exists anywhere in the app today (gatherings' `speaking` badge is owner/family-only). **No open questions remain.** Related: the `ConferenceCancelled` and `infoUrl` rows below. |
| **Schengen 90/180 day counter** | `SchengenDayCounterPlan.md` | `open` — planned 2026-08-12, **amended twice on 2026-08-12** (second amendment verified against the 2026-08-11 backup), nothing built. **Design now an evidence hierarchy, not one union:** tier 1 = **external Schengen border crossings** → entry/exit envelopes, authoritative, every interior day counts; tier 2 = union of conference/gathering/hotel dates, fallback for days no envelope covers; tier 3 = Ted's explicit assumed stays. **Prefer legs over hotels (Ted, 2026-08-12)** — hotels empirically carry the past on their own, but they evidence where he slept, not passport control; dropping them is not viable because the pre-submit warning exists for trips with no travel booked. Crossings come from **trains as well as flights** (the June trip's exit is the Brussels→London train; flights-only pairing over-counts by 4 days). Consequences: airport **membership** (a boolean, not full airport→country) moves from last to step 1; **no historical backfill is needed** (conferences contribute 0 unique past Schengen days — measured); candidate gaps clamp to the next known presence *anywhere*; an **open envelope** (entry with no exit) needs its own candidate type. Real numbers on current data: 20/90 used today, peak 45 on 2026-11-19; booking one FRA→SFO exit moved 9 days from assumed to confirmed. **Layovers (Ted, 2026-08-12):** always entered as separate flights, so a Lisbon stopover en route to Morocco produces a same-day Schengen envelope — the "invisible layover" limitation is void and replaced by a **transit candidate** where the default is counted and Ted's click removes a day. Rest of the row unchanged: Two surfaces: an owner-only current+peak strip on `/calendar`, and a pre-submit "days remaining" panel on the planning forms that fires on `country` blur (to decide Schengen membership) and `endDate` blur (to recompute). Presence is a **union of dated country intervals** from conferences, gatherings, hotels and trains — not a chain of legs — so a conference booked before any flight still counts, which is the normal order of work. **No padding** (Ted, 2026-08-12): the count rises as flights and hotels are added, and that is correct. **Amendment (Ted, 2026-08-12): two numbers, not one** — a confirmed floor (committed conferences + booked travel) and a worst-case ceiling (+ live speculative conferences + explicit *assumed stays*), from the same method called twice. Assumed stays are how contiguous-ish conferences get bridged when Ted stays in the region instead of flying home; **per-gap, no inferred tolerance** (Ted's call) — the app lists candidate gaps, Ted marks them, and the assumption is an event, i.e. just another dated interval into the union. Warning fires when **either** number hits 85, which in practice means the ceiling. **Depends on** the commitment events from `ConferenceSubmissionTrackingPlan.md` — without them every conference is speculative and the floor is always zero. Only missing datum is airport→country (`StaticAirportCityResolver` is city-only), sequenced last, and it also gates the "booked flight home breaks the gap" rule. Needs `SecurityConfig` matchers + `AuthorizationMatrixTest` rows for `/api/schengen-preview` and the assume/withdraw POSTs, and a `CalendarRedactionSecurityTest` case for the strip. |
| **Public "speaking" badge on the calendar** | `ConferenceSubmissionTrackingPlan.md` | `open` — decided 2026-08-12, nothing built. **That Ted is speaking at a conference or gathering is never private** — the event's place and time are already public — so it renders on the **anonymous** `/calendar`, not just owner surfaces. Supersedes the earlier draft that withheld it to avoid an absence-inference; "attending without speaking" is ordinary and reveals a rejection only to someone who already knows he submitted, which stays private. **Two halves, different readiness.** *Gatherings: ready now, no dependencies* — `GatheringPlanned.speaking` already exists and badges the itinerary (`ItineraryRenderer.java:231`) and OWNER-only planned list, but `GatheringCalendarProjector.toEntry` never accepts the field; thread it through, add `speaking` to `CalendarEntry`, keep it in the redactor's GATHERING branch, render the badge, both tiers of redaction test. *Conferences: waits for step 4 of the owning plan*, since the speaking fact only exists once submissions do. **Caveat (Ted, 2026-08-12): a private talk at a company is NOT this** — no public venue or time, so it must get its own `EntryKind` rather than be modelled as a conference or gathering to earn a badge; same trap as the private dinner, sibling of `PrivateSocialEventPlan.md`. Needs the `CLAUDE.md` redaction section amended in the same change. |
| **Replace Hotel** | `HotelCancelReplacePlan.md` | `partial` — Phases 0, 1 (`cancelBy`) and 2 (Cancel Hotel + deadline column) are done. Phase 3 (Replace: cancel the old booking and book a new one linked by `replacesHotelBookingId`) is unbuilt and needs a second `HotelBooked` schema bump. **Owning doc is kept as history and now carries a superseded banner (2026-08-13):** the check-in gate it specifies throughout was removed (`CancelHotelContext` is `bookingExists` alone; `CannotCancelAfterCheckIn` deleted), and cancel no longer hard-removes from `/booked-hotels`. Phase 3 is left as written but is partly invalidated — its `ReplaceHotelContext` (old `checkIn` + `now` "for the cancel gate") must be re-derived before building, and the replaced booking now *is* renderable on `/booked-hotels`. Rationale settled in `DecisionsToReview.md` S3/S5. |
| **Undo Cancel Hotel Booking** | `Future_Feature_Slices.md` | `open` — added 2026-08-13, nothing built. Reinstates a cancelled stay under its original `HotelBookingId` instead of forcing a re-entry. **Direct consequence of removing the check-in gate on Cancel Hotel** (same day): with no gate, the thing that keeps a mistaken cancel harmless is a cheap reversal, and today there isn't one. The greyed-out "Canceled" row now on `/booked-hotels` is deliberately action-free because Undo is the action that belongs there. Has to reinstate into *every* read model that drops the booking (calendar, itinerary, schedule problems, both tentative-hotel projectors, hotel details) — mirror each case in `HotelCancellationPropagationTest`. Shares its reinstate machinery with Phase 3 of `HotelCancelReplacePlan.md`. |
| **`ConferenceCancelled`** | `Future_Feature_Slices.md` | `open` — also the prerequisite for any slice that retracts a booking. Gathering cancellation is the same gap (explicitly out of scope in `ChangeGatheringPlan.md`). The event record and the projector branches already exist (`MigrateConferenceToGathering` emits it); what is missing is an owner-facing cancel action. Note it means *organizers cancelled* — `ConferenceSubmissionTrackingPlan.md` adds `ConferenceAttendanceDeclined` for "Ted decided not to go", which is a different fact. |
| **`infoUrl` on conferences** | `Future_Feature_Slices.md` | `open` — gatherings have one; conferences don't. `ConferenceSubmissionTrackingPlan.md` treats conference `infoUrl` as **public**, so it can ship with that work. |
| **`mapsUrl` on conferences** | `Future_Feature_Slices.md` | `open` — auto-computed from venue + address, as hotels do. |
| **Sunday/Monday week start** | `Sunday-Monday-Week-Start-Switch.md` | `open` — not started. `CalendarViewBuilder` still hardcodes Sunday (`CalendarViewBuilder.java:55`). Contained to the `web` package. |
| **Viewer-timezone selection** | `Future_Feature_Slices.md` | `partial / needs re-reading` — this entry predates the UTC rollout. `BrowserZoneScript` and `ZoneToggle` shipped with `UtcDatetimeStoragePlan.md` phase 4, so much of what it asks for exists. Re-read it against the current behavior before treating it as work. |

## Open cleanups

Detail lives in `Cleanup_Tasks.md`; this is the roll-call.

| Item | What's left |
|---|---|
| `/booked-hotels` ignores `bookingIntent` | `done 2026-08-15` (`6df5f63`) — `BookedHotelsProjector` now threads the real intent from `HotelBooked`/`HotelChanged` into the view; FINAL bookings read FINAL. Promoted to `EventSourcingRulesHeuristics.md` R8. |
| `ClearConflictController` POST error handling | `done 2026-08-15` (`6df5f63`) — malformed id / generic failure re-render the form via `bindingResult`; `ReadOnlyModeException` redirects to `/read-only`; summary rides as hidden inputs. Three mutation-verified `@WebMvcTest` cases. |
| Mockito replacement | `open` — replace with better test doubles. |
| Event-type filter on `/admin/eventlog` | `open` — the command-log filter is already done. |
| `/admin/commandlog` "Out of order" badge | `open` — only detects divergence *within* a page; `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` per call (`PostgresPersister.java:288`), so the first entry of any page can never be flagged. |

### Loose follow-ups not tracked anywhere else

From the Phase 1 `cancelBy` review (bottom of `HotelCancelReplacePlan.md`):

- Editing check-in earlier than an existing `cancelBy` fails on a field the user never touched
  (the form prefills it) — `ChangeHotelCommand.java:43`. Accepted behavior, not a bug; the
  alternative is clamping rather than rejecting.

*(The other two items on that list — the wrong cancel-by hint text and the duplicated
`cancelBy(LocalDateTime, ZoneId)` helper — were fixed inside `4efccaf` before it was committed.
The review had been written against the pre-fix working tree.)*

From the Cancel Hotel slice (2026-08-07):

- **`ChangeHotel` and `ChangeFlight` still decide from a projector**, which R1 in
  `EventSourcingRulesHeuristics.md` forbids ("never use a projection to make an automated
  decision"). Both read a details projector for an existence check; `CancelHotel` now folds from
  the event stream via `CommandExecutor.eventsForDecision()` and is the pattern to follow. Ted
  asked for this follow-up when choosing the fold for Cancel. Low risk today (the existence check
  is not a time gate and subscribers are synchronous), but the codebase currently contradicts its
  own rule doc. **Now owned by `DecisionContextQueryDesign.md`**, which replaces both the projector
  reads and `eventsForDecision()` with a tagged query — **unblocked 2026-08-10** (see next).
- **Export/import → event-oriented backup/restore. `open` — owning doc `EventOrientedBackupRestorePlan.md`.**
  Ted's call (2026-08-10): events are the source of truth, so backup/restore stores and restores
  events **verbatim** and stops re-executing commands; commands stay in the backup as opaque history
  for a future undo feature. This resolves the "wider decision before more commands need folded
  context" question that had blocked the decision-context query design — with no command replay on
  restore, there is no import context to fake. `DecisionContextQueryDesign.md` is therefore
  unblocked.

From `GeneralControllerRefactorPlan.md`:

- Stable `data-testid` attributes on the `index.html` nav groups, so the authorization tests stop
  asserting on `href` substrings and `>Admin</span>`.

From `j2html_Migration_Analysis.md`:

- The shared renderer infrastructure the migration proposed was never extracted — no
  `TemporalFormatter`, `ProblemCardRenderer`, or `EntryCardRenderer`; only `web/Page.java` exists.
  Formatting and card markup are duplicated across renderers.

## Decisions waiting on Ted

| Decision | Owning doc |
|---|---|
| **Adopt Strictland, or revert the spike?** The mapper pin (`EventJsonMapperFactory`) stays either way. Adoption is paused pending the author's feedback. If adopted: pick the first events to cover, the snapshot-file layout, and how coverage is enforced for new event types. | `Event_Serialization_Contract_Tests.md` |
| **Alpha-2 zone aliases for single-zone countries** (e.g. `GB`) — the last piece of the `LocationZoneResolver` state/province fix that unblocked the 2026-08-06 production import. | `UtcDatetimeStoragePlan.md` (improvement 2) |

## Explorations — deliberately unbuilt

These are design records. Do not implement without discussing with Ted first; several say so
explicitly in their own text.

| Doc | What it is |
|---|---|
| `CommandConsistencyEventStore.md` | Conditional/fenced append for a future multi-instance deployment. Nothing built. Unblocked by the CommandExecutor migration, but not committed to. |
| `DecisionContextQueryDesign.md` | `unblocked — ready to build` (was `paused`) — replace `CommandExecutor.eventsForDecision()` and the projector-based existence checks with a tagged/typed query port. Design close to settled (recommendations + open decisions recorded); the export/import blocker is resolved by `EventOrientedBackupRestorePlan.md` (2026-08-10). Owns `DecisionsToReview.md` D1 and D2. |
| `TaggedEventStoreQueryingDesign.md` (**repo root**, not `docs/`) | Filtered `EventStore` queries by event type and tag, pushed into Postgres JSONB + GIN. Deferred until a second concrete caller demands it. Its `@EventName` proposal is now **obsolete** — `EventTypes` does that job. Its "every id is a tag" rule and multi-valued tag shape are live input to `DecisionContextQueryDesign.md`. |
| `ReMoDeL-Specification.md` | A KDL-based read model definition language. Specification only — there is no KDL code anywhere in `src/`. |
| `ReadModelKdlTestDslPlan.md` | Test-support DSL for the above. Its 8 implementation steps reference an acceptance test that does not exist yet. Blocked on ReMoDeL itself. |
| `Refactoring_Opportunities.md` | 7 projection/rendering duplication findings with a priority order. Marked "do not implement without discussion". |
| `authorization_policy_centralization.md` | Authorization "Option 2" — derive `SecurityConfig` matchers and `GeneralController` nav flags from one `NavArea` policy type. Current Option 1 (two small hand-synced places) is judged low-risk because `AuthorizationMatrixTest` catches drift. This doc is the live tracker; the duplicate checklist inside `GeneralControllerRefactorPlan.md` is not maintained. |

## Done

Kept as design records. Each has a status banner at the top of the file.

| Doc | Landed |
|---|---|
| `UtcDatetimeStoragePlan.md` | All 7 phases + review fixes. `25104b9`, 2026-08-05 |
| `UtcDatetimePlanReview.md` | Folded into the plan above; historical record, checkboxes no longer maintained |
| `GatheringConferenceUtcRolloutPlan.md` | Gathering `ddf4ba8` (2026-07-27), conference `67a47af` (2026-08-05) |
| `TrainFlightUtcRolloutPlan.md` | Trains `daa7107`, flights `d2884fb`, 2026-06-24 |
| `HomeCityPlan.md` | `26d15ac`, 2026-07-26 |
| `ChangeGatheringPlan.md` | `f28cb49`, 2026-07-25 |
| `ChangeHotelPlan.md` | `d98af11`, 2026-06-21 |
| `ChangeTrainPlan.md` | `8390262`, 2026-06-17 |
| `Remove-Local-Profile.md` | `e962915`, 2026-06-17 |
| `j2html_Migration_Analysis.md` | `b0e6f11`, 2026-06-06 — views migrated, shared infrastructure not extracted (see loose follow-ups) |
| `GeneralControllerRefactorPlan.md` | Shipped; its "open items" 1 and 2 are resolved, item 3 moved here |

## Shipped without an owning plan doc

Small-to-medium slices that went straight from conversation to code, so no plan doc names them.
Recorded here only so this index matches the tree — none of them has open work unless a row says so.

| What shipped | Landed |
|---|---|
| **Multi-segment flight lookup offers each leg.** AeroDataBox returns one entry per segment for a flight number; the client used to collapse them into leg 1's departure and leg N's arrival, inventing a route no leg flies (UA 1604 → `RDU → RNO`). `FlightLookupCandidates` now carries every segment and reports whether a choice is needed; `book-flight` / `change-flight` render a shared "Which flight?" fragment and post the chosen segment back to `.../lookup/select` (no second API call). The whole trip is offered as an extra option only when the segments chain end to end. Also seeds the lookup date from `?date=` or the booking's own departure-zone day. **New route `/booked-flights/*/lookup/select`** — two segments past the id, so no existing matcher covered it; it has its own `SecurityConfig` line and `AuthorizationMatrixTest` row. | `a2b1845`, 2026-08-14 |
| **Owner home page redesign** — two-column viewing/editing layout for OWNER. The nav-card items in `Cleanup_Tasks.md` (state-aware "Schedule problems" card) still apply to it. | `5277366`, 2026-08-13 |
| **Owner future-day create menu on the calendar** — clicking a future day offers "Add flight / gathering / conference…", seeding each form via `?date=`. | `93a78ad`, 2026-08-12 |

## Not a task list

These describe how things are, not what to do next, and belong in no status column:
`JS-Behavior-Tests.md`, `EventSourcingRulesHeuristics.md`, `DEPLOYMENT.md`, `lodging-event-model.md`,
`ChangeFlightSlices.md`, `Features.md`, `EventModel.txt`, `EventLanesDoc.txt`,
`Tentatively-Plan-Conference-slice.txt`, `docs/slices/`.
