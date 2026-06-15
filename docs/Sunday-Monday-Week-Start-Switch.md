# Sunday / Monday Week-Start Switch

A plan for letting the confirmed calendar (`/calendar`) start its weeks on **Monday**
instead of the hardcoded **Sunday**, controlled by a user-facing toggle.

## Current behavior

The calendar is server-rendered static HTML (j2html). The week-start day is hardcoded
to Sunday in several places:

- `ConfirmedCalendarRenderer.render(...)` computes the date range and calls
  `CalendarViewBuilder.render(...)`.
- `CalendarViewBuilder.render(...)`:
  - `gridStart = rangeStart.with(previousOrSame(DayOfWeek.SUNDAY))`
  - `gridEnd = rangeEnd.with(nextOrSame(DayOfWeek.SATURDAY))`
  - loops week by week, stepping 7 days, calling `renderWeek(sunday, sunday.plusDays(6), ...)`.
  - the header row is a hardcoded list: `div("Sunday") … div("Saturday")`.
- All per-week column math (`segmentColumns`, `intersectsWeek`) is **relative to the
  week-start date passed in**, so it generalizes for free once the start date is correct.
- `formatDayLabel` and the month-tint / `is-month-start` logic are purely date-driven and
  are unaffected by which weekday starts the row.

## Target behavior

A toggle on the calendar page switches weeks between Sunday-first and Monday-first. The
grid, header labels, and entry layout all shift accordingly. The month-start borders/tints
and the past/today shading remain correct because they are date-driven.

## Implementation

### 1. Thread a `DayOfWeek weekStart` through the render path

Add a `DayOfWeek weekStart` parameter to:

- `CalendarController.getCalendar(...)` — parsed from the request (see step 5).
- `ConfirmedCalendarRenderer.render(rawEntries, today, isPublicUser, weekStart)`.
- `CalendarViewBuilder.render(entries, rangeStart, rangeEnd, today, isPublicUser, weekStart)`.
- `renderWeek(...)` — receives the concrete week-start date per week.

Default everywhere is `DayOfWeek.SUNDAY` to preserve current behavior.

### 2. `CalendarViewBuilder` changes

- Grid snapping:
  - `gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(weekStart))`
  - `weekEndDay = weekStart.minus(1)` (the day before the start = the week-end day)
  - `gridEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(weekEndDay))`
- The week loop already steps by 7 days from `gridStart`; no change needed.
- Rename the `sunday` / `saturday` parameters to `weekStartDate` / `weekEndDate`
  (currently `sunday`, `sunday.plusDays(6)`). All downstream column math
  (`segmentColumns`, `intersectsWeek`, `dayStateClass`, lane allocation) already keys off
  the passed-in start date, so only the names change.

### 3. Header row

Replace the hardcoded `div("Sunday") … div("Saturday")` with seven labels generated
starting from `weekStart`, e.g. iterate `weekStart` for 7 days and emit
`day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)` — or rotate the existing fixed list
so it begins at `weekStart`.

### 4. Unaffected logic (verify, don't change)

- `formatDayLabel` (month/year labels) — date-driven.
- `is-month-start` borders and month-tint classes — date-driven.
- `is-past` / `is-today` shading — date-driven.

### 5. Carrying the choice through the request

**First cut — query parameter.** `/calendar?weekStart=monday`. The controller parses it
(defaulting to `SUNDAY` when absent/invalid) and passes the `DayOfWeek` down. The UI toggle
is a small link/button that reloads the page with or without the param.

**Optional persistence.** Store the preference in a cookie or `localStorage` so it sticks
across visits. Cookie = a small server round-trip read in the controller; `localStorage` =
a few lines of client JS that rewrite the link's href. Query param alone is the cheapest
starting point and can be layered with persistence later.

### 6. UI toggle

A small, unobtrusive control on the calendar page (consistent with the rarely-used
"expand all past weeks" toggle): "Week starts: Sunday | Monday", where the inactive option
links to `/calendar` with the appropriate `weekStart` value.

## Tests

- `CalendarViewBuilderTest` and `ConfirmedCalendarRendererTest` currently assume Sunday-first
  (e.g. `>May 24, 2026<` is the Sunday before May 28). Keep them green by defaulting the
  existing calls to `SUNDAY` (an overload or explicit argument).
- Add Monday-start cases:
  - header begins with "Monday" and ends with "Sunday".
  - grid snaps to Monday `gridStart` / Sunday `gridEnd` for a known range.
  - an entry's column placement shifts by one vs. the Sunday-first layout.
- `CalendarWebIntegrationTest` — add a case asserting `?weekStart=monday` produces a
  Monday-first header (and that the default / absent param stays Sunday-first).

## Effort & blast radius

Mechanical but touches several signatures (controller → renderer → builder) plus the few
hardcoded "Sunday/Saturday" spots. Entirely contained to the `web` package — no domain,
event, or projection changes. The column-layout math already generalizes, which is what
keeps this from being a layout rewrite.