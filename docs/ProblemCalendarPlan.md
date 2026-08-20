# Problem Calendar Plan

**Status:** slices 1, 1.5 and 2 shipped (2026-08-19). Slices 3–4 open.

A second view of the OWNER-only `/schedule-problems` report: the same problems, placed on a
week-row calendar instead of in four card columns. The list answers "what is wrong"; the calendar
answers "when is it wrong, and how does it sit against the rest of the trip" — which is the
question a gap actually raises.

## Why

`ScheduleProblem` already carries dates. `MissingHotel` gives `checkIn`/`checkOut`. `MissingTravel`
gives `arrivedAt`/`nextDepartureAt`. Every problem is a run of days. A list of cards hides that
shape: three separate "no hotel" cards in one city read as three problems, when on a calendar they
are one visible hole in a trip.

## Decisions

### One route, two views — `/schedule-problems?view=list|calendar`

Not a new route.

- `/schedule-problems` is already `hasRole("OWNER")` in `SecurityConfig`. A query parameter cannot
  escape a path matcher, so the calendar inherits that gate exactly. No new matcher, no new
  `AuthorizationMatrixTest` row is *required* — one is added anyway, on the `?view=calendar` URL,
  as cheap insurance against a future refactor that splits the route.
- One nav card, one nav link, one bookmark.
- `?view=` is read at the boundary (`ScheduleProblemsController`) and passed inward, the same way
  `?filter=` is on the list views.
- **Default stays `list`.** Existing links and the nav card keep their present meaning. An absent
  or unrecognized value falls back to `LIST`, matching `TimeView.fromParam`.

### Redaction is not part of this

The page is OWNER-only, so no redactor runs and no anonymous viewer can reach any of it. The
deny-by-default *route* rule is satisfied by reusing the already-gated path. Nothing here may ever
be linked from, or rendered into, an anonymous surface.

### The types stay separate from the public calendar

The problem calendar does **not** reuse `CalendarEntry`, `EntryKind`, `CalendarViewBuilder`, or
`CalendarRenderer`.

The reason is coupling, not redaction. `CalendarEntry` is shaped for the public calendar, and it
is about to change shape entirely: `docs/RendererVsProjectorResponsibilities.md` decision S2+E2
splits it into a core record plus a sealed `EntryDetails`. A problem band does not belong in that
refactor, and dragging it in would give the refactor a second audience to satisfy.

So the grid code is deliberately duplicated, at a smaller size. The two grids will diverge: the
public calendar has entry kinds, day menus, edit pencils, zone toggles, past-week collapsing and a
redactor; the problem calendar has none of those and never will.

New presentation types, all in `web`:

- `ProblemBand(Lane lane, LocalDate firstDay, LocalDate lastDay, String title, String detail)` —
  one problem as a run of days in one lane, with `ProblemBand.from(ScheduleProblem)` as the
  mapping. The mapping is an exhaustive switch over the sealed `ScheduleProblem`, so a new problem
  type cannot be added without deciding how it lands on the calendar.
- `ProblemBand.Lane` — the fixed lane order down each week.
- `ProblemCalendarViewBuilder` — bands to week-row grid markup.
- `ProblemCalendarRenderer` — the page: head, view-nav, selector, grid.
- `ProblemView` (`LIST`/`CALENDAR`, with `fromParam`) and `ProblemViewToggle` — the selector,
  shared by both renderers of this page.

### The selector

A segmented control under the `<h1>`, on both views: **List | Calendar**.

It is not `TimeFilterToggle`. That one renders a `TimeView` and writes `?filter=`; this one renders
a view mode and writes `?view=`. The two would diverge on their next change. Only the CSS is
shared: `site.css` gets `.view-toggle` grouped into the existing `.time-toggle` rules, so the two
controls cannot drift apart visually.

Signature note: `TimeFilterToggleConventionTest` discovers every public static `render(...)` in
`web` that takes **both** a `List` and a `TimeView`, and demands the time toggle. Neither new
renderer takes a `TimeView`, so neither is caught by that scan. Keep it that way — this page filters
by nothing; `ScheduleGapProjector.problems(now)` has already dropped every problem whose window has
closed.

### Lanes, not entry kinds

Each week row carries the day-label row plus lane sub-rows, in fixed `Lane` order:

1. **Bed** — an uncovered night, from `MissingHotel`.
2. **Travel** — an unbridged gap between two cities, from `MissingTravel`.
3. **Clash** — an overlap, from `SchedulingConflict` and `DifferentCityConflict` (slice 4).

