# Calendar Away Band — see at a glance when Ted is out of town

**Status: planned 2026-08-20, nothing built.**

## The problem

On `/calendar` it is hard to quickly see when Ted is home and when he is out of town. The
entries carry all the information, but answering "am I away the week of the 14th?" means
reading flights, hotels, and events and mentally reconstructing the trip envelope.

## The design

A thick turquoise stripe along the bottom of the day-label cells for every day Ted is away
from home: it begins on the day he leaves a home city and ends on the day he returns to one.
Ted's original sketch — `border-bottom: 4px solid` on `.day-label-cell` — plus end caps so a
trip's start and end are visible as such.

Why the day-label row is the right home for it:

- Adjacent cells join into a continuous ribbon, and it does not compete with the entry lanes.
- A border layers cleanly over the existing month tint, `is-past` hatch, and `is-today`
  column tint without touching any of them.
- **The day-label row is the one thing that survives week-collapse**, so the stripe keeps
  showing the travel rhythm even in collapsed past weeks, where the entries themselves are
  hidden.

## Decisions (settled with Ted, 2026-08-20)

1. **Derive "away" from the location timeline, not from flights.** "Begins when I fly out"
   breaks for trips that start with a train, or where the first leg is not yet booked.
   `ScheduleTimeline` already walks flights, trains, ground transfers, hotels, conferences,
   gatherings, and private events into `locationByNight` (where Ted is each night), and
   `HomeCities` knows which cities count as home. The band is derived from those — one
   source of location truth, the same one `/schedule-problems` reads.
2. **No redaction.** Every viewer sees the band, anonymous included. All the information
   needed to derive "not home" is already public by decision (per-day travel facts, airport
   codes, city names), so the band aggregates but reveals nothing new. Record this in
   CLAUDE.md's "Public by decision" list so a future reader doesn't "fix" it.
