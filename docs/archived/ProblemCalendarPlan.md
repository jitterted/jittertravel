# Problem Calendar Plan

**Status:** `done`. Slices 1–3 shipped (2026-08-19), i.e. the Bed lane, the 1.5 context backdrop and
the Travel lane; Ted reviewed the live calendar after the schedule-problems rewrite and it reads
correctly (2026-08-20). **Slice 5 (fix links) shipped 2026-08-20** — suite green at 1217 (+ 48 js).
**Slice 4 (clash markers) shipped 2026-08-20** — suite green at 1246 (+ 48 js). Every problem the
report can detect now has a band; the open questions at the foot are the only thing left.

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
   "clear this conflict" action reachable from the band. Designed below.
5. **Fix links.** Each card and each band links to the form that fixes it, prefilled. This is what
   turns the report into an action list. Designed below.

**Slice 5 ships before slice 4** (Ted, 2026-08-20): "easily fixing the problems is most important."
The numbers stay as they are — they are identities now, referenced from `../Backlog.md` and from
`ProblemBand`'s javadoc — but the order of work is 5, then 4.

## Slice 5 — Fix links (both views)

Scope is wider than the calendar: a fix link belongs on the **list card** as much as on the band,
and the list is still the default view. One mapping feeds both, so the two views can never offer
different fixes for the same problem.

### F1 — One mapping, two consumers

`ProblemFix(String label, String href)` in `web`, with `ProblemFix.forProblem(ScheduleProblem)`
returning `List<ProblemFix>` — an exhaustive switch over the sealed `ScheduleProblem`, exactly like
`ProblemBand.from`, so a new problem type cannot be added without deciding how it gets fixed.

- `ScheduleProblemsRenderer` puts **every** fix in the card's `Fix ▾` menu, at the card foot, where
  the `.clear-link` on city-conflict cards already sits — same slot on every card, whatever the
  problem type (F9).
- `ProblemBand` carries `List<ProblemFix> fixes`, and the band is the menu's `<summary>` (F9). A
  band opens its menu only from its **first** week segment; a continuation segment is inert,
  because clicking the middle of a run reads as clicking that day.

Two real consumers exist on day one, so this is not abstraction ahead of a second user.

### F2 — What fixes what

| problem | fixes, in order |
|---|---|
| `MissingHotel` | **Book hotel** → `/book-hotel?city=…&checkIn=…&checkOut=…` |
| `MissingTravel` | **Book flight** → `/book-flight?fromCity=…&toCity=…&date=…`, then **Book train** → `/book-train?fromCity=…&toCity=…&date=…` |
| `DuplicateHotel` | one **Cancel "<hotel name>"** per stay → `/booked-hotels/{id}/cancel` |
| `DifferentCityConflict` | **Clear this conflict** → the existing `/clear-conflict?…` URL |
| `SchedulingConflict` | none yet — see F6 |

Flight is listed before train because it is the common case in Ted's data; both are offered,
because a Frankfurt→Leipzig gap is a train and guessing wrong costs a page load. The duplicate's
fixes are **one link per stay**, not a single "cancel the redundant one": which room to keep is
Ted's call, and `BookingIntent` (which rides along on `DuplicateStay` for exactly this reason) is
displayed beside them rather than acted on. `/booked-hotels/{id}/cancel` is an existing gated page
with its own confirmation, so the link navigates — it never POSTs from the report.

The `/clear-conflict` URL builder moves out of `ScheduleProblemsRenderer` into `ProblemFix`,
unchanged, along with its `URLEncoder` helper. That move is what lets slice 4 put the same action
on the band.

**A missing travel gap has more than two answers** (Ted, 2026-08-20): flight, train, and a ground
transfer — the taxi from the airport, the subway to the venue. So the fix list is *open-ended by
nature* for at least one problem type, and the presentation cannot assume "one link, maybe two".
That is what F9 settles.

The third answer now has a home: **`docs/archived/GroundTransferPlan.md`** (Ted, 2026-08-20) — an
`EntryKind.GROUND_TRANSFER` with its own event, its own redaction branch, and a `Movement` case in
`ScheduleGapProjector`, so a transfer *closes* the gap rather than silencing it. Its fix item is
**Add ground transfer** → `/plan-ground-transfer?date=…`, listed after train.

