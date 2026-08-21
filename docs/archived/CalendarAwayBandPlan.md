# Calendar Away Band — see at a glance when Ted is out of town

**Status: SHIPPED — planned 2026-08-20 (`3fca9b6`), built straight through and committed
2026-08-21 (`d4a9a3f`).** Planned, reviewed against the code, and built in one sitting;
every question the review raised is decided below (decisions 2 and 5–9). One correction landed
during implementation — see "the return day, and how it is detected".

## The problem

On `/calendar` it is hard to quickly see when Ted is home and when he is out of town. The
entries carry all the information, but answering "am I away the week of the 14th?" means
reading flights, hotels, and events and mentally reconstructing the trip envelope.

## The design

A thick turquoise stripe along the bottom of the day-label cells for every day Ted is away
from home: it begins on the day he leaves a home city and ends on the day he returns to one.
Ted's original sketch, unembellished — `border-bottom: 4px solid` on `.day-label-cell`, and
nothing else (see decision 8 on why the end caps were dropped).

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
   codes, city names), and assembling "Ted is out of town that week" from the public calendar
   is trivial already — the band saves the reader the arithmetic, it does not hand them a new
   fact (Ted, 2026-08-20). Record this in CLAUDE.md's "Public by decision" list so a future
   reader doesn't "fix" it.

   Two mechanical notes for whoever implements it, neither of which changes the decision: the
   away days do not travel through `CalendarEntry`, so `CalendarEntryRedactor`'s compile-break
   safety net does **not** cover them. And the 14-night carry (below) can stripe days that hold
   no visible entry at all, which is the band working as intended, not a leak.
3. **Turquoise, not red-adjacent.** The first sketch's `mediumvioletred` bumps against both
   the "red = irreversible" colour vocabulary and the amber month-start border decoration
   (`--calendar-month-start-color: #b45309`). The stripe uses CSS `turquoise` (#40E0D0),
   as a new `--calendar-away-color` token.
4. **A flight crossing a day boundary counts its departure day as the first away day**
   (Ted, explicitly). A red-eye leaving SFO at 11 PM on the 3rd makes the 3rd an away day
   even though the walk's `locationByNight` still places that night's *last known city* at
   home — the in-transit-night rule below is what delivers this.
5. **The band is commitment-blind** (Ted, 2026-08-20). The timeline takes every
   `ConferencePlanned` whatever its `AttendanceCommitment`, every hotel stay whatever its
   `BookingIntent`, and every planned ground transfer. A `WATCHING` conference — the one the
   calendar labels "Maybe" — and a tentative hotel booking both produce a solid band. That is
   deliberate: if it is on the schedule, it counts as away. The band is not hedged to match
   the chip, and a future reader should not "fix" the two into agreement.
6. **A midnight-crossing leg counts as transit even with home at both ends** (Ted,
   2026-08-20). The transit clause is not narrowed to legs with an away endpoint: one rule,
   shared verbatim with the missing-hotel sweep, and the occasional two-day stripe over a
   late arrival home is accepted. See "the one wrinkle" under the rule below — it is a known,
   chosen behaviour, not a bug to fix later.
7. **An unfinished trip's band stops where the schedule stops accounting for him** (Ted,
   2026-08-20; **refined 2026-08-21**, see below). The `+1` day is earned, not automatic: a run
   gets it when the schedule holds *anything* on that day — a flight home (he returned) or a
   conference's closing afternoon and a hotel checkout (he is still there). Only a day nothing
   is recorded on goes unbanded, and the band simply stops there, which is itself a legible
   signal that the return is missing.

   **The refinement, and why.** As first shipped, the trailing day was banded only when a point
   on it placed Ted in a **home** city. Ted found the hole the same day, on his own calendar:
   two conferences with no travel booked yet — J-Fall in Ede (Nov 11–12) and Agile Testing Days
   in Potsdam (Nov 16–19) — banded Nov 11–18, stopping a day short of a conference that runs
   through the 19th. Nothing placed him home on the 19th, and the night of the 19th is never
   filled (it follows the last fact), so the closing afternoon fell through both rules. Decision
   7 was only ever meant to stop the band inventing a *homecoming*; it was never meant to
   un-band a day the schedule explicitly accounts for. Widening the test from "a home city that
   day" to "anything that day" fixes it without touching the case it was written for: the day
   after the last recorded fact is still unbanded.

   Note what the test may *not* become: banding the day of every away point would band a
   same-day round trip (its Portland points would stripe a day spent asleep at home), which
   decision-by-definition says must stay unbanded. The narrowing to "the day after an away
   night" is what keeps both true.
