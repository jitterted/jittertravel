# Plan: `/planned-private-events` — the list view

> **Status: `shipped`** — written **and built** 2026-08-24. Every open question (Q1–Q4) was
> answered by Ted before code moved and folded into D1, D5, D10 and D11 below; the slice went in as
> written, with no deviations. Suite green on both tiers, and every new assertion mutation-verified
> in three rounds (see "What shipped"). This doc **owns** the
> "`/planned-private-events` list view" line that has sat in `Cleanup_Tasks.md` since 2026-08-21
> (lifted there from `archived/PrivateSocialEventPlan.md`); that entry becomes a pointer here.
> It is the sibling of `ChangePrivateEventPlan.md` and depends on nothing in it — D4 of that plan
> deliberately kept the bare `/planned-private-events` path free for this.

## Context

A private event is the only planned kind with **no list page**. It can be entered
(`/plan-private-event`, shipped 2026-08-13), cancelled (`/planned-private-events/{id}/cancel`,
shipped 2026-08-24), and seen on `/calendar` and `/itinerary` — and that is all.

The gap this closes is sharper than "one more list". `PrivateEventPlanned` carries a full
`Address`, and **no view reads `street`, `region` or `postalCode`**:

| Surface | What it shows |
|---|---|
| `/calendar` (owner) | title + `[venue?, "City, Country", start–end]` — `PrivateEventCalendarProjector.toEntry` |
| `/itinerary` | title, `venue · City, Country`, start–end — `ItineraryRenderer.renderPrivateEvent` |
| `/planned-private-events/{id}/cancel` | title, venue, city, country, start/end — identification only, on purpose (`PrivateEventDetailsView`) |

So the address Ted types into the plan form is written to the event log and never read back. On
`/planned-gatherings` the street *is* shown (`PlannedGatheringsRenderer.buildAddress`), and that
list is the model for this one throughout: **a private event is a gathering minus `speaking` and
`infoUrl`**, which is the same sentence `ChangePrivateEventPlan.md` D7 uses for the edit flow.

Nothing here is a redaction question. The route is OWNER-only (D8), and redaction is an
anonymous-`/calendar` concern — `PublicCalendarProjector` builds its `Busy` block from the event
directly and never touches any type in this plan.

---

## Decisions

**D1 — A dedicated `PlannedPrivateEventView` + `PlannedPrivateEventsProjector`, not a widened
`PrivateEventDetailsView`** (Ted, 2026-08-24).

`PrivateEventDetailsViewProjector` already holds every live private event keyed by id, so reusing
it looks like the frugal move: add `views(TimeView, Instant)`, add `street`/`region`/`postalCode`
to the record, implement `TemporalView`. Reject it, for two reasons:

1. **`PrivateEventDetailsView` is deliberately narrow, and its Javadoc says why**: the cancel page
   is a *recording* surface, so it carries "which evening is about to be removed, and nothing
   more" (CLAUDE.md, "A recording surface needs no decision-support information"). Widening it so
   a second, richer surface can share the map puts three fields on the cancel page's view that the
   cancel page must then not render — a value carried and stripped later, which is the shape this
   codebase keeps rejecting.
2. **The precedent is unanimous.** Every kind folds the same events into one read model per
   surface: `GroundTransferDetailsViewProjector` sits beside the transfer's calendar and itinerary
   projectors; `PlannedGatheringsProjector` sits beside `GatheringCalendarProjector`. Two
   `ConcurrentHashMap`s over the same stream is the established cost, and it is what keeps a
   surface's read model shaped by that surface.

**D2 — The view record and its `relevantUntil()`.** Mirrors `PlannedGatheringView` field for
field, minus the two public-event components:

```java
public record PlannedPrivateEventView(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        String street, String city, String region, String postalCode, String country,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt
) implements TemporalView {
    @Override public Instant relevantUntil() { return endsAt.utc(); }
}
```

`endsAt.utc()`, not `startsAt` — a dinner in progress is still upcoming, the same rule and the same
one-line reason `PlannedGatheringView` gives.

**D3 — The projector handles `PrivateEventPlanned` and `PrivateEventCancelled`, and cancellation is
a hard removal.** `PrivateEventPlanned` is a full snapshot, so it is a `put`; `PrivateEventCancelled`
is a `remove`. This is `ChangePrivateEventPlan.md` D1 applied to one more read model, and it is not
optional: `PrivateEventCancellationPropagationTest` is a **case per projector**, so the new
projector gets a case there in the same change (see Tests).

