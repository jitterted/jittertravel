# Plan: Change Private Event — cancel first, then edit

> **Status: `partial`** — planned 2026-08-24 (Ted), **slice 1 (Cancel) shipped the same day**;
> slice 2 (the edit flow) is unbuilt. Every open question was answered before building (see
> "Answered by Ted" below), and the slice went in as written — see "What shipped" under slice 1 for
> the two places the code says something the plan did not. This doc now **owns** the
> "Change Private Event (the edit flow)" line that has lived in `Cleanup_Tasks.md` since
> 2026-08-21; that entry becomes a pointer here.
>
> **Revised 2026-08-24, after the list view shipped.** `PlannedPrivateEventsListPlan.md` landed
> `/planned-private-events` the same day, and it moved three things under slice 2 (Ted reviewed and
> settled all three before any of slice 2 was written):
>
> - **A5 replaces "`PrivateEventDetailsView` grows the raw fields"** — the edit form gets its **own**
>   `PrivateEventEditView`. See A5; this is a correction, not a preference.
> - **Six existing read models need a `PrivateEventChanged` branch, not five** — the list's projector
>   is the sixth (phase 2).
> - **The list row gains the Edit link** alongside the calendar and itinerary pencils (phase 3).

## Context

`archived/PrivateSocialEventPlan.md` shipped the kind on 2026-08-13 as **plan-only**: a private
event could be entered (`/plan-private-event`), and after that it was immutable. There was no edit
page, no cancel action, and no list — the only places one was visible were `/calendar` (owner and,
collapsed to `Busy`, anonymous) and `/itinerary`. Slice 1 below added the cancel, and
`PlannedPrivateEventsListPlan.md` added the list, both on 2026-08-24; **the edit page is the last
of the three still missing.**

**Cancel ships before edit**, which is why the plan's own name is second in it. Three reasons:

1. **The common failure is a wrong entry, not a changed dinner.** A private event has no booking,
   no confirmation and no counterparty — it is Ted's own note that he is busy. When one is wrong,
   removing it is the honest correction; editing it is the rarer case.
2. **A wrong one poisons the schedule report, and today nothing can remove it.** A private event is
   an `Occupancy` in `ScheduleGapProjector` (`ScheduleGapProjector.java:152`) exactly as a
   gathering is — it asserts that Ted is in a city between two instants, and that assertion feeds
   away days, the missing-hotel check and (once `PrivateEventCityConflictCleared` exists, see
   `Backlog.md`) different-city detection. A mistyped date is a false presence fact with no way out.
   This is the same argument `GroundTransferCancelled` was built on, and it applies here unchanged.
3. **Cancel builds what edit then reuses:** the details projector, the URL prefix, the
   `SecurityConfig` matcher and the matrix row, and the `privateEventId` on the itinerary entry.
   Slice 2 adds a pencil beside a bin that is already there, rather than inventing both.

The model to copy throughout slice 1 is **`CancelGroundTransfer`** (shipped 2026-08-23) — the most
recent cancel slice, itinerary-linked, and the only one whose subject has no booking. Slice 2's
model is **`archived/ChangeGatheringPlan.md`**: a private event is a gathering minus `speaking` and
`infoUrl`, so its edit flow is that one with two fields removed.

---

## Decisions

**D1 — `PrivateEventCancelled(PrivateEventId)`, and cancellation is a hard removal.** Every read
model drops the event entirely; the event log is the only record it was ever planned. Mirrors
`GroundTransferCancelled`, and for its stated reason: an entry left in place keeps feeding a
presence fact that is not true.

**D2 — There *is* a reason field: optional free text, `""` when none was given** (Ted, 2026-08-24).
The transfer's argument — no booking, so nothing to explain away — was the wrong test. A note is
almost always useful and costs one nullable-normalized component: **"rescheduled to Friday"** is
the case that decides it, and it is a fact about the evening, not about a reservation. Shape and
wording copy `HotelBookingCancelled` exactly, down to the compact constructor and "recorded for the
traveler's own recall — nothing keys off it".