8. **No end caps: the bottom stripe is the whole band** (Ted, 2026-08-20). The sketch's
   departure/return caps are dropped. They bought a little legibility and cost real edge
   trouble — a 4px left border on a cell that has none (eating content box), a 4px right
   border thickening an existing 1px edge, and a same-specificity fight with the amber
   `.is-month-start` left border that source order alone would have decided. None of that is
   worth marking a boundary the ribbon's own start and end already show.
9. **The read model is a set of days, not a list of trips** (Ted, 2026-08-20). With the caps
   gone, nothing consumes a trip's boundaries — the renderer asks one question per cell, "is
   this date away?" — so `awayDays()` returns `Set<LocalDate>` and there is no `AwaySpan`, no
   run-merging, and no interval containment in the view builder. If something later wants a
   trip envelope (a "Sept 3–9" label, away-days-per-month, the band on `/schedule-problems`),
   runs get built then, from the same nights. Deliberately no abstraction before that second
   user.

## Deriving away days

### The definition (Ted, 2026-08-20)

**"Away from home" means sleeping away from home.** A trip exists exactly when at least one
night is spent somewhere other than a home city — in a hotel, at an event's city, or in
transit on a plane or train. The band's days are the days that border those nights: the day
each away run begins (departure) through the day after its last away night, when a return is
actually recorded (decision 7). Days are never away on their own; they are away because of
the nights around them. This is the
rule everything below derives from, and it is the same notion of "away" the missing-hotel
sweep already uses (a night at home or in transit needs no bed; every other night does).

### The rule, mechanically

A **night is away** when either:

- `locationByNight` places it in a city that `HomeCities` does not include, **or**
- the night is spent in transit (`ScheduleTimeline.inTransitOvernight` — a leg departs on
  or before that day and arrives after it). This is what makes decision 4 work: the
  red-eye's departure night is filled with the home city by the walk, but it is a transit
  night, so it counts as away.

This is **not quite** the complement of the nights `missingHotels()` skips, and the difference
is the one real trap in the derivation. `missingHotels()` skips
`homeCities.includes(city) || inTransitOvernight(night)`; the complement of that is
`!includes && !inTransit`, whereas the rule above is `!includes || inTransit` — the complement
**plus every transit night, home-to-home ones included**. The two reads use the same flag in
opposite directions: in `missingHotels()` "in transit" *suppresses a demand* (harmless if
over-applied), in `awayDays()` it *asserts a fact* (not harmless).

**The one wrinkle, accepted (decision 6):** a leg that crosses local midnight with home at
both ends — the ground transfer from SFO at 23:40 arriving in San Francisco at 00:15
(`GroundTransferPlanned` is a `Movement` like any other) — is a transit night, so the band
stripes two days on which Ted slept in his own bed. Ted chose to keep the rule shared rather
than special-case it. Do not "fix" this without asking him; it is cheaper than a second,
subtly different notion of transit.

**Every away night bands its own day, plus the trailing day it earns.** There is deliberately no
notion of a "trip" in the derivation (decision 9): a night away bands *its* day, and it also
bands the following day when — and only when — the schedule still accounts for him then. So a
run of away nights `[d1 .. dn]` comes out as band days `[d1 .. dn+1]` whenever anything is
recorded on `dn+1`, and `[d1 .. dn]` when the schedule has gone silent, without either case
being special-cased.

### The trailing day, and how it is detected

**Two corrections, both on the same test.**