Bands in the same lane that overlap in days stack into extra sub-rows, as entries do on the public
calendar. A band that crosses a week boundary renders one segment per week, squared off on the side
where it continues.

### Colour follows the list view

Bed bands take the list's `--missing-hotel` blue, travel bands its amber, clashes its red and
violet — each at 80% alpha, so the context behind shows through. The two views of the same report
must not disagree about what a colour means.

None of these is a destructive action, so the red/amber rule in CLAUDE.md is not in play here: the
list already chose these hues to separate *categories*, and the calendar keeps that mapping.

### The context backdrop (decided 2026-08-19, shipped as slice 1.5)

The first cut painted problems only. That was not enough: a gap without its cause does not say
*why* a bed is missing, and the exact dates of the conference behind it were nowhere on the page.

So the schedule's own contents are drawn **behind** the problem bands, in grey.

- **Source: `ScheduleGapProjector` gains a second read model**, `context()`, returning
  `ScheduleContext` — the sealed `Conference` / `Gathering` / `Travel` / `Stay`. This is the same
  state, computed in the same pass as the problems, deliberately. From a second source the two can
  disagree — a conference dropped in one model and not the other paints a gap with no cause, or a
  cause with no gap — and the whole point of showing them together is that they line up.
  `CalendarAggregator` was rejected as the source for that reason and because it would drag
  `CalendarEntry` back in.
- **Everything in the window, not only the implicated entry.** `ScheduleProblem` carries no
  reference to its cause (only `DifferentCityConflict` has ids), so cause-linking would mean the
  detector naming its causes as it detects. The window is narrow enough that nearly everything in
  it *is* a cause. Revisit at slice 4, where a fix link has to know which item it is fixing.
- **A backdrop, not a lane.** A context band spans the **full height** of its week's lanes, so a
  problem band sits visibly *inside* it. A band in its own lane would never overlap, and
  translucency would then mean nothing.
- **Problem bands are translucent** (80%), so the grey shows through them.
- **One grey for every kind.** Shades per kind would rebuild the entry-colour vocabulary of
  `/calendar` on a page that is not about that. The kind is named in the label.
- **Labels get their own lines.** Each context band reserves one line at the foot of the week —
  the lane block grows by one row per overlapping context band — so labels stack instead of
  colliding, and no label ever lands under a problem band. The label reads
  `dev2next, Chicago · Sep 14–18`: name, city, and the exact dates that the band's extent shows.
- Context is **not** clipped in the renderer: the window comes from the problems, and only the
  weeks inside it are drawn, so context outside it never reaches the page.

This is the "coverage painting" the first cut deferred, arriving under a different name. What it
does *not* do is paint days green: absence of a grey band is not a claim that a day is covered.

### Range

`problems(now)` has already dropped past problems, so there are no past weeks to collapse and no
"show past weeks" toggle.

- start: the earlier of today and the earliest band day (a stay can have begun already and still
  be missing a bed tonight)
- end: the later of today + 2 weeks and the last band day
- snapped out to Sunday…Saturday

`today` comes from the injected `Clock` plus the viewer's `viewerZone` cookie, resolved through
`ViewerTodayZone` — the same pair `CalendarController` uses. No production code reads the ambient
clock.

## Slices

1. **Bed lane.** `MissingHotel` bands, week grid, the selector on both views, tests. *(done)*
2. **Context backdrop** — slice 1.5, built before the Travel lane. `ScheduleContext` +
   `ScheduleGapProjector.context()`, `ContextBand`, the grey full-height backdrop and its stacked
   labels, translucent problem bands. *(done)*
3. **Travel lane.** `MissingTravel` bands. *(done)* Each end is read in **its own** zone: the band
   covers the span of both local days rather than running arrival→departure, because those two
   dates can come out in the wrong order (a Tokyo morning arrival, a San Francisco evening
   departure), which would render as nothing at all. The detail line names both zones —
   `Arrive 2:30 PM BST · depart 9:00 AM CEST` — so nobody subtracts two clocks that do not match.
4. **Clash markers.** `SchedulingConflict` and `DifferentCityConflict`, with the existing
   "clear this conflict" action reachable from the band.
5. **Fix links.** Each band links to the form that fixes it, prefilled: book a hotel with city and
   dates, book a flight with both cities. This is what turns the report into an action list.

## Open

- Whether the calendar should eventually become the default view.
- Whether a day number should link to `/itinerary?date=` (useful, but it is a fix-link decision —
  it belongs with slice 4, not before).