**Corrected 2026-08-20, when the transfer form actually shipped.** The paragraph here was written
before it existed and was wrong in three ways:

- There is **no `custom` token** — D12 dropped free text entirely, so an end is an airport or a
  booked hotel and nothing else.
- **Preselection was dropped.** D13 made the airport ends *flight legs*, so one `airport:DEN` value
  can belong to several options (two trips through DEN). Preselecting by value would silently
  highlight the first — picking a trip for Ted. That is exactly what F4's own rule forbids: a wrong
  preselection is worse than an empty one.
- D14 changed what brings options into range at all (today-or-later, per endpoint zone).

So the transfer fix link carries the **date alone** — `/plan-ground-transfer?date=…` — which is
what populates the option lists, and Ted picks the ends. No cities ride in its query string.

Sequencing: the two slices are independent, and the cost of doing fix links first is **one added
item in one exhaustive switch** when ground transfer lands. Ground transfer first is still the
better order — it makes real gaps closable, and then the menu ships complete.

### F9 — More than one fix means a menu, and the menu is the same on every card

A row of links widens with the number of answers, and the answers differ per problem type — which
means the fix affordance would sit in a different place, at a different width, on every card. That
is precisely what the "action affordances never move" rule in CLAUDE.md forbids.

So **every card and every band carries one control in one place — `Fix ▾` — and the answers live
inside it**, even when there is only one. A single-item menu costs one extra click and buys a
vocabulary that is identical on every row; a card that sometimes shows a bare link and sometimes a
menu button teaches nothing. The `SchedulingConflict` card renders the *same* control greyed, with
its reason (F6), rather than dropping it.

The pattern already exists: `CalendarRenderer`'s day menu is a `<details>`/`<summary>` popup
(`.day-menu`, `.day-menu-list`, `.day-menu-item`) with `DAY_MENU_SCRIPT` handling outside-click
dismissal, Escape, and no-stacking, proven by `CalendarDayMenuJsTest` in the js tier. This slice is
its **second user**, which is what justifies lifting the CSS and script out of `CalendarRenderer`
into a shared presentation collaborator rather than copying them. Copying a dismissal script is how
two menus end up behaving differently.

On the calendar, the band itself is the `<summary>` — the whole band is already the click target —
and the menu list floats over the grid the way the day menu floats over a day cell. The band gains
no visible chrome and no height, so week rows keep their present shape.

Consequences for the rest of the slice: `ProblemBand` carries `List<ProblemFix> fixes` rather than
`Optional<ProblemFix> primaryFix`, and the js tier gains a `ProblemFixMenuJsTest` extending
`JsBehaviorTest` — rendered HTML into `page.setContent`, no server — covering open, outside-click
close, Escape, and that opening one menu closes another. Per the standing gate, `./mvnw test
-Pjs-tests` runs before any push that touches this.

### F3 — Prefill extends the existing GET handlers; no new routes

`/book-hotel` and `/book-flight` already take `?date=` from the calendar day menu
(`BookHotelController:38`, `BookFlightController:51`). The new parameters join them:

- `/book-hotel`: `city`, `checkIn`, `checkOut` (dates, `@DateTimeFormat(iso = DATE)`), seeding the
  address city and the two `datetime-local` fields at the form's existing default clock times
  (15:00 / 11:00). `?date=` keeps working exactly as now.
- `/book-flight` and `/book-train`: `fromCity`, `toCity`, `date`.

Every parameter is `@RequestParam(required = false)` and every absent-value default stays what it
is today, so the index nav cards and the day-menu links are untouched — that is a regression test,
not a hope.

**No new path means no new `SecurityConfig` matcher and no new `AuthorizationMatrixTest` row**: the
matrix is keyed by path, and a query parameter cannot escape a path matcher (the same argument the
`?view=` decision above rests on). Worth stating because the deny-by-default route rule in
CLAUDE.md fires on new `@GetMapping`s, and this slice adds none. Nor is redaction in play: the
report is OWNER-only and every fix target is already an OWNER surface.

