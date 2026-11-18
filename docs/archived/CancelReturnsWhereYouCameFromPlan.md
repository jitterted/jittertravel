# Cancel Returns Where You Came From Plan

Status: **shipped** (`4589824` and `492cdc2`, 2026-11-16; archived 2026-11-18). Follows
`CancelBinEverywherePlan.md`, whose bins made the gap common. Built test-first: `LinkOriginTest`,
the convention test, the slice cases and the renderer `href` assertions were written and seen
failing before any production change. The one thing it left unbuilt, the missing-hotel fix row
carrying its day, is in `../Cleanup_Tasks.md`.

**Extended to create and change forms** (`4823409`, 2026-11-17). The calendar day menu's "Add …"
items and the edit pencils on both surfaces carry `from`/`day` the same way the bins do, and every
create and change form returns through `LinkOrigin.returnTo(from, day, fallback)`. The hidden
inputs moved into one fragment, `fragments/link-origin.html`, which the cancel pages now use too.
The record of that change is in `../Cleanup_Tasks.md` (Done).

### As built — differences from the plan below

- **`FixOrigin` → `LinkOrigin`**, with `PROBLEM_LIST("problems-list")`,
  `PROBLEM_CALENDAR("problems-calendar")`, `ITINERARY("itinerary")` and the new
  `CALENDAR("calendar")`. One new record, `WayBack(href, place)`: `place` completes
  "Keep it — back to …" (`the calendar`, `the itinerary`, `schedule problems`, `the list`).
  `LinkOrigin.wayBack(from, day, fallback)` returns the one value a cancel controller uses for
  both the `wayBack` model attribute and its success redirect, so the two ways out cannot disagree.
- **The advice's model attribute `fixOrigin` was renamed `linkOrigin`** (nine templates and
  `ProblemContextFragmentConventionTest`), and it gained a sibling, `originDay`. That is
  `LinkOrigin.validDay(day)`, so a value that is not a date never reaches a hidden input.
- **The calendar bin's day is the entry's start day.** Owner actions are drawn only on an entry's
  own first segment, so that is the day the bin sits on; `/calendar?day=` scrolls to and
  highlights it without changing the grid.