Be honest in the Javadoc about where it can be read: cancellation is a **hard removal**, so the
entry is gone from every read model and the note survives only in the event log
(`/admin/eventlog`) — which is the same place the hotel's lives. That is not an argument against
recording it; it is the reason the field's text says *recall*, and "rescheduled to Friday" is
exactly what a future re-plan flow would want to prefill from.

**D3 — Existence is the only refusal; no time gate.** `CancelPrivateEventContext(boolean
privateEventExists)`, folded from the event stream. A **past** private event is cancellable —
it is exactly the entry most worth removing, and the projectors it feeds look backwards as well as
forwards. Note the deliberate asymmetry with `PlanPrivateEventCommand`, which refuses a date that
is not in the future. Ted accepted that asymmetry, and what follows from it — a cancelled past
event cannot be put back from inside the app — rather than gating the cancel or colouring the page
red (A1).

**D4 — URL prefix is `/planned-private-events/…`, not `/private-events/…`.**

| Path | Slice | What it is |
|---|---|---|
| `/planned-private-events/{id}/cancel` | 1 | GET confirmation, POST performs |
| `/planned-private-events/{id}` | 2 | GET edit form, POST saves |
| `/planned-private-events` | — | the list view — **shipped 2026-08-24**, `PlannedPrivateEventsListPlan.md` |
| `/plan-private-event` | (shipped) | the entry form — **unchanged**, keep it where it is |

The gathering's shape (`/planned-gatherings/{id}`), not the transfer's (`/ground-transfers/*/cancel`).
The transfer chose its own prefix because it has no list page and never will; a private event now
has all three routes under one prefix, which is what this decision bought — three prefixes for one
kind is what we would have if slice 1 had picked `/private-events`. Matcher ordering follows the gathering
precedent: the per-item entries go **before** the bare list matcher, and because a single `*`
matches one segment, `/planned-private-events/*/cancel` needs its **own** entry alongside
`/planned-private-events/*` (the same trap `/booked-flights/*/lookup` documents).

**D5 — Its own page, no typed word, amber not red.** Recording a `*Cancelled`
after a `*Planned` destroys nothing — the log, the timeline and `/admin/eventlog` still hold the
event — so per CLAUDE.md this is an **ordinary domain action**: no typed word (that gate belongs to
`/admin/database` and `/admin/migrate-legacy-events`, and spending it here is what makes it noise
there), and amber (`#b45309`) because planning the event again puts it back. **Amber stands even
though a cancelled *past* event cannot be re-planned** (Ted, 2026-08-24) — see D3; the entry Ted
actually cancels is a future one, and refusing to remove a wrong past event to protect a
reversibility nobody wants is the worse trade.

The confirmation page exists so the sentence about *what cancelling does* has somewhere to live,
and so a POST is never one stray click away. Copy `cancel-ground-transfer.html` for the layout and
the amber button, and take the **optional reason field** from `cancel-hotel.html:104-116` (a single
`<input type="text" name="reason">` above the actions, with a placeholder) — per D2 this page is a
one-field form, not a bare button.

**D6 — The link is the itinerary's, and it is OWNER-only.** `ItineraryRenderer.renderPrivateEvent`
gains the trash `cancelBin(...)` in the entry title, in the same slot the transfer's sits in
(`ItineraryRenderer.java:380`) — the slot the pencil occupies on kinds that have an edit page. The
itinerary is `hasAnyRole("FAMILY","OWNER")`, and a family viewer can *never* cancel, so the bin
renders **not at all** for them — not greyed. That is the authorization half of the affordance rule
(CLAUDE.md: "where a viewer could never trigger it, render nothing"), and it is why
`renderPrivateEvent` has to start taking `isOwner`, which today it is the only entry kind not to.

**D7 — Slice 2 is `ChangeGathering` with two fields removed.** In-place, same `PrivateEventId`,
one full-snapshot `PrivateEventChanged` event. No new id, no cancel-and-rebook.

---

## Key constraints (the checklist this slice is graded against)

- Append through **`CommandExecutor`**, never `EventStore` (`ApplicationServicesUseCommandExecutorTest`).
- The decision fact is folded from the **event stream**, never read from a projector (R1) — copy
  `CancelGroundTransfer.contextFor`.
