# Private Social Event — Plan

**Status:** `open` — planned 2026-08-12. Modeling decided (own entry kind, own slice); scope
decided (**MVP + itinerary entry**: plan form + `/calendar` with redaction + an `/itinerary`
entry; Change and planned-list deferred); A+B+C refactoring folded in. Two rendering details
still to resolve while coding (zone label on redacted time; owner-view format).

Moved out of `Cleanup_Tasks.md` (it is a full vertical slice, not a small fix). The
anonymous-view decision recorded there (2026-08-07) is carried forward verbatim below.

## Why

Today the only social entry kind is `GATHERING`, which is **public by decision** — name,
venue, city, `infoUrl`, and times all render to anonymous viewers on `/calendar`. A private
dinner with friends modelled as a gathering would be fully exposed. CLAUDE.md flags this as a
known leak and forbids reusing `GATHERING` for anything private. There is no live leak today
only because Ted avoids entering private events at all.

## Modeling decision (Ted, 2026-08-12)

**Own entry kind, own slice.** A private social event gets its own command, event, decision
context, and projectors — paralleling the gathering slice — rather than a `private` flag on
`GatheringPlanned`. Reason: a flag would force *every* public gathering projector
(`PlannedGatheringsProjector`, `GatheringItineraryEntry`, `GatheringCalendarProjector`) to
individually remember to hide the private one; miss one and it leaks. A distinct type means
nothing public can render it by accident, and the redactor gets its own branch as CLAUDE.md
requires.

## Anonymous view (Ted, 2026-08-07 — unchanged)

Anonymous viewers on `/calendar` see only:

```
Busy
7pm–10pm EDT
Toronto, Canada
```

"Busy", the **time range in the event's own zone** (with a zone label), and **city + country**.
No name, no venue, no street, no `infoUrl`, no links, no edit path. The owner sees full detail.

Two deliberate departures from the general redaction rules, both reconfirmed for this slice:

- **Travel entries give anonymous viewers day granularity only.** A private social event shows
  a clock time — *less* redacted than a flight — because the point is to convey unavailability,
  which a day block doesn't.
- **This is the one redacted output that keeps a `ZonedTimestamp`.** `ZonedTimeTag`'s
  `datetime="<UTC instant>"` attribute is fine here (the time is public by this decision),
  unlike on FLIGHT/TRAIN/LODGING where that attribute is the leak the rule exists to prevent.

## Naming (proposed, confirm)

`PrivateEvent` throughout: `EntryKind.PRIVATE_EVENT`, `PrivateEventId`, `PlanPrivateEvent`
command, `PrivateEventPlanned` event, `PlanPrivateEventContext`. (The 2026-08-07 note sketched
`PRIVATE_SOCIAL`; `PRIVATE_EVENT` reads better with the command/event names. Easy to swap.)

## Slice — files to create

Mirrors the gathering slice (`Plan Gathering`):