- **The itinerary bin's day is the page's first column, not the bin's own day** (corrected
  2026-11-16, after Ted saw it). `/itinerary?date=` names the window's *first* day, so a
  third-column bin carrying its own day rebuilt the page starting there. "Keep it" then shifted
  all three columns left: the page moved although nothing had changed. Every bin now carries
  `ItineraryRenderer.windowStart`, and both ways out redraw the same three days. Pinned by
  `ItineraryRendererTest.aBinInTheThirdColumnCarriesTheFirstColumnsDateSoTheWayBackRedrawsTheSameDays`,
  which was seen failing first. **Not carried: `?tz=`** (the itinerary's time-zone toggle), so
  coming back from a non-default zone shows the default one. Left out on purpose (Ted,
  2026-11-16).
- **The private event's no-origin redirect changed**: it went to `/itinerary?date=<day>` while
  "Keep it" went to `/planned-private-events`. Now both go to the list (D2). The itinerary-day
  landing survives for the case it existed for: a bin clicked on the itinerary carries
  `from=itinerary&day=…`. The ground transfer has no list, so its fallback stays its departure
  day on the itinerary, for both ways out.
- **`cancel-flight-itinerary.html`'s departed re-render** ("Back to the list") reads the same
  `wayBack`, and the POST re-resolves it so the refusal keeps the origin it arrived with.
- **Controller claims went into the existing `@WebMvcTest` slices, not new plain unit tests.**
  All six slices already existed, so the plan's "too much ceremony" did not apply. And
  "Keep it" is a Thymeleaf claim that needs a render anyway. Each slice gained: a redirect with
  an origin, a "Keep it" with an origin, and a "Keep it" with none.
- **Not built: the bonus** (the `/itinerary` missing-hotel fix row carrying its day). It is not
  the same line of code after all. `ProblemFix` and the banner's Back link
  (`ProblemContextLookup`) would both have to carry a day. Left for a separate change.
- **Mutation-verified, each reverted:** a literal "Keep it" href in `cancel-train.html` and a
  dropped `day` input in `cancel-private-event.html` (convention test, both named); `backHref`
  ignoring the day (`LinkOriginTest`); the calendar bin without `linkFrom`
  (`CalendarViewBuilderTest` ×4 and `EveryCancelableKindShowsItsBinTest`); the itinerary hotel
  bin drawn with a fixed day (`ItineraryRendererTest`, the window-start case); the
  hotel redirect ignoring the origin; the ground-transfer GET ignoring it; the flight-itinerary
  refusal dropping it; and `validDay` echoing its input. Each was caught by its slice or unit
  test. A default PIT run on `LinkOrigin` killed 46 mutants with no survivors. The one with no
  coverage, `fromParam(null)`, now has a test.

## The problem

Every cancel page sends both of its ways out to one fixed place, whatever surface the link was
clicked on:

| Page | "Keep it" | After a successful cancel |
|---|---|---|
| `cancel-hotel.html` | `/booked-hotels/{id}` — **the edit page**, never the origin | `/booked-hotels` |
| `cancel-flight.html` | `/booked-flights` | `/booked-flights` |
| `cancel-train.html` | `/booked-trains` | `/booked-trains` |
| `cancel-private-event.html` | `/planned-private-events` | its list |
| `cancel-ground-transfer.html` | `/itinerary?date=<departure>` | its default |
| `cancel-flight-itinerary.html` | `/booked-flights` | `/booked-flights` |

The one exception is a schedule-problems fix link: it carries `?from=` (`FixOrigin`), and a
successful cancel goes back to the report. "Keep it" ignores even that.

Now that a bin sits on every cancelable entry on `/calendar` and `/itinerary`, "you came from
the calendar, so here is the hotel list" is the usual case, not an edge case.

## Decisions (Ted, 2026-11-15)

- **D1. All six cancel pages**, through one shared mechanism.
- **D2. Both ways out agree.** "Keep it" and the post-cancel redirect go to the same place: where
  the link was clicked.
- **D3. Extend `FixOrigin` and rename it**, so that one enum answers "where was this link
  clicked". Working name `LinkOrigin`.

## Design

### Origins

| Origin | `?from=` | Returns to | Label |
|---|---|---|---|
| `PROBLEM_LIST` | `list` | `/schedule-problems?view=list` | Back to schedule problems |
| `PROBLEM_CALENDAR` | `calendar` → see Q1 | `/schedule-problems?view=calendar` | Back to schedule problems |
| `ITINERARY` | `itinerary` | `/itinerary?date=<day>` (bare `/itinerary` with no day, as today) | back to the itinerary |
| **`CALENDAR`** (new) | see Q1 | `/calendar?day=<day>` | back to the calendar |

**No origin** (a link from a `/booked-*` list, or a typed URL) keeps each controller's own
fallback, its list. So the list pages need no change, and **"Keep it" stops pointing at the edit
page.**

### The day

Calendar and itinerary bins append `&day=<the day they were drawn on>`. That is the day of the
column or itinerary day, not the entry's start, so a check-out-day bin returns to the check-out
day. *(Superseded for the itinerary: see "As built". There it is the window's first day.)*

- The renderer adds both parameters to the projector's `cancelPath`. The projector's path stays
  bare, because where a link is drawn is the renderer's business.
- The day is parsed as a `LocalDate`, and an unparseable value is dropped, giving bare
  `/itinerary` or `/calendar`. A day is never put into a path as text, so a hand-edited value
  cannot become a redirect target. The origin is still resolved through the enum, never echoed.
- **Bonus:** the existing missing-hotel fix row on `/itinerary` can carry its day the same way,
  so its Back link lands on the day instead of the itinerary's default. That's in scope only
  because it's the same line of code.

### Carrying it through the page

`ProblemContextAdvice.fixOrigin` already exposes the raw `from` to every template, so a refused
submit's re-render keeps it. It gains a sibling for `day`. Each cancel template:

- carries `from` and `day` as hidden inputs, the way three of them already carry `from`;
- renders "Keep it" from a resolved origin. One model attribute with the href and the label,
  resolved once, not six templates each doing the string work.

Each cancel controller's success redirect becomes `LinkOrigin.returnTo(from, day).orElse(itsList)`.

### Tests

- `LinkOrigin` unit tests: each origin's href with a day and without one; a garbage day is
  dropped; an unknown `from` resolves through the enum and never becomes a path.
- A **convention test** in the style of `ProblemContextFragmentConventionTest`: discover every
  `cancel-*.html`, assert its "Keep it" link reads the resolved origin rather than a literal
  href, and assert it carries `from` and `day` through the POST. A seventh cancel page then cannot
  ship with a fixed target.
- One `@WebMvcTest` per controller's redirect is too much ceremony. Instead, a **plain controller
  unit test per cancel controller**, parameterized `origin → redirect`, in the
  "three claims, three tiers" shape.
- Renderer tests: the calendar and itinerary bins carry `from` and `day`, asserted as the whole
  `href` attribute.
- `EveryCancelableKindShowsItsBinTest` asserts `href="<path>"` exactly, so it must change to match
  `href="<path>?from=…"`. It should check the *path* and leave the query string to the renderer
  tests. One claim per test.

## Open questions

- **Q1 — decided (a), Ted 2026-11-15.** Which values for `?from=`? `calendar` already means the problem calendar.
  - (a) **Rename the problem ones to `problems-list` / `problems-calendar`**, and use `calendar`
    for `/calendar`. Clearest. A bookmarked fix link with an old value would fall back as an
    unknown value does today (to the problem calendar), which is harmless.
  - (b) Keep `list` / `calendar` for problems, and give `/calendar` a different word (`month`?
    `cal`?). No rename, but a confusing pair forever.
  - Recommended: **(a)**.