*While implementing (2026-08-20).* The plan first proposed `locationByNight.containsKey(dn + 1)`,
reasoning that a run of away nights ends only when the next night is a home night, so the key is
present exactly then. That is wrong in the most common shape there is: `walk()` fills nights only
*between* points, so the night after a trip's **last** fact is never filled — and a trip's last
fact is usually the flight home. The map test would have silently dropped the return day from
every trip that ends the schedule.

*After Ted read his own November (2026-08-21).* Reading the **points** for a *home* city fixed
that but left the mirror-image hole: a trip that has not been booked yet ends at a conference,
not a flight, so nothing places him home on its closing day and that day went unbanded (decision
7 above has the worked example). The test is therefore not "did he come home" but "does the
schedule say anything at all":

```java
private boolean accountedForOn(LocalDate day) {
    return points.stream().anyMatch(point -> point.day().equals(day));
}
```

Each version is strictly wider than the last, and the widest is still narrow enough: it only ever
applies to the day *after an away night*, so a day trip — away points, no away night — stays
unbanded, and the day after the schedule's last fact stays unbanded too.

### Guards and edge cases

- **Empty `HomeCities` is inert: no away days at all.** With `jittertravel.home-cities` unset,
  `HomeCities.includes` is always false, which would otherwise classify *every* night as
  away and stripe the entire calendar. An empty instance must yield an empty set — one
  early return, plus a three-line `isEmpty()` on `HomeCities`.
  Note this is a **divergence** from `missingHotels()`, not an inherited property: with no
  home cities that sweep demands a bed for every night, while the band shows nothing. The
  band's silence is the right failure for a misconfiguration; say so in the code. Watch also
  `ScheduleGapProjector`'s one-arg convenience constructor, which defaults to an empty
  `HomeCities` — any wiring that reaches for it gets no band and no error.
- **Same-day round trip** (leave home in the morning, back the same evening): no away night,
  therefore **no band — by definition, not as a limitation**. Sleeping at home that night
  means the day was not away, however far the day trip went; the entries themselves are
  still visible on the calendar.
- **`TRIP_BREAK_NIGHTS` is inherited, but read `fillNights` before relying on it.** After a
  14-night quiet stretch the walk does not simply stop: it records **one** night at the last
  known city and returns. So the run ends one night *past* the last recorded fact. Nothing
  places Ted home there, so by decision 7 the band ends on that night's own day and no
  trailing day is added — the ribbon stops rather than claiming a homecoming.
- **The schedule never leaves a run open.** `walk()` fills nights only *between* points and
  deliberately demands nothing after the last one, so "a missing return flight leaves the
  nights away until something places Ted home" is only true up to the last thing on the
  schedule. A trip whose outbound flight is the *only* fact recorded therefore bands nothing
  beyond the night in the air, if there was one: no night after the arrival is filled, so
  nothing says he slept away. That matches `missingHotels()`, which demands no bed past the
  last fact either.

### Where it lives

The whole derivation is one method, and it is small enough to write out:

```java
Set<LocalDate> awayDays() {
    if (homeCities.isEmpty()) {
        return Set.of();
    }
    Set<LocalDate> days = new LinkedHashSet<>();
    for (Map.Entry<LocalDate, String> night : walk().locationByNight().entrySet()) {
        if (homeCities.includes(night.getValue()) && !inTransitOvernight(night.getKey())) {
            continue;
        }
        days.add(night.getKey());
        LocalDate dayAfter = night.getKey().plusDays(1);
        if (accountedForOn(dayAfter)) {   // the schedule still says where he is
            days.add(dayAfter);
        }
    }
    return days;
}
```

- `ScheduleTimeline` gains that package-private `awayDays()`, calling the same `walk()` the
  other reads call. To be precise about what that buys: `walk()` is a method, not a memo —
  `missingTravel()` and `missingHotels()` already re-walk, and this makes a third walk per
  batch. The walks agree because they run over the same immutable state, not because there is
  only one of them.
- `HomeCities` gains `isEmpty()`.
- **No new value type, and no run-building** (decision 9). Nothing sorts, nothing merges
  consecutive nights, and no interval arithmetic appears anywhere.