- `commandId` is a **fresh `UUID` captured in the controller** (an event may be cancelled once, but
  the plan path's "commandId = the entity id" trick does not extend to a second command on the same
  entity). No `now` is needed at all in slice 1, per D3.
- New `Event` ⇒ `register(...)` in `EventTypes` **and** a case in `GoldenEventDeserializationTest`,
  in the same change.
- New route ⇒ `SecurityConfig` matcher **and** an `AuthorizationMatrixTest` row, in the same change
  (`.anyRequest().permitAll()` means the route is public until both exist).
- Thymeleaf POST endpoints ⇒ `@WebMvcTest` with `@WithMockUser` and `.with(csrf())`.
- Renderer assertions name **whole elements/attributes**, and every new or changed test is
  mutation-verified.
- Both gates before push: `./mvnw test` and `./mvnw test -Pjs-tests`.

**Not a schema bump.** `PrivateEventCancelled` is a new event type at `schema_version` 1; nothing
stored changes shape, so there is no upcaster, no migration, no `OneOffTaskRegistry` entry, and
backup stays at **v3**. Old backups restore untouched. If an `infrastructure` edit beyond the two
`register(...)` lines appears in slice 1, something has stopped being additive.

---

## Slice 1 — Cancel Private Event — **SHIPPED 2026-08-24**

### What shipped

Everything in the four phases below, as written. Suite green both tiers (**1724** unit + **61**
js), and every new or changed test mutation-verified in three rounds — the five removal branches
knocked out together (7 failures, one per case), then the renderer's `isOwner` guard both ways, the
`SecurityConfig` matcher, the controller's reason parameter, the command's reason, and the event's
null→`""` normalization.

Two notes for whoever builds slice 2:

- **`ScheduleGapProjector` needed no new plumbing.** Removing the occupancy from the existing map
  was the whole change, and the observable proof is `SchedulingConflict`: a gathering and a dinner
  at the same hour clash, and cancelling the dinner clears it. That test is what shows the
  occupancy left the *state* rather than just a calendar.
- **No nav card, deliberately.** The cancel page is reached from the itinerary bin and nowhere
  else; `index.html` gains nothing. The private event's nav card still carries its placeholder icon
  (`Cleanup_Tasks.md`), which is untouched by this slice.

### Phase 1 — Domain

- **`PrivateEventCancelled(PrivateEventId privateEventId, String reason) implements Event`**, with
  the `HotelBookingCancelled` compact constructor normalizing `reason` null→`""` (no null Strings
  in the domain). Javadoc carries the presence-fact argument from Context above, says the removal
  is hard, and says where the reason can be read (D2).
- **`PrivateEventNotFound`** — runtime exception, mirrors `GroundTransferNotFound`.
- **`CancelPrivateEventContext(boolean privateEventExists) implements DecisionContext`** — one
  fact, no clock (D3).
- **`CancelPrivateEventCommand(PrivateEventId privateEventId, String reason) implements
  DomainCommand<CancelPrivateEventContext>`**: `!privateEventExists` → `PrivateEventNotFound`; else
  `Stream.of(new PrivateEventCancelled(privateEventId, reason))`. The reason is carried, never
  inspected — no rule reads it, and none should start.
- `register("PrivateEventCancelled", PrivateEventCancelled.class)` in `EventTypes`.

### Phase 2 — Application + projections

**2a. `CancelPrivateEvent`** (`application/`) — `CommandExecutor` only, no projector dependency.
Folds `privateEventExists` over `commandExecutor.eventsForDecision()`: `PrivateEventPlanned` → true,
`PrivateEventCancelled` → false, so a second cancel is refused as not-found rather than emitting a
duplicate. Straight copy of `CancelGroundTransfer` with the types swapped.