### F4 — City → airport is ambiguous, so the link carries cities and the controller resolves

**As built:** the `AirportCityResolver` bean landed with **four** injection sites, not two — the gap
projector, both ground-transfer collaborators, and `BookFlightController`. The two ground-transfer
ones were constructed inline when that slice shipped the day before; this slice folded all three
inline constructions into the one bean. Adding `soleAirportFor` also stopped the interface being
functional, which broke a lambda stub in `ScheduleGapProjectorTest`; it was spelled out as an
anonymous class rather than given a `default`, so a future resolver still has to decide.

`StaticAirportCityResolver`'s table is many-to-one — London is LHR/LGW/STN/LCY, New York is
JFK/EWR/LGA — so there is no city→code answer in general. Two consequences:

- The link carries `fromCity`/`toCity`, never a guessed code. Building the URL stays pure string
  work with no resolver dependency.
- `BookFlightController` takes `AirportCityResolver` as a constructor dependency and seeds the
  airport field **only where the city has exactly one code**; otherwise it leaves the field blank
  and seeds the dates. A wrong prefilled airport is worse than an empty one: Ted has to notice it
  to undo it.

That needs `Optional<String> soleAirportFor(String city)` on `AirportCityResolver`, implemented in
`StaticAirportCityResolver` as a reverse index built once, dropping every city with more than one
code — and an `AirportCityResolver` `@Bean` in `EventSourcingConfig`, which today constructs
`new StaticAirportCityResolver()` inline for the projector (`EventSourcingConfig:283`). One bean,
two injection sites.

`/book-train` needs none of this: `BookTrainRequest` already carries `departureCityName` and
`arrivalCityName`, so the gap's own cities go straight in. The train link is the cleanest prefill
in the slice, which is a fair argument for listing it beside the flight rather than behind it.

### F5 — The hotel form's zone is not prefilled

`MissingHotel` carries a city and two dates and no zone, because the night sweep's location map is
keyed city-only. Prefilling the zone means carrying a zone per night through `ScheduleTimeline` —
real work, for a dropdown. Ship city + dates; revisit if picking the zone turns out to be the
annoying part.

### F6 — `SchedulingConflict` gets a stated non-fix, not a silent one

Its two sides are names, cities and times with **no ids**, and either side may be a gathering or a
private event, so a link would need a kind+id reference the record does not carry. That is the same
cause-linking gap the context backdrop ran into, and it is adjacent to the tabled private-event
conflict work — out of scope here.

Per the affordance rule in CLAUDE.md, the card shows greyed, non-interactive **Fix** text with a
`title` saying why ("Editing a gathering from here arrives with cause-linking"), in the same slot
the other cards put their links — the `ConferencesRenderer.confirmSlot` pattern, an honest
presentation limit. On the calendar there is no slot vocabulary to keep: a scheduling-clash band is
simply not an anchor.

### F7 — Read-only mode needs no special case

`/book-flight`'s GET already redirects to `/read-only`, and `CommandExecutor` throws before any
write. A fix link in read-only mode lands on the read-only page, which is the correct answer.

### F8 — Tests

- `ProblemFixTest`: one case per problem type, asserting the **whole href** string, including the
  encoded city (`/book-hotel?city=Johannesberg&checkIn=2026-09-10&checkOut=2026-09-14`).
- `ScheduleProblemsRendererTest`: the card's menu carries the whole anchor for each fix, a
  travel card carries both the flight and train anchors, and the scheduling-conflict card carries
  the greyed span with its `title` and *no* anchor.
- `ProblemCalendarRendererTest`: the band is a `<summary>` whose menu holds the same hrefs, and a
  continuation segment holds none.
- `ProblemFixMenuJsTest` (js tier, extends `JsBehaviorTest`): open, outside-click close, Escape,
  and opening one menu closes another.
- `@WebMvcTest` per touched controller: the prefilled GET seeds the request object, and the
  parameterless GET is unchanged.
- `StaticAirportCityResolverTest`: `soleAirportFor("Frankfurt")` is FRA, `soleAirportFor("London")`
  is empty.