- `ScheduleGapProjector` exposes it as a third cached read model, `Set<LocalDate> awayDays()`,
  recomputed per handled batch alongside `problems()` and `context()` — O(1) on read, never
  re-derived, and always consistent with the problems computed from the same state. Cached in a
  `volatile` field like the other two, for the same reason (written on the append/replay
  thread, read on a web thread), and copied on the way out.
- **No `now` filter**, unlike `problems(Instant)`. The band's whole value is that it shows in
  collapsed past weeks, so a past cut would delete the feature.

## Rendering

### Data flow

`CalendarController` gains a `ScheduleGapProjector` constructor dependency (one new
dependency on one class) and passes `Set<LocalDate>` into `CalendarRenderer.render(...)`,
which hands it to `CalendarViewBuilder.render(...)`. The redaction boundary is untouched:
nothing is added to `CalendarEntry`, so `CalendarEntryRedactor` does not change, and the
same set is passed for every viewer (decision 2).

Mechanics, since both renderers are already overload-heavy:

- `CalendarRenderer.render` has four overloads and `CalendarViewBuilder.render` two. The set
  goes on the **widest** overload of each; the narrower ones pass `Set.of()`, so every existing
  caller and renderer test keeps working with no band.
- It threads down through `renderWeek` (already eight parameters) to `renderDayLabelCell`
  (already six). That is more parameter creep on two long-signature private methods; live with
  it rather than inventing a collaborator for one caller.

### Markup

`renderDayLabelCell` appends **one** class to the existing `day-label-cell` class list:
`is-away`, when `awayDays.contains(date)`. There is no `away-start`/`away-end` (decision 8) —
emitting classes no stylesheet reads would be dead markup, and the two ends of the ribbon are
already visible as the two ends of the ribbon.