**2b. `PrivateEventDetailsView` + `PrivateEventDetailsViewProjector`** (`application/`) — what the
confirmation page shows, keyed by `PrivateEventId`, `findById` returning `Optional`. `Planned` is
an upsert, `Cancelled` a `remove`, so a stale link resolves to nothing rather than to the wrong
evening. Fields: `privateEventId`, `title`, `venueName`, `city`, `country`, `startsAt`, `endsAt`
(the last two as `LocalDateTime` — venue-zone wall clock, both ends sharing one zone). ~~Slice 2
extends this record with the remaining `Address` parts and the zone for form binding~~ — **no
longer: superseded by A5.** This record stays exactly this narrow, permanently; the edit form gets
its own `PrivateEventEditView`. Register the bean via `bootstrapper.register(...)` in
`EventSourcingConfig` (`EveryProjectorBeanIsRegisteredTest` covers this the day it is written).

**2c. A `PrivateEventCancelled` branch in every existing consumer of `PrivateEventPlanned`** —
four, each a removal keyed by `PrivateEventId`:

| Consumer | Branch |
|---|---|
| `PrivateEventCalendarProjector` | `entries.remove(e.privateEventId())` |
| `PublicCalendarProjector` | `entriesBySubject.remove(e.privateEventId())` |
| `ItineraryProjector` | `privateEventEntries.remove(e.privateEventId())` |
| `ScheduleGapProjector` | `privateEvents.remove(e.privateEventId())` |

The public one is not optional politeness: a cancelled private event that stays on the anonymous
calendar is a `Busy` block asserting Ted's whereabouts on a day he is not there. `PublicCalendarProjector`
already removes on all four of the other removal events, so this is the fifth row of an existing
pattern — and `CalendarRemovalPropagationTest` grows the fifth row that proves both models agree.

**2d. `PrivateEventItineraryEntry` gains `PrivateEventId privateEventId`** as its first component,
threaded from `ItineraryProjector.toPrivateEventEntry`. Same reason `GroundTransferItineraryEntry`
carries one: the card's OWNER-only cancel link, and nothing else.

### Phase 3 — Web

- **`CancelPrivateEventRequest(UUID privateEventId, String reason)`** (`web/`) — a record, not a
  form bean: the id comes from the path and the reason from a single request parameter, so the
  controller builds it directly. Normalizes `reason` null→`""` in its compact constructor, exactly
  as `CancelHotelRequest` does.
- **`CancelPrivateEventController`** — a copy of `CancelGroundTransferController`:
  - `GET /planned-private-events/{privateEventId}/cancel` → look up the details view; empty
    (unknown, malformed UUID, or already cancelled) → `redirect:/itinerary` **silently**, because
    the itinerary is a j2html view that cannot render a flash; else render `cancel-private-event`
    with `model.addAttribute("reason", "")`, so the field binds on first render
    (`CancelHotelController.java:49`).
  - `POST` same path, taking `@RequestParam(value = "reason", required = false) String reason`
    → re-look-up, capture the event's **day** before cancelling, call the service
    with a fresh `UUID.randomUUID()` as the commandId, catch `PrivateEventNotFound` (cancelled in
    another tab) → `redirect:/itinerary`; success → `redirect:/itinerary?date=<day>`, landing back
    on the day it left, which is what shows it is gone.
- **`cancel-private-event.html`** — copy `cancel-ground-transfer.html`. Summary block: title, venue
  and city, the date, the time range. Then the **optional reason input** (D5), the amber confirm
  button, a "Keep it" link back, and a warning that
  says what cancelling actually does — removes it from the calendar and the itinerary, and drops
  the presence fact from `/schedule-problems` (so a night it was covering may reappear as a
  problem); the event log keeps the record; to put it back, plan it again.
- **`ItineraryRenderer`** — `renderPrivateEvent(e, isOwner)`, wrapping the title's `span(e.title())`
  in a `div.entry-title` that appends `cancelBin("/planned-private-events/" + id + "/cancel",
  "Cancel private event")` when `isOwner`. Update the `case PrivateEventItineraryEntry` arm in
  `renderEntry` to pass it through.
- **`SecurityConfig`** — add `"/planned-private-events/*/cancel"` to the per-item OWNER block
  (D4), beside `/ground-transfers/*/cancel`. Slice 1 adds **only** the cancel action; the bare
  `/planned-private-events/*` matcher arrives with the page it gates, in slice 2.

### Phase 4 — Tests