**domain/**
- `PrivateEventId` — id wrapper (copy `GatheringId`).
- `PlanPrivateEventCommand implements DomainCommand<PlanPrivateEventContext>` — same two rules
  as `PlanGatheringCommand`: start must be on a day after `now` (in the event's own zone), end
  after start. Reuse `GatheringDateNotInFuture` / `InvalidGatheringTimeRange` or add private
  twins (proposed: reuse, they're generic messages).
- `PrivateEventPlanned implements Event` — `id, title, venueName, location, startsAt, endsAt`.
  **No `speaking`, no `infoUrl`** (both are public-event concepts). `venueName` normalized to
  `""` per the no-null-Strings rule.
- `PlanPrivateEventContext(Instant now) implements DecisionContext`.

**application/**
- `EntryKind.PRIVATE_EVENT` — add to the enum. Placement sets lane order in `CalendarViewBuilder`;
  proposed just after `GATHERING`.
- `PrivateEventPlanning` — app service, ctor `(CommandExecutor, LocationZoneResolver)`, exactly
  like `GatheringPlanning`.
- `PlanPrivateEventHandler` — request → command, resolving the zone (copy `PlanGatheringHandler`).
- `PrivateEventCalendarProjector implements EventStreamConsumer` — builds the **owner** (full)
  `CalendarEntry`: `mainTitle = title`, subtitle `[venue, city/country, Range(times)]`.
- `CalendarEntryRedactor` — new `case PRIVATE_EVENT` branch producing `Busy` + city/country +
  time range, dropping title/venue/editPath. **This is the one branch that keeps a
  `ZonedTimestamp`.** (See "rendering detail #1" below.)
- `CalendarAggregator` — inject the new projector and add its `entries()` (one-line changes to
  ctor + `allEntries()`).

**web/**
- `PlanPrivateEventRequest` — form-backing bean (copy `PlanGatheringRequest`, drop `speaking`
  and `infoUrl`; keep date + two `LocalTime`s + address + `zone`).
- `PlanPrivateEventController` — GET/POST `/plan-private-event`, `?date=` seeding, `Instant.now(clock)`
  at the boundary (copy `PlanGatheringController`).
- `plan-private-event.html` — Thymeleaf form (copy `plan-gathering.html`, drop speaking/infoUrl,
  reuse the shared address paste-and-parse widget).

**infrastructure/**
- `EventSourcingConfig` — `@Bean`s for the projector + app service, register projector as an
  `EventStore` subscriber, and thread it into the `calendarAggregator` bean.
- `SecurityConfig` — matcher for `/plan-private-event` (OWNER; the plan/GET+POST form is
  owner-only like the other planning forms).
- `EventPayloadUpcaster` / event-type registry — register `PrivateEventPlanned` so it
  deserializes on restore (check how `GatheringPlanned` is registered).

**index.html**
- New nav card for "Plan a private event". **Ask Ted**: which group, icon (Font Awesome Pro
  fill SVG from the travel-icons row), and any background color.

## Two rendering details to resolve before coding

1. **Zone label on the redacted time ("EDT").** `SubtitleLine.Range` renders via `ZonedTimeTag`,
   which per `UtcDatetimeStoragePlan.md` decision 5 emits **no** zone label. The anonymous private
   view wants `7pm–10pm EDT`. Options: (a) a new `SubtitleLine` variant that renders a zone
   abbreviation, (b) a flag on `Range`, (c) a pre-formatted `SubtitleLine.Text` for the time —
   but that loses the `<time datetime>` element. Proposed: (a), a `Range` variant that shows the
   zone, used only by the redactor. Needs a look at `ZonedTimeTag` first.
2. **Owner-view time format** — the owner presumably sees the same `Range` the gathering owner
   sees (no zone label, matching every other entry). Confirm the zone label is anonymous-only.

## Tests (both redaction tiers are mandatory — CLAUDE.md rule 5)

- `PlanPrivateEventCommandTest` — future-day rule, end-after-start rule (domain unit).
- `PrivateEventCalendarProjectorTest` — owner entry shape (renderer unit).
- `CalendarEntryRedactorTest` — asserts title/venue/street/editPath are **gone**, city + time
  survive (unit, asserts on absence).
- `CalendarRedactionSecurityTest` — anonymous rendered body `doesNotContain` the title and venue
  through the real security chain; `contains` "Busy" + city.
- `AuthorizationMatrixTest` — new `/plan-private-event` row (GET+POST OWNER, anonymous denied).
- `@WebMvcTest` for `PlanPrivateEventController` — Thymeleaf renders, `?date=` seeds, POST with
  csrf redirects (CLAUDE.md: every Thymeleaf endpoint needs one).
- Every new/changed test mutation-verified per the standard procedure.

## Refactoring folded into this slice (Ted, 2026-08-12: A + B + C)

The private event is the **third** "plan a dated venue event" slice (gathering, tentative
conference, private event), so extraction is rule-of-three, not speculative. Ted's constraint:
reduce duplication **without introducing too much coupling** — so pure/composed primitives are
in, inheritance-based base classes are out.

**Already extracted — reuse, don't reinvent:**
- `VenueZone` — "explicit `CommonZone` wins, else resolve the address" — already shared by the
  gathering + conference handlers. The new handler uses it directly.
- `HotelStayRequest` — a read-only **interface** shared by book/change request beans, concrete
  beans keep their setters (Spring binding + Jackson target concrete setters). This is the
  established "share request shape without a base class" pattern.
- `HotelHandler` — book + change folded into one handler reading through the interface.

**A — Composable calendar primitives:**
- `Address.cityCountry()` — one method on the `Address` record; removes the city/country
  formatting copied in `GatheringCalendarProjector`, `ConferenceCalendarProjector`, and the new
  projector. Zero new type, zero coupling.
- A small `EventCalendarSubtitle` collaborator building `[venue?, city, Range(times)]`, used by
  the gathering **and** private-event projectors (gathering refactored onto it, mutation-verified).
- **Redactor untouched:** these build only the *owner* (unredacted) subtitle. `CalendarEntryRedactor`
  stays per-kind, deny-by-default. A shared builder must never become a path redaction flows through.

**B — `ProjectorBootstrapper`** (independent infra win; `Refactoring_Opportunities.md` #1):
- Collapse the `new / eventStore.subscribe(p) / p.handle(eventStore.findAll())` triple (11 beans in
  `EventSourcingConfig`) into one `register(...)` call. Touches only `EventSourcingConfig`; no slice
  coupling; unrelated to redaction. Can land as its own commit before or after the slice.

**C — Shared plan-form pipeline** (web-only, medium coupling):
- First fold `HotelHandler.resolveZone` onto `VenueZone` (kills the inline re-implementation).
- A `VenueEventRequest` read-only interface (the `HotelStayRequest` analog) for the plan-event
  request beans: address fields + `zone` + `getLocation()`. Concrete beans keep setters.
- Settle `Address` construction in **one** place (`getLocation()` on the interface's default, or a
  single handler helper) rather than three.

**Rejected (too much coupling — Ted's caveat):** `BaseCalendarProjector<E,ID>` template-method
base class (`Refactoring_Opportunities.md` #2) and any shared base plan-command or base request
bean. Duplicated *structure* across independent slices is cheaper than the wrong shared parent.

**Broader sweep — noted, not in this slice** (`Refactoring_Opportunities.md` owns these):
projector base class (rejected above), booked-X simple projectors, booked-X + itinerary Thymeleaf
fragments. Revisit separately; none block the private-event slice.

### Build order

1. **B** `ProjectorBootstrapper` (isolated, its own commit).
2. **A** `Address.cityCountry()` + `EventCalendarSubtitle`, refactor gathering + conference
   projectors onto them (proves reuse before the new slice consumes them).
3. **C** `HotelHandler` → `VenueZone`; introduce `VenueEventRequest`.
4. The private-event slice itself, composing 1–3.

## Deferred to follow-ups (NOT in this slice unless Ted says so)

To keep the first slice focused on the leak it closes:

- **Change Private Event** (edit flow) — parallels `ChangeGathering`. A private event can be
  entered but not yet edited.
- **Planned list view** (`/planned-private-events`) with the FUTURE/ALL toggle — so the owner
  can see upcoming private events in a list, not only on the calendar.
- **Itinerary entry** — a `PrivateEvent…ItineraryEntry` so it appears in `/itinerary`.

These are real gaps; the calendar + redaction slice is the minimum that closes the leak and
lets Ted enter a private event safely. Ted to confirm whether any of these belong in v1.