Membership is per date, so a trip spanning a Saturday/Sunday week break just marks days in
both week rows and the stripe continues naturally into the next row.

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
```

That is the entire stylesheet change. Interactions, decided:

- The away stripe **replaces** the cell's normal 1px `border-bottom` on away days. Cells are
  `box-sizing: border-box` with `min-height: 40px`, and the grid row stretches all cells to
  one height, so the thicker border costs ~3px of content box on away cells — negligible.
- **Nothing touches the side edges** (decision 8), so the month-start decoration keeps its
  amber `border-left` unchallenged and the cells' existing 1px `border-right` is untouched.
  The specificity fight that caps would have started — `.day-label-cell.is-month-start` and a
  cap rule tie, leaving *source order* to decide, and the away block would naturally be
  appended last — never arises. Worth remembering if anyone reintroduces caps.
- `is-past` hatch and `is-today` tint are backgrounds; the border sits on top of both with
  no interaction.
- No sideways-scroll risk: the tracks are `minmax(0, 1fr)`, the cells are `border-box`, and a
  bottom border consumes no horizontal space at all.

## What this deliberately does not do

- **No band on the schedule-problems calendar** (`ProblemCalendarViewBuilder`). Different
  audience, different question. **Closed 2026-08-21: Ted does not want it there** — this is
  settled, not a follow-up, and nothing tracks it anywhere else.
- **No "home" marker.** Away is the exception; mark the exception.
- **No tinting of the whole day column** — rejected: fights the month-tint alternation and
  the past hatch, and reads heavy.
- **No dedicated away-ribbon lane row** — rejected: costs a grid row per week and disappears
  in collapsed weeks, losing the best property of the label-row stripe.
- **No departure/return end caps** — rejected (decision 8): the side borders cost more in edge
  interactions than the boundary marking was worth.

## Tests

Timeline/projector (extend the existing suites):

- Simple round trip: fly out, hotel nights, fly home → exactly the departure day through the
  return day, and neither neighbouring day.
- **Overnight outbound leg: the departure day is the first away day** (decision 4 — the
  red-eye case, pinned by name).
- Overnight return leg: the arrival day is the last away day.
- Two trips separated by at least one home night: the home day between them is **not** in the
  set (the case run-merging used to be responsible for, now just an absent key).
- Empty `HomeCities` → empty set (the everything-looks-away guard).
- Same-day round trip → no away day (pins the definition: no away night, no away day).
- Quiet stretch past `TRIP_BREAK_NIGHTS` — asserting the exact last away day, since
  `fillNights` records one night past the last fact.
- **A trip with no return booked ends on its last away night's day, not the day after**
  (decision 7). Its sibling — the same trip *with* the return flight — gains exactly one day.
  The pair is what makes the rule precise; either test alone passes for the wrong reason.
- **A midnight-crossing leg between two home locations stripes both days** (decision 6 — the
  23:40 SFO ground transfer). Pinned as *chosen* behaviour, with the reason in the test name,
  so a future reader meets the decision instead of a puzzling assertion.
- **A `WATCHING` conference and a tentative hotel booking both produce a band** (decision 5,
  pinned so nobody later "fixes" the band into agreeing with the Maybe chip).

View builder / renderer (per the whole-element assertion rules):

- An away day's label cell carries `is-away` in its full rendered `class="..."` attribute;
  a neighbouring home day's does not (`doesNotContain` on that day's full class attribute).
- Given a set of away days, the marked cells are exactly those days — the day either side is
  unmarked. With the caps gone, that boundary is the whole of the band's rendering contract,
  and the view builder has no date arithmetic of its own left to get wrong.
- The stripe renders on day-label cells of a collapsed past week (the survives-collapse
  property is a claim worth pinning).
- CSS assertion pairs: `contains("border-bottom: var(--calendar-away-border-width) solid
  var(--calendar-away-color)")` plus the token definitions, with a `doesNotContain` guard
  against the un-tokenized literal — and, per decision 8, a `doesNotContain` on
  `"border-left: var(--calendar-away-border-width)"` so caps cannot creep back unnoticed.

Controller and slice tests — **this is not a zero-cost dependency**:

- `CalendarController` gaining a constructor parameter breaks three test classes until each
  supplies the new collaborator: `CalendarControllerTest` (plain JUnit, constructs the
  controller directly → one more `@Mock`), `CalendarRedactionSecurityTest` and
  `CalendarWebIntegrationTest` (both `@WebMvcTest(CalendarController.class)` slices → a
  `@MockitoBean ScheduleGapProjector` stubbed to an empty set, or the context fails to
  start). The earlier draft of this plan claimed `CalendarRedactionSecurityTest` would pass
  untouched; it will not.
- One controller test that the away days actually reach the renderer — otherwise a controller
  that forgets to pass them renders a bandless calendar and every renderer test stays green.
- One `CalendarRedactionSecurityTest` case asserting the **anonymous** body carries the band
  markup. That is the real claim of decision 2, and the only thing that would catch a future
  change quietly gating the band behind a viewer check.

All mutation-verified, per standing practice.

## What shipped (2026-08-20)

Everything above, as written, with the return-day correction noted at the top.

- `HomeCities.isEmpty()`; `ScheduleTimeline.awayDays()` + `accountedForOn(LocalDate)`.
- `ScheduleGapProjector`: `cachedAwayDays` (volatile, recomputed per batch beside the other
  two read models) and `Set<LocalDate> awayDays()`.
- `CalendarController` takes `ScheduleGapProjector` and passes `awayDays()` through;
  `CalendarRenderer` and `CalendarViewBuilder` each gain a widest overload carrying the set,
  the narrower ones passing `Set.of()`.
- `--calendar-away-color` / `--calendar-away-border-width` tokens and the single
  `.day-label-cell.is-away` rule, declared above `.is-month-start`.
- Tests: `ScheduleTimelineTest.AwayDays` (11 cases, including Ted's two-conferences-no-travel
  November as `twoConferencesWithNoTravelBookedBandOneStripeCoveringBothAndTheDaysBetween`),
  `ScheduleGapProjectorTest.AwayDaysReadModel`
  (3), two `CalendarViewBuilderTest` cases, two `CalendarRendererTest` cases, one
  `CalendarControllerTest` case, one `CalendarRedactionSecurityTest` case; mock/`@MockitoBean`
  added to the three calendar test classes. Suite green at 1266 + 48 (js tier).
- CLAUDE.md's "Public by decision" list records the band, with the limit the argument rests on
  (it says *when*, never where or why).