| Test | Claim |
|---|---|
| `CancelPrivateEventCommandTest` | unknown/already-cancelled id throws `PrivateEventNotFound`; live id emits exactly one `PrivateEventCancelled`; **a past event cancels** (pins D3); the reason rides onto the event verbatim, and a `null` reason becomes `""` |
| `CancelPrivateEventTest` (application) | existence is folded from the stream — planned→cancelled→cancel is refused; a `CommandExecutor` fake proves no `EventStore` use |
| `PrivateEventDetailsViewProjectorTest` | upsert on planned, gone after cancelled, `findById` empty for an unknown id |
| `PrivateEventCancellationPropagationTest` | mirrors `GroundTransferCancellationPropagationTest`: one stream, present in calendar + public calendar + itinerary + schedule timeline, then gone from all four |
| `CalendarRemovalPropagationTest` (extend) | the fifth removal event: present-in-both → gone-from-both |
| `CancelPrivateEventControllerTest` | GET renders the summary **and the reason input**; unknown/malformed id redirects to `/itinerary`; POST cancels and redirects to `/itinerary?date=…`; a submitted reason reaches the service, and an omitted parameter arrives as `""` not `null`; `PrivateEventNotFound` on POST redirects without error |
| `ItineraryRendererTest` | owner body `contains("href=\"/planned-private-events/<id>/cancel\"")`; the **family** body `doesNotContain` that exact href — and still contains the event's title, so the assertion is about the bin, not the card |
| `AuthorizationMatrixTest` | `arguments("/planned-private-events/abc/cancel", OK, DENIED_HOME, LOGIN)` |
| `GoldenEventDeserializationTest` | a `PrivateEventCancelled` sample deserializes, reason and all — plus a sample with the field **absent**, pinning null→`""` at the wire boundary |

Redaction: no new public field ships in slice 1, so no new `CalendarRedactionSecurityTest` case is
required — the public change is a *removal*, which `PrivateEventCancellationPropagationTest` and
`CalendarRemovalPropagationTest` both assert. Do not skip the public-projector branch on the
strength of that: a missing branch there is a silent leak of stale whereabouts.

### Not in slice 1

The calendar's cancel bin (`EntryDetails.PrivateEvent` is an empty record and
`CalendarViewBuilder.ownerActions` returns `List.of()` for it) — **it arrives in slice 2 with the
pencil** (Ted, 2026-08-24), so the calendar card gains its whole vocabulary at once rather than a
bin now and a pencil later; actions appearing one at a time is what the affordance rule is unhappy
about. Leave the `ownerActions` arm and its comment as they are, and update the comment in slice 2.
The `/planned-private-events` list view — which then shipped on its own later the same day
(`PlannedPrivateEventsListPlan.md`), reaching back into slice 2 in the two places noted at the top.
Undo.

---

## Slice 2 — Change Private Event (the edit flow)

Ships after slice 1; nothing here is needed to make slice 1 useful.

### Phase 1 — Domain

- **`PrivateEventChanged(PrivateEventId, String title, String venueName, Address location,
  ZonedTimestamp startsAt, ZonedTimestamp endsAt) implements Event`** — the full snapshot,
  identical in shape to `PrivateEventPlanned` (including its `venueName` null→`""` compact
  constructor). `register(...)` in `EventTypes` + a golden sample.
- **`ChangePrivateEventContext(boolean privateEventExists, LocalDate today)`**, and
  `ChangePrivateEventCommand` with the rules in order, matching `ChangeGathering`:
  `!privateEventExists` → `PrivateEventNotFound`; the **new** date not after `today` →
  `PrivateEventDateNotInFuture` (exists); end not after start → `InvalidPrivateEventTimeRange`
  (exists). An event whose original date has passed may still be edited, as long as the new date is
  in the future.

### Phase 2 — Application

- `ChangePrivateEvent` service + a handler mapping the request to the command, mirroring
  `PrivateEventPlanning`/`PlanPrivateEventHandler` — including the `LocationZoneResolver` pass, so
  moving the venue moves the zone.