When `PrivateEventChanged` arrives (that plan's slice 2), it lands here as a second `put` — one
line, exactly as `GatheringChanged` does in `PlannedGatheringsProjector`.

Sorted by `startsAt().utc()` ascending, filtered with `timeView.includes(view, now)`.

**D4 — Four columns: When / Private Event / Venue / actions.** The gathering table minus its
Speaking column, which a private event has no concept of. Grid tracks `auto 2fr 2fr auto`, the
same subgrid arrangement (`.private-event-list` owning the tracks, header and rows inheriting via
`grid-template-columns: subgrid`), the same 640px collapse to one stacked column with per-cell
`.leg-label`s. Nothing capped, nothing scrolling sideways.

- **When** — `ZonedTimeTag.render(startsAt, "EEE, MMM d, yyyy")` over
  `start – end` in `"h:mm a"`. Venue-local wall clock as the text, UTC instant in the `datetime`
  attribute for the browser-zone upgrade, as everywhere else.
- **Private Event** — the title. No link: a private event has no `infoUrl`, and there is no detail
  page to point at (until slice 2, see D5).
- **Venue** — `venueName` on its own line when non-blank, then the full address built exactly as
  `PlannedGatheringsRenderer.buildAddress` builds it: `street, city, region postalCode, country`,
  blanks skipped. **This is the only place `street`, `region` and `postalCode` are ever read**, and
  it is the reason the page is worth building.
- **actions** — D5.

**D5 — The actions cell carries **Cancel** today, and slice 2's Edit is appended *below* it**
(Ted, 2026-08-24). Cancel is the one per-item action that exists, it is already OWNER-only and already
linked from the itinerary, and a list of things you cannot act on is a worse page. Rendered as a
text link (`Cancel`, href `/planned-private-events/{id}/cancel`) like the gathering's `Edit` — not
the itinerary's bin icon, which belongs to a card, and well under the three-link threshold that
would call for a menu.

**Cancel goes first in the cell so that it never moves.** The cell is a column flex; adding a link
above an existing one shifts the existing one down, and "action affordances never move" (CLAUDE.md)
is about exactly that. Reading order would prefer `Edit · Cancel`, so this is a real trade — but a
one-time shift of a *destructive-looking* control is the worse half of it.

**D6 — Empty states, per filter**, following the gathering's two-message shape:
`"No upcoming private events."` under FUTURE, `"No private events planned yet."` under ALL.

**D7 — Its own CSS block in its own renderer, copied from `PlannedGatheringsRenderer`.** That
makes six near-identical list-view CSS blocks in `web`, and extracting a shared one is *not* part
of this change — it is a refactor across five shipped pages and belongs in
`Refactoring_Opportunities.md`, where it can be judged on its own. "No abstraction before the
second user" cuts the other way once there are six, but not inside a slice that adds the sixth.

**D8 — OWNER-only, and the route is public until a matcher says otherwise.** Add
`"/planned-private-events"` to the **list** matcher group in `SecurityConfig` (the one already
holding `/booked-flights`, `/conferences`, `/planned-gatherings`) — **after** the per-item group,
where `/planned-private-events/*/cancel` already sits, because a single `*` matches one segment and
the ordering comment there depends on it. Add the `AuthorizationMatrixTest` row in the same change:
`OK / DENIED_HOME / LOGIN`, matching `/planned-gatherings`.

**D9 — Two navigation entries, both OWNER.** A `"Private Events"` link in `Page.viewNav`'s OWNER
bar (making it ten links — it flex-wraps, so length is a legibility question, not a layout one),
and a nav card on `index.html` in the **Bookings** group beside `Planned gatherings`, titled
"Planned private events" / "View private evenings out". *(Icon: D11.)*

**D10 — The cancel page's "Keep it" link comes back *here*, hard-coded** (Ted, 2026-08-24).
`cancel-private-event.html:117` currently reads
`Keep it — back to the itinerary` → `/itinerary?date=…`. It becomes
`Keep it — back to the list` → `/planned-private-events`, which is
**`decline-conference.html:113` verbatim** — that page has pointed its keep-link at `/conferences`
since it shipped, so this is an existing precedent rather than a new pattern. No `?from=`
mechanism: the destination is fixed, which is the whole point of hard-coding it.

Two consequences, both accepted:

- The Thymeleaf `#temporals.format(privateEvent.startsAt(), …)` call in that link **goes away** —
  the link no longer depends on the event's date at all.
- **The flow is now asymmetric, deliberately.** *Keeping* returns to the list; *cancelling* still
  redirects to `/itinerary?date=<the day it was on>`, and the three stale/already-cancelled paths
  in `CancelPrivateEventController` still redirect to `/itinerary`. Those four are untouched —
  the success redirect has its own stated reason ("landing back on the itinerary day it left is
  what shows the evening is gone from that day"), and it is a different question from where a
  *cancelled* click goes. If you want them pointed at the list too, that is a follow-up, not this
  slice.

**This makes the template edit part of *this* slice, not a follow-up.** The link would 403 until
the route and its `SecurityConfig` matcher exist (D8), so it ships in the same change.

**D11 — The icon is Font Awesome Pro `utensils`, the same SVG on both cards** (Ted, 2026-08-24,
answering "🍴"). It is a **fill-based FA Pro SVG from the travel-icons row**, per the standing nav
card rule — *not* an emoji character in the markup. The path is already in the tree: the
`/plan-private-event` card at `index.html:519` carries it, labelled a placeholder in
`Cleanup_Tasks.md` since the kind shipped. Copy that `<svg>` verbatim onto the new list card and
**close the placeholder line** — the placeholder turns out to have been the right icon.

Note this departs from the gathering precedent, where the plan card (`users`-adjacent) and the
`/planned-gatherings` card (`users`) differ. One icon for both private-event cards is the choice
here.

---

## Questions for Ted

**Answered 2026-08-24** — Q1: dedicated projector and view (D1). Q2: Cancel on the row, first
(D5). Q3: the keep-link comes back to this list, hard-coded (D10).

Q4: the icon is FA Pro **`utensils`** (D11).

**Nothing is open. The plan is ready to build.**

---

## Implementation order

Each step compiles and its test goes green before the next.

1. **`application/PlannedPrivateEventView.java`** — the record in D2, implementing `TemporalView`.
2. **`application/PlannedPrivateEventsProjector.java`** — `EventStreamConsumer`, `put` on
   `PrivateEventPlanned`, `remove` on `PrivateEventCancelled`, `views(TimeView, Instant)` sorted by
   `startsAt().utc()`. Copy `PlannedGatheringsProjector` and delete the two public-event arguments.
3. **`infrastructure/EventSourcingConfig`** — `@Bean` registering it through
   `bootstrapper.register(...)`, beside `privateEventDetailsViewProjector` (≈ line 379).
4. **`web/PlannedPrivateEventsRenderer.java`** — `public static String render(List<PlannedPrivateEventView>, TimeView)`.
   The signature matters: `TimeFilterToggleConventionTest` discovers list renderers *structurally*
   by it, so this shape is what makes the toggle guard cover the page automatically.
   `Page.head("Planned Private Events", CSS)`, `Page.viewNav(NavAudience.OWNER, "/planned-private-events")`,
   `h1`, `TimeFilterToggle.render("/planned-private-events", activeFilter)`, then the list or the
   empty state.
5. **`web/PlannedPrivateEventsController.java`** — I/O only: `TimeView.fromParam(filter)`,
   `Instant.now(clock)` from the **injected** `Clock`, `ResponseEntity` with
   `new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)`. A verbatim copy of
   `PlannedGatheringsController` with the names changed.
6. **`infrastructure/SecurityConfig`** — the matcher in D8.
7. **`web/Page.navLinks`** — the OWNER `NavLink` in D9.
8. **`templates/index.html`** — the nav card in D9.
9. **`templates/cancel-private-event.html`** — the keep-link in D10. Last, so the link never
   points at a route that does not exist yet.

## Tests

| Test | What it must pin |
|---|---|
| `PlannedPrivateEventsProjectorTest` (new) | planned events appear; a cancelled one is gone; FUTURE hides a finished evening and keeps one in progress; ALL shows both; sort order |
| `PrivateEventCancellationPropagationTest` (**edit**) | a case for the new projector — the file is one case *per projector* and its Javadoc says so |
| `PlannedPrivateEventsRendererTest` (new) | whole elements only: `contains("<title>Planned Private Events</title>")`, `contains("href=\"/planned-private-events/pe-123/cancel\"")`, the street rendered, both empty-state sentences, and `doesNotContain` for the title on an empty list |
| `PlannedPrivateEventsControllerTest` (new, `@WebMvcTest`) | 200, `text/html;charset=UTF-8`, `?filter=` reaching the projector. Import `WebTodayTestConfig` — a slice has no `Clock` bean |
| `AuthorizationMatrixTest` (**edit**) | the `/planned-private-events` row, `OK / DENIED_HOME / LOGIN` |
| `PageTest` (**edit**) | `ownerNavLinksToEveryViewPage` gains the href; `familyNavHasOnlyItineraryAndCalendarNotOwnerViews` gains a `doesNotContain("/planned-private-events")`. `anonymousNavHasOnlyTheHomeLink` already covers it via `doesNotContain("/planned-")` |
| `TimeFilterToggleConventionTest` | **no edit needed** — discovery is structural. Optionally add the class to its `contains(...)` sanity list, which only guards the scan itself |
| `CancelPrivateEventControllerTest` (**edit**) | the keep-link's new target, asserted as a whole attribute: `contains("href=\"/planned-private-events\"")` plus `doesNotContain("Keep it — back to the itinerary")`. Nothing pins that link today, which is exactly how it could be changed and nobody notice |

Mutation-verify every new assertion (standing practice): change the production string, watch it go
red, revert. The two that most need it are the street in the venue cell and the cancel href, both
of which a loose `contains` would pass for the wrong reason.

Run `./mvnw test` **and** `./mvnw test -Pjs-tests` before pushing — the page adds no JS, but the
gate is both.

## Not in this plan

- **The edit flow** — `ChangePrivateEventPlan.md` slice 2. This list adds the row its `Edit` link
  will hang off; it does not wait on it, and slice 2 does not wait on this.
- **Undo cancel** — the same standing gap `Future_Feature_Slices.md` records for hotels.
- **A shared list-view CSS extraction** — D7; belongs in `Refactoring_Opportunities.md`.
- **Anything on `/calendar` or `/itinerary`** — both already render everything their entry types
  carry. The address gap is closed here and nowhere else.

## What shipped

Built 2026-08-24, in the order above. Nine production files, seven test files (four new, three
edited). Nothing departed from the plan; three details are worth writing down.

**1. The row colour is slate, not the gathering's purple.** The plan said "copy
`PlannedGatheringsRenderer`" without saying what to do about its `#5b21b6`/`#6d28d9` accents. The
kind already has a colour — `ItineraryRenderer`'s `.entry-card--private-event` is `#475569` — so the
list wears that, and the copy stops at structure.

**2. The `SecurityConfig` comment above `/planned-private-events/*/cancel` was rewritten.** It used
to explain that the per-item matcher was "the whole gate" because *no* `/planned-private-events/*`
page existed. That is now half-wrong: the bare list path exists, so the reason the cancel entry
still needs its own line is the ordinary one — a single `*` matches one segment. The
`AuthorizationMatrixTest` comment beside its row got the same correction.

**3. Mutation-verification, three rounds, five mutations, all caught:**

| Mutation | Went red |
|---|---|
| projector ignores `PrivateEventCancelled` | `PlannedPrivateEventsProjectorTest.cancelledPrivateEventLeavesTheListEntirely` **and** `PrivateEventCancellationPropagationTest.thePlannedPrivateEventsListDropsTheCancelledEvent` |
| `buildAddress` drops the street | `PlannedPrivateEventsRendererTest.wholeStreetAddressIsRendered` |
| cancel href points elsewhere | `PlannedPrivateEventsRendererTest.eachRowLinksToItsCancelPage` |
| `/planned-private-events` removed from `SecurityConfig` | `AuthorizationMatrixTest.enforcesPolicy[28]` |
| keep-link reverted to the itinerary | `CancelPrivateEventControllerTest.keepLinkGoesBackToThePlannedPrivateEventsList` |

`TimeFilterToggleConventionTest` picked the new renderer up on its own, with no edit — the
structural discovery worked as designed. It was added to that test's `contains(...)` sanity list
anyway, which guards the scan rather than the page.

## Doc updates that are part of "done"

- `Cleanup_Tasks.md` — the `/planned-private-events` bullet becomes a pointer to this file. If Q4
  settles the icon, the **placeholder-icon** bullet closes in the same change.
- `Backlog.md` — index this plan with its status.
- This file's status header — `planned` → `shipped`, dated, with a "What shipped" note for anything
  the code says that the plan did not.