- Mutation-verify each, per standing practice.

## Slice 4 — Clash markers *(shipped 2026-08-20)*

### C1 — One lane, two colours

`Lane.CLASH` is appended after `TRAVEL` (the enum's javadoc still says "CLASH arrives with slice 3"
— stale, fix it in this slice). Both conflict types share the lane; the band class carries the
distinction, `pc-band--clash-city` (violet) and `pc-band--clash-scheduling` (red), matching the
list's column colours per "Colour follows the list view" above. The destructive-action red rule in
CLAUDE.md is not in play: nothing here deletes anything, and the list already chose these hues to
separate categories.

**As built:** the distinction needed somewhere to live, and a second `Lane` value was the wrong
place — two lanes would reserve a row per kind on every week. So `ProblemBand` now carries a
**`Marker`** (`BED`, `DUPLICATE`, `TRAVEL`, `CLASH_CITY`, `CLASH_SCHEDULING`) instead of a `Lane`,
each marker naming the lane it packs into and deriving its own CSS modifier from its name;
`lane()` stays as a derived accessor, so the packing code and its tests were untouched. Two markers
therefore share `Lane.CLASH` and its sub-rows, which is what C1 asked for.

`ProblemBand.from` also **stopped returning `Optional`**: it returned empty only for the lanes that
did not exist yet, and with this slice every problem type lands somewhere. The renderer's
`flatMap(Optional::stream)` went with it.

Wording (Ted, 2026-08-20, offered three ways): the existing band vocabulary won — a short kind word,
an em dash, then the two names, with the cities or times in the detail. `City clash — Lunch ·
dev2next` over `Denver vs Chicago`; `Clash — XP Day · Lunch` over `9:00 AM BST · 12:00 PM BST`.

### C2 — Placement

- `DifferentCityConflict` carries a single `date()` — a one-day band.
- `SchedulingConflict` carries **no single date on purpose**: each side has its own
  `ZonedTimestamp`s and city, and a San Francisco evening can overlap a Tokyo morning that falls on
  the next local date. So the band spans **min→max of the two sides' local dates**, the same
  argument (and the same inversion bug) as the travel band, and the detail line names both zones —
  `6:30 PM PDT · 10:00 AM JST` — so nobody subtracts two clocks that do not match.
  **As built:** min→max over **all four** timestamps (both starts *and* both ends), not just the
  two starts — an end can fall on a later local date than the other side's start, and taking a
  subset reintroduces exactly the inversion this rule exists to avoid.

### C3 — The clear action rides on the band

A city-conflict band's menu holds the `/clear-conflict` item, supplied by `ProblemFix` from slice
5. That is the whole of "the existing action reachable from the band" — no second URL builder, and
the list card and the band demonstrably link to the same place (a test asserts the two hrefs are
equal). A scheduling-clash band opens no menu, per F6.

### C4 — Tests

`ProblemBand` cases for both types (including the inverted-zone span), calendar-renderer assertions
on both modifier classes as whole class attributes, and the shared-href test above. Mutation-verify
each.

## Open

- ~~What the third answer to a travel gap actually is.~~ **Closed 2026-08-20**: Ted wrote
  `docs/archived/GroundTransferPlan.md` — the transfer is modelled as its own located entry kind, so it
  contributes a presence fact and *closes* the gap. The rejected alternative was dismissing the gap
  (a `DifferentCityConflictCleared` analogue), which is cheaper but a plaster: `MissingTravel`
  carries no ids, so a dismissal keyed to a city pair and two instants would have to lapse when
  either end moves, or a rescheduled flight leaves a real gap silently suppressed. See F2 for how
  its fix item is prefilled.
- Whether the calendar should eventually become the default view.
- Whether a day number should link to `/itinerary?date=`. It is a fix link, so it belongs with
  slice 5 — but as its own step: the day cell is not interactive today, and making it so is a
  change to the grid rather than to a band.

> **Archived 2026-08-21.** Slices 1–5 all shipped. The two questions immediately above were
> **lifted into `../Cleanup_Tasks.md`** so they stay findable; nothing is tracked from this file.