- **`PrivateEventEditView` + `PrivateEventEditViewProjector`** — the edit form's own read model,
  **not** a widened `PrivateEventDetailsView` (A5). Shape is `GatheringDetailsView`'s exactly, minus
  `speaking`/`infoUrl`: `privateEventId`, `title`, `venueName`, the whole `Address` (so
  `locationForMatching` rides along), and `startsAt`/`endsAt` as **`ZonedTimestamp`** — raw and
  unformatted, so the form's date/start/end inputs fill from `localDateTime()` and re-opening the
  form shows what was entered rather than a time shifted into the server zone. The zone comes from
  `startsAt.zone()`; no separate component. `Planned` and `Changed` are both upserts, `Cancelled` a
  removal. Register the bean via `bootstrapper.register(...)`
  (`EveryProjectorBeanIsRegisteredTest` catches a forgotten one).
- A `PrivateEventChanged` branch in **every** existing consumer that handles `PrivateEventPlanned`,
  each an upsert by id. There are **six**, not the five this plan said before the list view shipped:

  | Consumer | Why it must see the change |
  |---|---|
  | `PrivateEventCalendarProjector` | the owner calendar's title, venue, city and time range |
  | `PublicCalendarProjector` | the anonymous `Busy` block's **times and city** — stale ones assert Ted's whereabouts wrongly |
  | `ItineraryProjector` | the itinerary card |
  | `ScheduleGapProjector` | the occupancy — a moved dinner is a moved presence fact, and away days and the missing-hotel check read it |
  | `PrivateEventDetailsViewProjector` | the cancel page must name the evening as it stands now |
  | **`PlannedPrivateEventsProjector`** | **the list row — added 2026-08-24 with the list view.** Miss it and a renamed dinner keeps its old title and address on `/planned-private-events`. Its `PrivateEventPlanned` arm already carries a comment saying this branch belongs here |

  Every one gets a plan→change lifecycle scenario test — that, not sealing `Event`, is how this
  project guards exhaustiveness. With the new edit projector that is **seven** read models handling
  `PrivateEventChanged` once slice 2 lands.

### Phase 3 — Web

- `ChangePrivateEventRequest` (same fields as `PlanPrivateEventRequest`, **including its own copy of
  `getLocation()`** — the fourth copy, written deliberately: A4 extracts *after* this slice, not
  inside it), `ChangePrivateEventController`
  at `GET`/`POST /planned-private-events/{privateEventId}`, and `change-private-event.html` copied
  from `plan-private-event.html` (including the address-parse helper) — retitled, posting to the
  per-event URL, errors rendered **on the form page** via `rejectValue`, never a redirect to a view
  that drops the flash.
- **The pencil arrives with the page it points at**, in both places, exactly as
  `Cleanup_Tasks.md` says an edit page must: `EntryDetails.PrivateEvent` gains
  `String editPath`, `PrivateEventCalendarProjector` sets it, `CalendarViewBuilder.ownerActions`
  gets its arm, and `ItineraryRenderer.renderPrivateEvent` appends `editPencil(...)` beside the bin.
- **The calendar's cancel bin lands here too**, deferred from slice 1 (Ted, 2026-08-24). So
  `EntryDetails.PrivateEvent` gains **both** `editPath` and `cancelPath` in one change, and its
  `ownerActions` arm renders pencil-then-bin — the `Lodging`/`Gathering` pencil and the
  `GroundTransfer` bin combined, in that order, in the slot both already use. Null-tolerant like
  the transfer's (`cancelPath() == null ? List.of() : …`), so a calendar built before the projector
  sets it degrades to no action rather than a broken href.