3. **Turquoise, not red-adjacent.** The first sketch's `mediumvioletred` bumps against both
   the "red = irreversible" colour vocabulary and the amber month-start border decoration
   (`--calendar-month-start-color: #b45309`). The stripe uses CSS `turquoise` (#40E0D0),
   as a new `--calendar-away-color` token.
4. **A flight crossing a day boundary counts its departure day as the first away day**
   (Ted, explicitly). A red-eye leaving SFO at 11 PM on the 3rd makes the 3rd an away day
   even though the walk's `locationByNight` still places that night's *last known city* at
   home — the in-transit-night rule below is what delivers this.

## Deriving away days

### The definition (Ted, 2026-08-20)

**"Away from home" means sleeping away from home.** A trip exists exactly when at least one
night is spent somewhere other than a home city — in a hotel, at an event's city, or in
transit on a plane or train. The band's days are the days that border those nights: the day
each away run begins (departure) through the day after its last away night (return). Days
are never away on their own; they are away because of the nights around them. This is the
rule everything below derives from, and it is the same notion of "away" the missing-hotel
sweep already uses (a night at home or in transit needs no bed; every other night does).

### The rule, mechanically

A **night is away** when either:

- `locationByNight` places it in a city that `HomeCities` does not include, **or**
- the night is spent in transit (`ScheduleTimeline.inTransitOvernight` — a leg departs on
  or before that day and arrives after it). This is what makes decision 4 work: the
  red-eye's departure night is filled with the home city by the walk, but it is a transit
  night, so it counts as away.

This is exactly the complement of the nights `missingHotels()` skips (home nights and
transit nights need no bed) — the two reads agree on what "away" means by construction.

**Contiguous away nights form a span.** A run of away nights `[d1 .. dn]` renders as band
days `[d1 .. dn+1]` inclusive: the first away night is the departure day, and the day after
the last away night is the return day (an overnight return leg's night is a transit night,
so it is already in the run and the arrival day still lands as `dn+1`).

### Guards and edge cases

- **Empty `HomeCities` is inert: no spans at all.** With `jittertravel.home-cities` unset,
  `HomeCities.includes` is always false, which would otherwise classify *every* night as
  away and stripe the entire calendar. An empty instance must yield an empty span list.
- **Same-day round trip** (leave home in the morning, back the same evening): no away night,
  therefore **no band — by definition, not as a limitation**. Sleeping at home that night
  means the day was not away, however far the day trip went; the entries themselves are
  still visible on the calendar.
- **`TRIP_BREAK_NIGHTS` is inherited.** The walk stops carrying last-known-location after a
  14-night quiet stretch, so an away run ends where the schedule goes quiet — same
  behaviour, same reasoning as missing-hotel detection, no new rule.
- **Gaps don't break the band.** A missing return flight leaves the nights away until
  something places Ted home; the band honestly shows him still away, which matches what
  `/schedule-problems` reports for the same nights.

### Where it lives

- `ScheduleTimeline` gains a package-private `awaySpans()` producing the runs from the same
  `walk()` the other reads use (one walk, consistent views of the same journey).
- A new public value type in `application`: `AwaySpan(LocalDate start, LocalDate end)`,
  both ends inclusive.
- `ScheduleGapProjector` exposes it as a third cached read model, `awaySpans()`, recomputed
  per handled batch alongside `problems()` and `context()` — O(1) on read, never re-derived,
  and always consistent with the problems computed from the same state.

## Rendering

### Data flow

`CalendarController` gains a `ScheduleGapProjector` constructor dependency (one new
dependency on one class) and passes `List<AwaySpan>` into `CalendarRenderer.render(...)`,
which hands it to `CalendarViewBuilder.render(...)`. The redaction boundary is untouched:
nothing is added to `CalendarEntry`, so `CalendarEntryRedactor` does not change, and the
spans are passed identically for every viewer (decision 2).

### Markup

`renderDayLabelCell` checks the date against the spans and appends classes to the existing
`day-label-cell` class list:

- `is-away` — any day inside a span
- `away-start` — the span's first day (departure)
- `away-end` — the span's last day (return)

Membership is by date, so a trip spanning a Saturday/Sunday week break just marks days in
both week rows — the stripe continues naturally, and the caps appear only at the true trip
ends, never at week edges. A single-day span (hypothetical) carries all three classes.

### CSS

New token in the `:root` block of `CalendarRenderer`:

```css
--calendar-away-color: turquoise;
--calendar-away-border-width: 4px;
```

```css
.day-label-cell.is-away {
    border-bottom: var(--calendar-away-border-width) solid var(--calendar-away-color);
}
.day-label-cell.away-start { border-left: var(--calendar-away-border-width) solid var(--calendar-away-color); }
.day-label-cell.away-end   { border-right: var(--calendar-away-border-width) solid var(--calendar-away-color); }
```

Interactions, decided:

- The away stripe **replaces** the cell's normal 1px `border-bottom` on away days. Cells are
  `box-sizing: border-box` with `min-height: 40px`, and the grid row stretches all cells to
  one height, so the thicker border costs ~3px of content box on away cells — negligible.
- **Month-start decoration wins the left edge.** When an `away-start` day is also
  `is-month-start`, the 3px amber `border-left` takes precedence (declare `.is-month-start`
  so it wins the cascade); the away-ness still reads from the bottom stripe. Calendar
  structure beats trip decoration.
- `is-past` hatch and `is-today` tint are backgrounds; the border sits on top of both with
  no interaction.

## What this deliberately does not do

- **No band on the schedule-problems calendar** (`ProblemCalendarViewBuilder`). Different
  audience, different question; can be a follow-up if Ted wants it there.
- **No "home" marker.** Away is the exception; mark the exception.
- **No tinting of the whole day column** — rejected: fights the month-tint alternation and
  the past hatch, and reads heavy.
- **No dedicated away-ribbon lane row** — rejected: costs a grid row per week and disappears
  in collapsed weeks, losing the best property of the label-row stripe.

## Tests

Timeline/projector (extend the existing suites):

- Simple round trip: fly out, hotel nights, fly home → one span, departure day through
  return day inclusive.
- **Overnight outbound leg: the departure day is the first away day** (decision 4 — the
  red-eye case, pinned by name).
- Overnight return leg: the arrival day is the last away day.
- Two trips separated by at least one home night → two spans, no bridge.
- Empty `HomeCities` → empty span list (the everything-looks-away guard).
- Same-day round trip → no span (pins the definition: no away night, no away day).
- Quiet stretch past `TRIP_BREAK_NIGHTS` ends the span.

View builder / renderer (per the whole-element assertion rules):

- An away day's label cell carries `is-away` in its full rendered `class="..."` attribute;
  a neighbouring home day's does not (`doesNotContain` on that day's full class attribute).
- `away-start` / `away-end` land on exactly the trip's first and last days.
- The stripe renders on day-label cells of a collapsed past week (the survives-collapse
  property is a claim worth pinning).
- CSS assertion pairs: `contains("border-bottom: var(--calendar-away-border-width) solid
  var(--calendar-away-color)")` plus the token definitions, with a `doesNotContain` guard
  against the un-tokenized literal.

All mutation-verified, per standing practice. No redaction-tier test is needed — nothing
viewer-dependent is added — but `CalendarRedactionSecurityTest` should keep passing
untouched, which itself demonstrates the band added no per-viewer branch.

## Follow-up (small, same change or next)

- Add one line to CLAUDE.md's "Public by decision" list: the away band (that Ted is out of
  town on a given day, as an aggregated stripe) is public, decided 2026-08-20.
