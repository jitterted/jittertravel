# Quiet Week Runs — the far future stops being empty scrolling

**Status: BUILT AND REVERTED, 2026-08-31 — an exploration record, not shipped code.** Designed,
built, refined through three visual iterations, and then reverted the same day when Ted named the
actual problem:

> i wonder if i'm solving the wrong problem … i think i need a more zoomed out view — a year or more
> at a time — where i can get a sense of things and then click on a month to jump to the linear
> calendar. **then the gaps are irrelevant.**

That is the correct diagnosis and it retires this whole plan. Everything below solved "the linear
calendar is too long to scroll" — a real problem, but a symptom. The successor is
`YearOverviewPlan.md`.

**Two things here outlived the revert, and are why this doc is kept:**

- **D4, the away-day trap** — an empty week can still be a *banded* week, and any future feature
  that treats "no entries" as "nothing here" will hit it. That reasoning applies to the year view
  unchanged.
- **D5, three failed attempts at making one element visible**, and what finally worked. The lesson
  generalizes to any dense view: proportional *size* is the signal that scales, and a thing shaped
  like its neighbours will be read as one of them.

**One piece is worth re-landing on its own** — the sticky month band (D5b). It fixed a complaint
that has nothing to do with collapsing anything ("i completely lose what month it is for weeks that
have entries"), Ted liked it, and it went out with the revert only because it rode in on this
branch. Tracked in `../Cleanup_Tasks.md` under Open.

## The problem

Entering an event 8–10 months out extends `/calendar` to cover it: `CalendarRenderer` sets
`rangeEnd` from the **last entry's end + 5 days**, unbounded. Ten months is ~44 weeks, and almost
all of them hold nothing.

Each empty future week costs a day-label row (~40px) **plus** a deliberate empty lane band
(`--calendar-empty-band-min-height: 120px`, added so a near-term empty week reads as open space
rather than a thin strip of dates). About 40 empty weeks at ~160px each is **~6,400px** — six or
seven screenfuls of nothing between the things Ted actually wants to see.

Past empty weeks already collapse to their label row. The mechanism existed; it simply never
pointed forward.

## What shipped

Past a **two-month horizon**, a run of **two or more** weeks holding nothing collapses to a visible
break in the grid:

```
│ 14 │ 15 │ 16 │ 17 │ 18 │ 19 │ 20 │
└────┴────┴────┴────┴────┴────┴────┘
 ╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲
        SEP · OCT — 7 weeks
          nothing planned
 ╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲╲
┌────┬────┬────┬────┬────┬────┬────┐
│ 25 │ 26 │ 27 │ 28 │ 29 │ 30 │ 31 │
```

One strip per run, **spanning month boundaries** rather than breaking at them (Ted, 2026-08-31):
one strip compresses further than one per month would, and the label names the months so they stay
landmarks. Ten months goes to roughly **1,600px**, nearly all of it the near two months at full
detail.

`CalendarViewBuilder` gathers quiet weeks as it walks the grid and flushes the run when a week with
something in it — or the end of the grid — closes it. `QUIET_RUN_HORIZON` and `QUIET_RUN_MINIMUM`
carry the two numbers.

## D1. Why the horizon is two months, and why it is a horizon at all

The first objection to collapsing anything was that it hides the **day numbers**, and with them the
owner's tap-a-day disclosure menu — the surface CLAUDE.md calls the one a new `EntryKind` gets
forgotten on, and the place Ted starts.

That objection is **true near-term and false far out**, which is what the horizon encodes (Ted,
2026-08-31):

> for far-future (more than 2 months away) events, I don't want to scroll to a day and click a menu
> to add, I'm going directly to Plan whatever and type in the date(s). the collapsing is so that I
> can see more of what's important with less scrolling.

So inside two months the calendar is a thing you plan *into* by tapping a day, and nothing is
collapsed. Past it, the days are not what those weeks are for, and their only job is to say what is
coming.

## D2. Why the strip does not expand

It was first proposed as an accordion — click to expand a collapsed run. Rejected, and the reason
generalizes: **the past-weeks toggle works because it hides entries**, with day badges hinting at
them, so expanding pays. A quiet run hides only absence. Expanding it reveals nothing.

The only thing an expand would restore is the day cells, which D1 has just established are not
wanted out there. So the expand's whole purpose would have been to undo a collapse that was too
aggressive — creating the need it then satisfies. That is the "door in front of a door" rule in
CLAUDE.md, one step worse.

It is also two lines to add later: `.calendar-week--collapsed` already has the click handler. If
the strip ever turns out to need one, the mechanism is sitting there.

## D3. Why the minimum is two, not one

A single quiet week collapsed to a strip saves about 50px and costs the week its shape. The strip
only pays for itself once there are two weeks to replace. A lone quiet week is drawn as the ordinary
empty week it is, band and all.

## D4. Why "quiet" means no entries **and** no away days

The one that nearly shipped wrong. The first assumption was that an empty week cannot be an away
week — a trip's weeks carry a hotel or a flight, so they are never empty.

`ScheduleTimeline.walk()` fills the nights **between** two points. A trip flown out and back with no
hotel booked yet therefore bands every night in between, while the only *entries* are the two
flights. The weeks in the middle hold **no entries at all** and are striped turquoise.

Those stripes are the app saying Ted has nowhere to sleep. A strip drawn over them would erase the
warning **and** assert that nothing is planned — wrong on both counts, in exactly the case the away
band exists for. So `isQuiet` asks both questions, and
`anAwayWeekWithNoEntriesIsNotQuietAndKeepsItsBand` is the regression.

## D5. Three versions to make it visible, and what actually did it

**v1** was a thin grey centred row: `Sep 6 – Oct 24 · nothing planned`, ~30px, in the weekday
header's own light grey, flush in the stack like every other row.

> it's not at all obvious that there are so many missing weeks/months. I didn't even notice it first
> and thought it wasn't working. (Ted, 2026-08-31)

**v2** added a gap, a dashed edge, a grey hatch and a taller band. Still not enough:

> it's still not working for me because it still doesn't stand out enough visually … i'm fine with
> using a bit more vertical space that is proportional to the time being collapsed.

The common fault in both: **they were rows**, so they read as one more week. What finally worked was
to stop drawing a row and draw a **canyon the calendar falls into** — four mechanisms, no one of
which carries it alone:

- **Depth.** A vertical gradient, dark at both rims to a lit floor in the middle, plus two inset
  shadows just inside those rims. The eye reads that stack as a recess, so the page looks like it
  *drops away*.
- **A torn silhouette.** `::before`/`::after` bite triangular teeth out of the band in the page's own
  white. Nothing else on this calendar has a non-straight edge, so the shape alone identifies it.
- **Size proportional to what is missing.** `--quiet-weeks` comes from the builder, so a two-week
  gap is a notch and a ten-week gap is a chasm you scroll through. **This is the signal that
  scales** — the reader does not have to read the number to feel it. Clamped at both ends: a floor
  so a short run is still unmistakable, a ceiling so a year-long gap cannot push the calendar off
  the screen.
- **Strata.** Two hatch layers at opposing angles in different hues over the gradient. Cross-hatching
  in more than one colour reads as *material*; flat grey read as an empty row.

**On colour.** v2's write-up claimed the fix "is structural and textural, never hue", which
overstated it — Ted's correction: *"not sure why amber is an obvious move, there are lots of other
colors and other mechanisms"*, and then *"gradients, more of a mix of colors with the hatching
instead of just black and white"*. What is actually constrained is narrower: **amber is a problem
and red is destructive**, and the seven lane fills are spoken for too — a warm band would read as a
train or a transfer, a green one as lodging. Slate through steel to a lit periwinkle floor belongs
to no kind, which is what makes it available. Colour was never the wrong tool; grey was the wrong
colour.

The exact dates went away with v3 and are not missed: the weeks above and below the strip carry
their own dates, so the boundaries are legible from context.

**Verify a visual mechanism by looking at it.** Three rounds went out on reasoning alone. The
torn-edge geometry in particular is two 45° gradients per edge whose *union* is the tile minus one
pointing triangle — easy to get inverted on paper, and it was, in the first draft. Rendering the
real `CalendarRenderer` output to a file and screenshotting it headlessly settles in one step what
argument cannot. See "Checking it" below.

## D5b. The month band — a bug this work exposed rather than caused

Ted, in the same message: *"i completely lose what month it is for weeks that have entries."*

That is not about the strip. The only thing naming a month anywhere on `/calendar` was the day label
on the **1st**, so any week not containing a 1st — most of them — left a reader with no way to tell
September from October without counting. The alternating month tint is too faint to answer it and
was never meant to.

So `/calendar` now carries a **sticky month band**, one per month, parked under the weekday header
as the page scrolls. A week is filed under the month its **Sunday** falls in, so a straddling week
belongs to one month and not both; the alternative would put two bands between two adjacent weeks.

A quiet run gets **no band of its own** — it already names every month it swallowed, and the next
band appears at the first week that starts after it. That is the one place the two features meet.

## D6. Rejected alternatives

- **Graduated band heights** (120 → 80 → 40 → gone, by distance). Ted's own first proposal, and a
  real fisheye design. Dropped once the horizon in D1 was agreed: band height carries no
  information — empty is empty — and hard thresholds put two identical-looking empty weeks at
  different heights, which reads as a rendering bug. The arithmetic also favoured the collapse:
  ~2,240px against ~1,600px.
- **Collapse empty *months*.** Cannot be built as stated. The grid is Sunday→Saturday weeks; a month
  almost never starts on a Sunday, so an "empty month" shares its first and last week with a
  neighbour that is not empty. It reduces to "collapse the empty weeks inside it" — this plan, with
  a misleading name — unless the week grid is abandoned, which loses column registration.
- **Cap the range and link out to far trips.** A calendar that does not show a trip you have booked
  is a worse failure than a long scroll.
- **Month anchor rail** (`<a href="#m-2026-11">`, zero JS). Genuinely good when the page was
  ~2,200px; solving a problem that no longer exists at ~1,600px with the far future in four strips.
  Worth remembering if the page ever grows again.

## Redaction

No new disclosure, and it needed checking because this is `/calendar`. The strip is computed from
whatever entries *that viewer* has, and states **absence** — which the empty grid already stated,
at the same day granularity that is public by decision. Anonymous viewers get the same strip from
the public projection, built from their own entries; nothing private reaches the label, which
carries two dates and four words. `PublicCalendarProjector` is untouched.

## Tests

`CalendarViewBuilderTest`, five new cases: a far run spanning months becomes one strip and no weeks
survive it; a run crossing New Year names the months on both sides; a lone quiet week stays a week;
every week inside the two-month window survives (all eight of them); and D4's away week.
Mutation-verified — removing the away-day clause, moving the minimum to 3, and moving the horizon
to one month each fail exactly one test, and the intended one; collapsing `monthsSpanned` to the
first month, and adding one to the week count, each fail all three label assertions.

The away-week test's second claim is carried by the **count**: the strip after the banded week says
`2 weeks`, and four weeks are in range, so `doesNotContain("4 weeks")` is what proves no strip
spanned across it.

Three more cover D5b and the proportional height: every month gets one band above the first week
that *starts* in it (with the straddling week filed under the earlier month), a quiet run emits no
band of its own, and the run carries `--quiet-weeks` into the markup. Mutation-verified — filing a
week by its Saturday instead of its Sunday fails the first.

## Checking it

Nothing here can be asserted in a string. To look at a change to this band:

```
mvn -o compile
# javac + java a throwaway main that calls CalendarRenderer.render(...) and writes the HTML
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless --disable-gpu \
    --screenshot=shot.png --window-size=1100,3600 --hide-scrollbars file://…/calendar.html
```

Render **two runs of very different lengths in one page** — the proportional height is the main
mechanism, and it is the one thing a single screenshot cannot show. This is not "launch the app": no
server, no database, no auth, just the renderer's own output in a headless browser.

No JS, so no `js`-tier change. Three existing tests needed their `today` moved: the file pins
`TODAY = 2020-01-01` with 2026 ranges, which now puts their empty weeks past the horizon and leaves
no day cells to assert on. `NEAR_TODAY_MAY` / `NEAR_TODAY_DECEMBER` sit just in front of their
ranges instead, so nothing reads as past or today.

## Tail

Two follow-ups, both **lifted to `Cleanup_Tasks.md` (Deferred)** so this doc owns no open work:

- the near-term empty band, 120px → 80px — proposed here, neither taken nor refused;
- **a zoomed-out, year-long calendar view with less detail** — one compact row per month past some
  horizon, trips as marks. The all-in answer to a long calendar, sketched during this design and
  deliberately not built: a second renderer, with its own redaction decisions, and this plan removed
  the pressure that would have justified it. It would *replace* the quiet-run strip past its own
  horizon rather than sit alongside it.