- **The list row gains its Edit link** (added 2026-08-24 with the list view).
  `PlannedPrivateEventsRenderer.actionsCell` renders Cancel today and **Edit is appended below it**
  — that order is load-bearing and was chosen for this moment: the cell is a column flex, so
  appending keeps Cancel exactly where it has always been (`PlannedPrivateEventsListPlan.md` D5,
  and CLAUDE.md's "action affordances never move"). Two links, well under the three that would
  call for a menu. Its test asserts the whole anchor, as the Cancel one does.
- `SecurityConfig`: add `/planned-private-events/*` to the per-item OWNER block; matrix row
  alongside. **The ordering this needs is already there** — the per-item block sits ahead of the
  list block that `/planned-private-events` joined on 2026-08-24, so this is an addition to an
  existing group rather than a reshuffle. `/planned-private-events/*/cancel` keeps its own entry
  regardless: a single `*` matches one segment.

### Phase 4 — Redaction tests (this slice needs both tiers)

Slice 2 adds an event the **public** projector reads, so CLAUDE.md rule 5 applies in full:
a `PublicCalendarProjectorTest` case asserting that after a `PrivateEventChanged` the public entry
is still `Busy` — carrying the new *times and city* and **not** the new title — and a
`CalendarRedactionSecurityTest` case asserting the rendered anonymous body `doesNotContain` the new
title, built by driving real events through a real `PublicCalendarProjector`. Assert on absence of
the title, not merely on the presence of `Busy`.

---

## Answered by Ted — 2026-08-24

All five questions this plan has opened are closed — the first four before slice 1, and **A5 after
the list view shipped later the same day**. Nothing here is waiting on a decision; the decisions
above already read as settled, and these are the reasons behind them.

**A1 — Keep amber, and keep the no-time-gate (D3, D5).** A cancelled *past* private event cannot
be re-planned, because `PlanPrivateEventCommand` refuses a date that is not in the future — so
"plan it again" restores a future event but not a past one, and the colour rule asks exactly that
question. Amber stands anyway: the entry Ted actually cancels is a future one, and the rejected
alternative (red for a past event only) would have the page colour itself from the event's date,
buying inconsistency to protect a reversibility nobody wants.

**A2 — The calendar bin waits for slice 2, with the pencil.** Slice 1 links from the itinerary
alone. See "Not in slice 1" and slice 2's phase 3.

**A3 — Record a reason after all (D2), reversing the transfer's call.** "Better to have it and not
use it" — *"rescheduled to Friday"* is the note that makes the case. Optional free text, `""` when
none, `HotelBookingCancelled`'s shape exactly.

**A5 — The edit form gets its own `PrivateEventEditView`; do not widen `PrivateEventDetailsView`**
(Ted, 2026-08-24, after the list view shipped). This **replaces** the phase-2 line that said the
details view "grows the raw fields the form binds". That line was right when written, when the
details view was the kind's only read model besides the calendars. It is wrong now, twice over:

1. **It contradicts the reasoning the list view was built on.** `PrivateEventDetailsView` belongs to
   the **cancel** page, which is a *recording* surface: it names the evening being removed and
   carries nothing else, and its Javadoc says so. `PlannedPrivateEventsListPlan.md` D1 declined to
   widen it for the list on exactly this ground — adding fields a surface must then not render is
   the carry-and-strip shape this codebase keeps rejecting. Widening it for the edit form instead
   would make the same move with a different caller.
2. **It contradicts the gathering precedent this slice claims to follow.** A gathering has **two**
   records, not one widened: `GatheringDetailsView` ("used to hydrate the edit form", raw `Address`
   + `ZonedTimestamp`s) and `PlannedGatheringView` (the list). A private event already has the list
   one and the cancel one; the edit form is the third surface, and the gathering's answer to a third
   surface is a third record.

The cost is a third read model over the same events, which is the cost this codebase already pays
everywhere: one read model per surface, shaped by that surface. `EveryProjectorBeanIsRegisteredTest`
keeps the new bean honest.

**A4 — REVISED 2026-08-24 (Ted asked for the costs and gains). The wait is over, but the thing to
extract is not the interface — and it happens *after* slice 2, not inside it.**

> The original answer read: *"Leave `VenueEventRequest` unextracted; wait for one more user.
> `ChangePrivateEventRequest` makes `getLocation()` a third copy, which clears the letter of the
> second-user rule, but this is the same case seen three times rather than a shape that has proved
> general. Revisit when a fourth — or a different-shaped — venue request turns up."*
>
> It miscounted (three copies exist *today*; slice 2 makes the fourth) and, more importantly, it
> misread the precedent it was reasoning from.

**What is actually duplicated:**

| Fragment | Copies today | After slice 2 |
|---|---|---|
| `getLocation()` — one line building an `Address` from six fields | 3 — `PlanGatheringRequest`, `ChangeGatheringRequest`, `PlanPrivateEventRequest` | 4 |
| Handler preamble — `getLocation()` → `venueZone.resolve(getZone(), location)` → two `ZonedTimestamp.fromLocal(getDate().atTime(…), zone)` | 3 handlers | 4 |

`PlanGroundTransferHandler` has the date+times half but **not** the venue half: its zone comes from
two endpoints, not one address. It stays out of anything extracted here.

**Why `HotelStayRequest` is not the precedent A4 thought it was.** Its gain was never deduping
getters. Its own Javadoc names it — *"`HotelHandler` reads both through this interface and turns a
request into a command **in one place**"* — and `HotelHandler`'s Javadoc names the harm that
motivated it: *"Previously `BookHotelHandler` and `ChangeHotelHandler` held byte-identical copies of
it, which is exactly how the cancellation deadline's null-preserving zone conversion came to exist
twice."* The interface was the **enabler**; merging two handlers that had already diverged was the
**gain**.

That only half transfers. Hotels dedupe book+change of **one kind** — same id type, same command
arity, same extras — so one class with two methods works. Venue events duplicate across **two
kinds**: the commands differ in type, in id type, and in trailing fields (`speaking`/`infoUrl`
versus nothing). **The four handlers cannot collapse into one that returns a command.** Only their
*inputs* can be shared. Note also that the bug-prone part is already gone: `VenueZone` (5 users)
absorbed the one branch — explicit `CommonZone` wins, else the address must resolve — so what
remains has no conditional for a fourth copy to drift wrongly into.

**The options, and the verdict:**

- **(B) The interface on its own** — the literal original proposal. ~12 getter declarations plus a
  `default Address getLocation()`: removes four one-liners, adds a fourteen-line interface, net LOC
  a wash, and **nothing consumes it polymorphically**. A type with no client is abstraction in
  anticipation — the very rule A4 was invoking. **Rejected on its own terms.**
- **(C) Extend the `VenueZone` seam to produce the timing too** — a helper taking a
  `VenueEventRequest` and returning `(location, startsAt, endsAt)`, so each handler becomes "call
  the helper, build my command". This removes the four-line preamble ×4, and it is the version
  where the interface **earns its keep the way `HotelStayRequest` does**: as the helper's parameter
  type. **Chosen.**

**And it is a follow-up, not part of slice 2.** Slice 2 writes the fourth copy exactly as the
original A4 said. The extraction then happens with all four call sites visible at once — which is
both when the second-user rule is properly satisfied and when the refactor is safest to perform.
Folding it into slice 2 would drag the two shipped, working gathering handlers into an edit-flow
slice; the cost of waiting is one re-edit of a file slice 2 will just have written. Tracked in
`Cleanup_Tasks.md`.

**One stale comment to fix in slice 2:** `PlanPrivateEventRequest`'s Javadoc still says a shared
`VenueEventRequest` is "deferred pending Ted's call". The call is made — point it at this answer.

---

## Not in this plan

- **`/planned-private-events` list view — SHIPPED 2026-08-24**, owned by
  `PlannedPrivateEventsListPlan.md`. It was never a prerequisite for either slice here and was not
  treated as one: cancel is reached from the itinerary, edit from the calendar/itinerary pencil.
  D4 kept the bare path free for it, which is what it took. It reaches **into** slice 2 in two
  places all the same — the sixth `PrivateEventChanged` branch and the row's Edit link, both in the
  phases above — so it is no longer "not in this plan" so much as a neighbour this slice must
  update.
- **Undo cancel** — the same gap `Future_Feature_Slices.md` records for hotels.
- **`PrivateEventCityConflictCleared`** — tabled by Ted 2026-08-20, see `Backlog.md`. Slice 1 does
  not make it more or less needed.
- **The nav card's placeholder icon** — **settled 2026-08-24**: FA Pro `utensils`, the SVG the plan
  card was already carrying. Nothing for this slice to do.
