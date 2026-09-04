# Year Overview — the whole calendar at a glance, as an overlay

**Status: SHIPPED 2026-09-01**, all four slices, in `YearOverview` (+ `Page.viewNav`'s trailing
slot, the month anchor ids on `CalendarViewBuilder`'s month-start day cells, and the CSS/script
wiring in `CalendarRenderer`). Tests: `YearOverviewTest` (25),
`YearOverviewAnchorConventionTest` (4), `YearOverviewJsTest` (11), `StickyLayerHeightsJsTest` (6),
plus two `PageTest` and two `CalendarRedactionSecurityTest` cases. Archived 2026-09-04.

## Read this before the plan below it: five things the build reversed

The body of this doc is the plan **as written**, including decisions the implementation then
overturned. Kept whole, because the arguments are why the reversals were right — but do not read a
paragraph below as describing the code.

1. **Three tints and one glyph, not seven of each.** The plan settles on the saturated
   `--entry-*-fg` lane colours for all seven kinds plus a per-kind glyph
   (🎤 👥 🍽️ ✈️ 🚆 🚕 🏨), and a `FLIGHT > TRAIN > CONFERENCE > …` priority. All of it went the day
   it shipped: a busy month was a wall of pictures in which nothing's *absence* was noticeable, and
   every travel day punched a hole in the trip it served. What ships is `COLOUR_PRIORITY` —
   `CONFERENCE > GATHERING > LODGING`, using the week grid's **pale** `--entry-*-bg` — and one
   glyph, the plane, on **every** flight day rather than a run's first. The reasoning is now
   CLAUDE.md's "Zooming out is lossy on purpose".
2. **There is no legend**, which the plan's mockup had. Three tints the week grid already uses need
   no key.
3. **The sticky month bands are gone**, removed 2026-09-01 rather than kept as the plan assumed. A
   week is filed under its *Sunday*, so a `gridEnd` on the 1st–5th left a month with no band and its
   mini a silently dead click — and Sep 1–5 rendered under a band reading "AUGUST 2026". The anchors
   are the month-start day cells, whose set is complete by construction (D3).
4. **Q3 answered: `Page.viewNav`'s trailing slot.** Q3's own analysis argued for a calendar-only
   toolbar as "strictly more moving parts"; the opposite held, because the nav is the only sticky
   element on the page and a second bar would have stacked a third offset under it.
5. **Q4 answered: `<details>`**, reusing `DisclosureMenu` — which also bought
   one-open-at-a-time across the day menus, the thing that keeps the panel and a day menu from
   fighting over the z-index they cannot resolve between them (`.view-nav` is a stacking context).

Two later corrections, from the 2026-09-04 review of the unpushed commits: the weekday header's
`47px` literal was **5px out** against its real 42, so both sticky layers are now measured by
`StickyLayerHeights` (renamed from `StickyNavScript`) and the jump lands where it says; and a stay's
**check-out day is tinted**, four days for three nights, decided rather than inherited.

## Revision 2 (Ted, 2026-09-01, after seeing it running)

The first build was rebuilt against the **approved mockup** (`overlay-mockup.html`), which I had not
opened — I worked from this plan's prose instead, and got a panel two months wide that scrolled. The
mockup's `flex-wrap` over a **fixed 15px cell** is the whole trick: a mini is a natural 117px, so a
1180px panel wraps eight per row and 18 months fit in three rows. Also restored from it: the
per-mini weekday header row, and the exact palette. Then Ted cut it back further:

- **The month bands are gone.** They were for orientation while *scrolling* to find a month; the
  jump replaces that. And they were wrong, not just redundant — a week is filed under its **Sunday**,
  so Sep 1–5 rendered under a band reading "AUGUST 2026". Nothing structural depended on them: the
  anchors are the day cells and "you are here" reads those. `CalendarViewBuilderTest`'s band test is
  now the record of why they went.
- **One glyph, flights only.** Every kind wearing one made a busy month a wall of little pictures,
  each covering the colour under it. Flights book-end trips — and the *absence* of planes on a
  future trip says its flights are not booked yet, which is the useful half.
- **Two channels, not one cascade.** Colour says what the trip *is*; the plane says Ted *moved*. So a
  flight never costs a day its colour, and a conference flown home from on its last day still shows
  four conference days.
- **Three tints, and they are the calendar's own `--entry-*-bg`** — conference > gathering > hotel.
  Travel tints nothing (a flight has its plane; a train or taxi used to punch a hole in the trip it
  served). A private event tints nothing: the map answers "where am I going and when".
- **The legend went with the seven-colour palette it existed to decode.** Three tints and one glyph
  need no key, and the tints match the week grid below, so panel and calendar teach each other.
- **The plane is U+2708 with no U+FE0F.** The variation selector forces the colour emoji, which
  ignores `color`. An earlier revision *added* it and pinned it with a test — backwards. It is dark
  slate, not the mockup's white: white worked on saturated fills and is invisible on pale ones.
- **The cell is 18px, not the mockup's 15** (Ted, 2026-09-01) — taken once the panel had room to
  spare. It costs one mini per row and buys a legible plane.
- **No title row.** It named the range ("Aug 2026 – Feb 2028"), which the first and last minis
  already say. The ✕ stays: it is a control, not information.

**WIDTH is the binding dimension, not height** — I had this backwards and tuned the spacing against
an invented 800px height limit. Ted, 2026-09-01: *"on an ipad, the width is 820px in portrait mode,
so 800px is not a height limit... ipad air viewport size is 820px × 1180px"*. The narrowest real
target is short on width and generous on height, so **how many 138px minis fit across** sets the row
count and there is vertical room to spare everywhere. Measured with a 19-month range, and nothing
scrolls:

| viewport | minis/row | rows | panel height |
|---|---|---|---|
| laptop 1280×800 | 7 | 3 | 551 |
| iPad portrait 820×1180 | 4 | 5 | 895 |
| iPad landscape 1180×820 | 6 | 4 | 703 |
| wide 1440×900 | 7 | 3 | 551 |

The panel caps at 1180px wide, so a larger screen adds margin rather than columns. If the range ever
gets long enough to scroll, the fix is the **cell size or the cap**, not the gaps — and measure
`scrollHeight` against `clientHeight` at 820×1180 rather than reasoning about it.

**The empty cell carries no fill at all — only its border** (Ted, 2026-09-01, after seeing the pale
tints in place). Any grey in an empty cell competes with them: at `#fafbfc` the gathering's `#f5f3ff`
was nearly invisible, and a grey-blue weekend read as the conference's pale blue. Drawing the empty
grid in outline leaves the whole tint range for cells that mean something. Two rules fall out, and
both are load-bearing:

- **Weekend shading is a WARM near-white (`#faf8f5`).** Every tint here is cool — lavender, violet,
  mint — so a neutral or blue-grey weekend reads as one of them, which is precisely what the earlier
  `#eceff2` did against the conference's `#e0e7ff`. Warm *and* very light: it only has to separate
  two empty cells from each other, so it needs far less weight than it did when it was competing
  with saturated fills.
- **"Filled" is said by the EDGE, "which kind" by the hue.** A filled cell's border is materially
  darker than an empty one's. That split is what keeps the gathering's near-white tint readable as a
  marked day at all: it is detectable before it is identifiable, which is the right order for a map.

**Two CSS bugs worth remembering**, both found by screenshot, neither by a test: `width: max-content`
on a wrapping flex container resolves to the *unwrapped* width (the panel went full-bleed); and this
page has no `border-box` reset, so padding and border landed outside the width and overhung the
viewport. A third was found by Ted: `DisclosureMenu`'s `.disclosure-menu > summary { display: block }`
is **more specific** than a bare `.year-overview-trigger`, so the trigger never became a flex row and
its block-level icons stacked three lines tall.

## Revision 1 notes

**Five things the first implementation turned up that the plan did not predict**, all fixed and all
with a test:

1. **The CSS was a leak.** `YearOverview.CSS` was appended to `Page.head` unconditionally, so an
   anonymous body carried `.year-overview`, `.yo-month` and the words "Jump to month" in a
   stylesheet even with no markup. A stylesheet describing an owner-only surface *is* a disclosure —
   the same reason CLAUDE.md gives a viewer nothing rather than a greyed control. The CSS and the
   script are both withheld now. `CalendarRedactionSecurityTest.anonymousViewersGetNoYearOverviewAtAll`
   caught it, which is exactly the "assert absence of the panel *and* the trigger" case D4 asked for.
2. **"You are here" was off by four pixels, and named the wrong month.** The first script re-added
   the sticky stack from element heights (`nav.offsetHeight + .calendar-header.offsetHeight` = 83px)
   while the anchors' `scroll-margin-top` uses the `--calendar-weekday-header-height` literal (87px).
   So immediately after jumping to a month, the panel reported the *previous* one. It now reads each
   anchor's own computed `scroll-margin-top` — the resting place and the test for "am I at it" are
   one number, not two that can drift.
3. **The last month could never be marked.** There is not enough document below the final months to
   scroll their anchors to a resting place, so a plain "last anchor above the offset" named an
   earlier month while Ted looked at December — D7's own failure, at the end of the range he
   scrolled all the way to on purpose. Scrolled to the bottom, the last month wins.
4. **Empty day cells were invisible.** White on white meant the minis did not read as month
   calendars at all: the marks floated in space with no way to tell which weekday one sat on, which
   is the entire point of aligning them. It is the "a 13px square of `#e0e7ff` is indistinguishable
   from empty" lesson pointed at the *empty* cells. They now carry a light fill with weekends a
   shade darker.
5. **The `js` tier cannot click this trigger.** `site.css` is a linked stylesheet and that tier runs
   no server, so `.view-nav` is not sticky there; Playwright scrolls the page back to the top to
   bring the trigger into view, destroying the scroll position under test. The scroll-dependent
   cases set `open` directly, which fires the same `toggle` event. Worth knowing for anything else
   that tests sticky behaviour in this tier.

---

**Original plan follows. Status when written: PLANNED 2026-09-01, two foundations shipped
2026-08-31.**
Layout chosen from an interactive mockup Ted drove himself, so the visual decisions below are
settled rather than proposed. Everything under "Open questions" is not.

**Reviewed against the code 2026-09-01** (second pass, before any of the overlay was written). Five
claims in the first draft did not survive contact with `CalendarViewBuilder`, `CalendarRenderer`,
`DisclosureMenu` and `Page`; they are corrected in place below, each marked **[corrected]** with what
was actually found, because a plan that is wrong about the code it builds on is worse than no plan.
Four further gaps are marked **[gap]**. The review raised five decisions that were genuinely Ted's:
three he answered the same day (the anchor element, the glyphs, and the collapse priority — which he
**reversed**, see D5), and two are still open at the bottom.

## The problem

`/calendar` is a linear stack of Sunday→Saturday weeks. Enter an event eight months out and it
becomes a page you scroll for a long time to learn very little — and there is no way to answer
"what does next spring look like?" without travelling through everything in between.

The first attempt at this treated the length as the problem and collapsed the empty stretches
(`QuietWeekRunsPlan.md`). It was built, refined through three visual iterations, and
reverted, because length was a symptom:

> i wonder if i'm solving the wrong problem … i think i need a more zoomed out view — a year or more
> at a time — where i can get a sense of things and then click on a month to jump to the linear
> calendar. **then the gaps are irrelevant.** (Ted, 2026-08-31)

That is the frame this plan is built on. The linear calendar keeps its length; what it gains is a
map.

## Already shipped (2026-08-31)

Both landed as their own changes, and both turned out to be prerequisites rather than niceties:

- **Sticky month bands** (`CalendarViewBuilder.monthHeader`). Built to fix "i completely lose what
  month it is for weeks that have entries" — and they are also what **D7** reads to answer "which
  month am I looking at". Do not remove them as decoration. (They are *not* necessarily the scroll
  anchors; see D3, which is where the first draft was wrong.)
- **Sticky nav + the z-index scale** (`site.css`, `StickyNavScript`). The overlay's trigger has to
  be reachable from anywhere in a long page, which means the bar it sits in has to be. `--nav-height`
  is measured, not a literal, because the bar wraps.

## D1. An overlay on `/calendar`, not a page

The overlay is markup inside the calendar page, opened by a button in the calendar's own toolbar.

**What that buys, and it is most of the cost of this feature:** no new route, no controller, no
`SecurityConfig` matcher, no `AuthorizationMatrixTest` row, and **no new read model**. It is built
in the renderer from the same `List<CalendarEntry>` the page already holds.

Note for anyone checking this against **R12** (a read model is built from events alone, never from
another read model): that rule governs *projectors*. This is a renderer composing one read model it
was handed, a layer up, which is exactly where R12 says composition belongs.

**Where the code goes: a new class of its own, not another static on `CalendarViewBuilder`.**
`CalendarViewBuilder` is already 621 lines, and CLAUDE.md's day-menu lesson is explicit that *"a
self-contained static renderer gets its own direct test; reaching it only through the page that
embeds it leaves exactly this kind of gap"*. The overlay is the same shape of thing as `dayMenu`,
only larger. See "Tests".

## D2. It spans the page's rendered range — not a fixed 12–18 months

The overlay covers exactly the grid the page drew: `gridStart`…`gridEnd`. Ted's framing is that it
is **a zoomed-out view of the page you are on**, and that settles several things at once:

- **`?from=`/`?to=` views stay coherent.** An overlay pinned to "current month forward" would omit
  months the page is showing.
- **Past weeks revealed by "Show past weeks" are covered**, because they are inside `gridStart`.
  (Note those weeks are always *in the markup* — collapsed, not absent — so their anchors exist
  whether or not the toggle has been pressed. The toggle only appears when a collapsed week has
  entries, which in the default view it never does.)
- **The span is unbounded.** `rangeEnd` is *last entry end + 5 days*, so a booking three years out
  makes a three-year overlay — 36+ minis, and the panel scrolls internally. Acceptable for a map;
  "12–18 months" is no longer an accurate name for it, only a typical size.

In the default view this is the same thing as "starts at the current month", because the page starts
at `today − 1 week`.

**[corrected] The range is computed in `CalendarRenderer.render`, not `CalendarViewBuilder.render`.**
`rangeStart = today − 1 week`, `rangeEnd = max(today + 2 weeks, lastEntryEnd + 5 days)`, then
`?from=`/`?to=` override (and swap if reversed) — all of that is in `CalendarRenderer`. Only the
Sunday/Saturday rounding is in the view builder:

```java
LocalDate gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
LocalDate gridEnd   = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
```

It matters because whichever class the overlay lives in has to be handed the grid bounds, and only
one of those two classes knows them today.

**Unbounded really is unbounded, and it is reachable by hand.** `?from=2000-01-01` renders ~320
minis of ~42 cells — over 13,000 extra elements. Nothing today caps it and nothing needs to for
Ted's data; it is written down so that a slow page from a hand-edited URL is a known consequence
rather than a mystery. If it ever bites, the cap belongs on the *overlay* (draw the first N months,
say so at the bottom), never on the calendar itself, because D3 requires every mini to have a target
on the page.

## D3. Every jump is a scroll

Clicking a month scrolls the linear calendar to that month's anchor. **No page load, ever** — that
is the point of the feature, and D2 is what makes it possible: every month in the overlay is on the
page by construction.

Two consequences:

1. **The month-start day cell carries `id="m-YYYY-MM"`** (Ted, 2026-09-01, answering Q1). Nothing
   carries an id today. The first draft said the month bands; that is the claim the review broke,
   and the reasoning is below.
2. **Every anchor needs `scroll-margin-top` equal to the whole sticky stack** —
   `calc(var(--nav-height) + var(--calendar-weekday-header-height))` — or a jumped-to month lands
   underneath the bars that are covering it. (Verified: that is exactly the `top` the month band
   already sticks at, so the two agree by construction.)

The anchor should acknowledge the jump (a brief highlight), because scrolling a long page to a place
that looks like every other place is disorienting.

### [corrected] The month bands are **not** a complete anchor set — the month-start day cells are

A week is filed under the month its **Sunday** falls in:

```java
YearMonth weekMonth = YearMonth.from(weekStart);   // weekStart is the Sunday
if (!weekMonth.equals(headedMonth)) { weekRows.add(monthHeader(weekMonth)); }
```

`gridEnd` is a Saturday. So whenever `gridEnd` lands on the **1st–5th** of a month, that month has
days on the page and **no band at all** — the final week's Sunday is filed under the previous month.
A "December" mini would then link to `#m-2026-12`, which is not in the document: no error, no
navigation, nothing. That is roughly **one Saturday in six**, not an exotic case. The head of the
grid is safe, `gridStart` being a Sunday by construction, so the hole is at the tail only.

The day-label cells have the mirror-image property, and it comes out complete:

```java
boolean isMonthStart = date.getDayOfMonth() == 1 || isFirstCellOfGrid;
```

`gridStart` itself counts as a month start. So `{gridStart} ∪ {every 1st in the grid}` contains
**exactly one cell in every month the grid touches**, head and tail, with no gaps — because the grid
is contiguous, so every month after the first has its 1st inside it. That set is complete *by
construction*, which is a stronger guarantee than a test can give.

Two smaller points in its favour, neither decisive:

- A jump to the 1st's cell lands on the week **containing** the 1st. A jump to the band lands above
  the first week whose *Sunday* is in that month, which can be a week that is four-sevenths the
  previous month.
- It decouples the overlay from the band's filing rule, so a future change to how weeks are filed
  cannot silently break navigation.

**This does not mean the bands go.** They answer a different question — *"what month am I scrolled
to?"* — which is why they shipped, and D7's "you are here" reads their positions. Keeping them and
hanging the id elsewhere costs nothing.

**Settled (Ted, 2026-09-01): the id goes on the month-start day cell, the bands stay untouched.**
So `renderDayLabelCell` gains `.withId("m-" + YearMonth.from(date))` on the cell it already flags
`is-month-start`, and `monthHeader` is not modified at all. Two consequences worth writing down:

- The `scroll-margin-top` in D3 goes on the **day cell**, not the band. Same value — the day cell
  must clear the same sticky stack — but a different element, and the band's own sticky `top` is
  what that value has to keep agreeing with.
- The jump-acknowledgement highlight lands on the day cell too, so it should read as "this week",
  not "this one square": highlight the cell's row or the week, not a 40px box in the middle of a
  seven-column grid.

## D4. OWNER and FAMILY only — by not emitting it

**The overlay markup is not rendered at all when `isPublicUser`.** Not hidden with CSS, not gated in
JavaScript: absent.

**[corrected] The reason in the first draft was wrong, and the decision is right anyway.** The draft
said an anonymous viewer would learn "the kind of every entry on the calendar", which the public
calendar does not give them today. It does. `renderEntrySegment` takes no `isPublicUser` and emits
`class="entry entry--flight"`, `entry--conference`, `entry--private_event` and the rest for **every**
viewer; the lanes are per-`EntryKind` for everyone; and `EntryDetails.Busy` reports `PRIVATE_EVENT`,
so even the redacted bar is colour-coded and lane-placed by kind. Per-entry kind is already public.

The actual argument is **aggregation, not disclosure**. Each fact in the overlay is public on its
own; a year of them rendered as one 13px-per-day picture is a legible *pattern* — how often Ted
travels, how long he is gone, which seasons are dense, how far ahead he plans. That is a different
artefact from the same facts spread over 150 scrolled weeks, and it is not one we owe a stranger.
Combined with *"when in doubt, redact and ask"*, deny-by-default is the whole answer, and it costs
nothing because nobody has asked for it.

This is worth stating precisely rather than approximately, because the redaction section of
CLAUDE.md is a document people reason **from** later, and a wrong premise there licenses a wrong
conclusion about the next feature.

**FAMILY gets the full overlay**, and that is consistent rather than generous: `CalendarController`
already serves FAMILY the owner's `CalendarAggregator.allEntries()`, so the overlay shows them
nothing their calendar is not already showing.

**Gate on `isPublicUser`, never on `isOwner`** — and there is a test for it, because the trap is
right next door: `dayMenu` is gated `isOwner && date.isAfter(today)`, so an implementer working from
the neighbouring line gates the overlay on `isOwner`, FAMILY silently loses it, and every other test
listed below still passes.

Per CLAUDE.md's affordance rules this is **hiding by permission**, not state — so anonymous viewers
get no greyed-out trigger either. Nothing says the surface exists.

## D5. What a day cell looks like

Layout and colour settled by the mockup; the **collapse priority was reversed afterwards** by Ted on
2026-09-01, and the glyphs were chosen the same day — neither came from the mockup.

Layout **B**: a mini month calendar per month, weekday-aligned, 7 columns, days about one character
wide.

- **Saturated colours.** Use `--entry-<kind>-fg`, not the pale `-bg` fills the week grid uses. A
  13px square of `#e0e7ff` is indistinguishable from empty.
- **Glyph on the first day of a run only.** At this size an emoji fills the cell and hides the colour
  it sits on, so a five-day conference wearing five microphones is five illegible cells. One glyph
  followed by four solid cells reads as "a conference, this long". The seven glyphs are settled — see
  below; they did not exist before 2026-09-01.
- **Edges before identity, filler last.** A day with several entries takes one kind's colour, in this
  order: **`FLIGHT` > `TRAIN` > `CONFERENCE` > `GATHERING` > `PRIVATE_EVENT` > `GROUND_TRANSFER` >
  `LODGING`** (Ted, 2026-09-01). This **replaces** the mockup's "reason before logistics"; see the
  section below for why the reversal is right and what it costs.
- **Weekend shading is on**, because here the columns genuinely *are* weekdays. (It was noise in the
  rejected day-of-month layout, where they are not.)
- **Today is outlined**, in the same amber the month accent uses (`--calendar-month-start-color`).
- **A day cell is display only — no link, and no `title` either** (Ted, 2026-09-01: *"and no hover
  text either, I won't use it"*). Only the month name is a click target. Two things follow, and the
  second is easy to miss:
  - **A day cell holds no text at all** — its whole content is a colour, sometimes a glyph, and its
    position. So the mini grids are **decorative** in the accessibility sense and should say so
    (`aria-hidden="true"` on the grid), with the month link carrying the accessible name. Leaving
    unlabelled empty cells in the tree would have a screen reader announce thirty-one nothings per
    month. The stated cost: a screen-reader user gets a list of months, which is a working jump
    menu and nothing more.
  - Tap targets stop mattering. A ~15px cell is well under a comfortable touch target, which is one
    of the reasons clickable days were declined; with nothing to hit, the size is free to stay small.

### The seven glyphs (Ted, 2026-09-01)

They did not exist. `CalendarViewBuilder.kindIcon` returns a glyph for exactly **one** kind — the
private event's utensils SVG — and the ✈️ and 🚕 that appear on the calendar are baked into *title
strings* built by `FlightCalendarProjector` and `GroundTransferCalendarProjector`. Conference,
gathering, train and lodging had no symbol anywhere in the app. So four of these are new and three
are the app's existing de facto symbols written down:

| Kind | Glyph | |
|---|---|---|
| `CONFERENCE` | 🎤 | new |
| `GATHERING` | 👥 | new |
| `PRIVATE_EVENT` | 🍽️ | matches the utensils SVG on the entry title |
| `FLIGHT` | ✈️ | matches `FlightCalendarProjector`'s route text |
| `TRAIN` | 🚆 | new |
| `GROUND_TRANSFER` | 🚕 | matches `GroundTransferCalendarProjector.TAXI` |
| `LODGING` | 🏨 | new |

**It is a renderer-side `EntryKind`→glyph map, and it is not scraped out of title text.** The two
that exist today live inside strings an *application*-layer projector built — presentation formatting
in the wrong layer, which CLAUDE.md tolerates as an existing counter-example but tells us not to
extend. Parsing an emoji back out of a title would build a second dependency on that mistake. The map
belongs beside the overlay's other rendering decisions.

**Make it an exhaustive `switch` over `EntryKind`**, like `kindIcon` already is, so a new kind stops
the class compiling until someone chooses its glyph. That is the same forcing function
`CalendarDayMenuTest` relies on, and it is free here.

**Two encoding notes, both live in this repo.** Emoji are non-ASCII, so they go through `rawHtml`
per CLAUDE.md's j2html rule, and the page already sets a UTF-8 `Content-Type` and `meta charset`.
And ✈️ and 🍽️ carry a variation selector (U+FE0F) that makes the difference between a glyph and a
monochrome dingbat — keep it, and write the assertions against the exact two-code-point sequence.

### The collapse priority — edges versus identity

**What the review found first.** `EntryKind` declares `CONFERENCE, GATHERING, PRIVATE_EVENT, FLIGHT,
TRAIN, GROUND_TRANSFER, LODGING` — the mockup's "reason before logistics" order, verbatim — and its
javadoc already says *"Lanes are rendered top-to-bottom in declaration order, so the ordering here is
significant."* So that rule was not new; it was the existing lane order, and the recommendation was
to derive it from `EntryKind.values()` rather than copy it.

**What Ted said next** (2026-09-01): *"flights are the highest priority and must always show as they
tend to start/end trips"*. That is a straight reversal of the mockup's rule, and it is right for a
reason the mockup did not have to face:

- The two rules were never fighting the same enemy. "Reason before logistics" was written against
  **`LODGING`**, which is durational — a hotel covers *every night of the trip*, so it is the kind
  most able to swamp a block and say nothing. A flight is not that. It is **punctual**: one day, two
  or three days a trip, and always at an edge.
- At 13px per day, a durational kind gives a block its **identity** and a punctual kind gives it
  **edges**. Losing one day of identity costs little — a five-day conference still has three or four
  indigo days saying what it is. Losing an edge costs everything about that edge, because the edge
  *is* one day. Flying out on the conference's own first day is the ordinary case, not the corner
  one, and under the mockup's order that flight is invisible.
- It improves the glyph story too. With a run defined as consecutive days sharing the winning kind,
  a flight day breaks the conference run, so the conference gets its glyph on the first day it
  actually owns: `✈ 🎤 ▪ ▪ ✈` rather than a solid indigo bar. That reads as *fly, conference, fly* —
  the trip's actual shape.

**The knock-on: the derive-from-the-enum shortcut is dead.** Once flights outrank conferences the
view's priority is *deliberately* different from `EntryKind`'s declaration order, which is the lane
order and must stay as it is. So the overlay needs its **own named ordering**, and a test that pins
it as intentionally divergent — otherwise the next reader sees two orderings, assumes drift, and
"simplifies" the map back into hiding every departure. Name it for what it means (edges before
identity, filler last), not `PRIORITY`.

**The settled order** (Ted, 2026-09-01):

```
FLIGHT > TRAIN > CONFERENCE > GATHERING > PRIVATE_EVENT > GROUND_TRANSFER > LODGING
└──── edges ────┘ └────────── identity ──────────────┘ └─ local hop ─┘ └ filler ┘
```

Three placements carry the argument, and each is a decision rather than a slot in a list:

- **`TRAIN` rides with `FLIGHT`, above the reasons.** The edge argument is the same word for word: an
  intercity train starts and ends a trip exactly as a flight does, and Ted's European travel is full
  of them. Demoting it would reproduce the failure he flagged, one kind further down — a
  Hamburg→Berlin leg on the gathering's first day, invisible.
- **`GROUND_TRANSFER` drops *below* the reasons**, which is the one rank that moves down. A taxi is
  not a trip edge; it is a hop to the airport or across town, usually on a day that already has the
  flight it serves (which outranks it anyway) or in the middle of a conference (where it would
  otherwise hide a day of the thing Ted went for). It stays above `LODGING`, because a transfer day
  inside a stay is worth more than "still in a hotel".
- **`LODGING` stays last**, and that costs it nothing: priority only decides days a kind **shares**.
  A hotel night with nothing else on it is the only claimant and is still green, so a purely personal
  stay reads exactly as it should.

**Write the order down as its own named thing, not as `EntryKind.values()`.** It now diverges from
the declaration order deliberately, and the declaration order is the *lane* order, which must not
move. Name the constant for what it means — edges before identity, filler last — and give it a test
that asserts the divergence is intentional. Without that test the next reader sees two orderings,
reads it as drift, and "simplifies" the map back into hiding every departure.

### [gap] "The first day of a run" is undefined at three boundaries

The rule needs saying precisely before it is written, because all three cases are reachable:

1. **A run is consecutive days sharing the same *winning* kind** — after the priority collapse, not
   per entry. Two back-to-back conferences read as one run; that is accepted (the map says "eight
   conference days", and the linear calendar says which).
2. **A run clipped by `gridStart`** — an entry starting before the range — takes its glyph on the
   first day *visible in the overlay*, not on its true first day, which is off the map.
3. **A run crossing a month boundary** gets its glyph in the first month only; in the next month's
   mini it is solid colour with no glyph. That matches how the week grid treats a continuation
   segment (no title, no icon), so it is consistent rather than a compromise.

### [gap] Away underline and today outline can land on the same 13px cell

D6's turquoise underline and D5's amber outline are both edge treatments on a cell with no interior.
Draw them so they compose — the outline inset, or the underline drawn over it — and put a case in
the test for a day that is both. Nothing here needs a decision; it needs remembering.

## D6. The away band, and the trap it carries

Days Ted is away wear the turquoise underline, from the `awayDays` set `CalendarViewBuilder` is
already handed. It is public by decision and needs no gate of its own.

**The trap, inherited from the reverted plan's D4 and still live:** `ScheduleTimeline.walk()` fills
the nights *between* points, so a trip flown out and back with no hotel booked yet marks days that
carry **no entries at all**. Any code here that reasons "no entry ⇒ nothing to draw" will silently
drop exactly the days the away band exists to warn about. The overlay must read `awayDays`
independently of whether a day has an entry.

## D7. The overlay opens on the month you are looking at

Opening the panel scrolls it to whichever month the linear calendar is currently showing, and marks
that mini "you are here" (Ted, 2026-09-01). Not "always at today", and not a remembered panel
position.

**Why:** the overlay is a map, and a map that does not show where you are standing is a menu. The
failure mode of the remembered-position option is the one a map must not have — the panel sitting in
autumn 2027 while the page behind it sits in September 2026, with nothing saying so.

**It is recomputed on every open, never stored**, so there is no state to go stale when the page is
scrolled with the panel closed. The mechanism is the last `.calendar-month-header` whose top edge is
at or above the sticky offset — the bands being `position: sticky` at that exact offset is what makes
the currently-pinned one identifiable. This is the same query a permanent "you are here" marker would
need, so it is not extra machinery, it is the machinery. **This is the bands' second job, and the
reason they stay even though the anchor ids went to the day cells (D3).**

**[gap] The fallback, which the first draft omitted: when the query matches nothing, mark the first
month in the range.** It matches nothing in the ordinary landing state — page at scroll 0, no band
pinned yet — and again when `?from=`/`?to=` puts the range somewhere today is not. Silently marking
nothing is the "map with no you-are-here" failure the whole decision exists to avoid.

## D8. The trigger is always live

It renders and works whatever the range, including the default one- or two-month window (Ted,
2026-09-01). A two-month overlay is still a map, just a small one.

The alternatives both cost more than they save. Hiding it below some span breaks *"action
affordances never move"* and spends **hiding**, which CLAUDE.md reserves for permission, on a
question of **state**. Greying it follows the letter of the state rule but the reason it would give —
"your calendar is short" — is something the reader can already see and cannot act on, which is a
greyed control that explains nothing. Always live invokes neither rule and means the same thing on
every visit.

## D9. The trigger and the panel

- **Trigger:** a calendar glyph plus a chevron, labelled "Jump to month". The chevron rotates when
  open. **Where it goes is Open question Q3; what it is built from is Q4** — see below, both were
  written as settled in the first draft and neither is.
- **The panel is `position: fixed`, not absolute.** The trigger is meant to live in the sticky nav
  region, so an absolutely-positioned panel would slide away from its own button as the page scrolls.
- **z-index from the scale in `site.css`:** panel 60, scrim 55. (Verified: both slots are **already
  written into the scale** in `site.css`, above `DisclosureMenu`'s 50 and the nav's 30, so a day menu
  left open cannot punch through the panel. Nothing to add there.)
- **Dismissal:** outside click, Escape, the ✕, and choosing a month. Focus returns to the trigger.

### [corrected] There is no calendar toolbar to put it in

`/calendar`'s body is, in order: `Page.viewNav(...)`, `ZoneToggle.render(...)`, the calendar markup,
the scripts. The **only** sticky element on the page is `.view-nav` (z-index 30) — `ZoneToggle` is
not sticky, and the "Show past weeks" button lives inside `.calendar-outer`, which scrolls away.

And `Page.viewNav(NavAudience, String activePath)` has no slot for page-specific content. It is
shared by 11 view pages. So "a button in the calendar toolbar, inside the sticky nav region" is not
something that can be written today without first building somewhere for it to go — which is **Q3**,
and a slice of its own that the first draft's slice list did not have.

### [gap] The rotating chevron can silently break `position: fixed`

A `transform` (or `filter`, `backdrop-filter`, `will-change`, `contain`) on **any** ancestor becomes
the containing block for a `fixed` descendant. D9 asks for a fixed panel *and* a rotating chevron. So
put the rotation on the chevron element itself and never on the trigger's wrapper or a shared
parent. It fails only when the panel is open, only on the axis nobody is looking at, and looks like a
CSS mystery rather than a rule violation — which is why it is written down rather than left to
review.

## Slices

1. **Somewhere sticky for the trigger to live** (Q3). Ships nothing user-visible on its own if it is
   a `viewNav` slot; ships an empty bar if it is a new calendar toolbar. Do it first either way,
   because it decides the offsets everything below sticks at.
2. **Month anchor ids + `scroll-margin-top`** on the month-start day cells. Still nothing
   user-visible. Ships the anchors and the convention test's other half.
3. **The overlay, owner/family only** — trigger, panel, minis, colours, glyphs, away underline,
   today outline, "you are here". Scroll-to-month via anchor.
4. **The convention test** (below). Written with slice 3, listed separately because it is the thing
   most likely to be skipped.

## Tests

- **Its own test class, on its own renderer** — `YearOverviewTest` against the new class from D1, not
  a section bolted onto `CalendarViewBuilderTest`. This is the `CalendarDayMenuTest` lesson applied
  before the fact instead of after it: reaching a self-contained static renderer only through the
  page that embeds it is what let a renamed menu item and an added one both ship green.
  Cases: one mini per month of the range, the right kind colour per day, glyph only on a run's first
  day (including the two boundary cases in D5), away underline on an entry-less away day (D6), today
  outlined, and a day that is both away and today.
- **The collapse priority, as its own cases**, because it is the rule most likely to be "tidied" by
  someone who notices it disagrees with `EntryKind`: a flight sharing the conference's first day
  renders as a flight; a train sharing a gathering's first day renders as a train; a taxi sharing a
  conference day renders as the **conference**; a hotel night sharing anything renders as the other
  thing; and a hotel night sharing nothing still renders as lodging. Plus one assertion that the
  overlay's order is *not* `EntryKind.values()`, naming the reason, so the divergence reads as
  intentional at the place someone would go to remove it.
- **The glyph map is exhaustive over `EntryKind`** and every kind's glyph is asserted by its exact
  code points, U+FE0F included — a variation selector silently dropped turns ✈️ into a monochrome
  dingbat, which is the kind of thing nobody notices in a diff.
- **The audience cases, all three.** Owner renders it; **FAMILY renders it** (the case that fails if
  anyone gates on `isOwner`, per D4); anonymous renders **no overlay markup at all** — assert absence
  of the panel *and* of the trigger, not just of one link.
- **The convention test — the one to insist on.** *Every month link in the overlay resolves to an
  anchor id actually emitted in the same page.* With "every jump is a scroll", a missing anchor is a
  **silently dead click**: no error, no navigation, nothing. Derive both sides from the render rather
  than from a fixture, so a future change to how the range is computed, or to how weeks are filed
  under months, fails here instead of in Ted's hands. Sibling in spirit to
  `TimeFilterToggleConventionTest`. **Include a case whose `gridEnd` falls on the 1st–5th of a
  month** — that is the specific arrangement that breaks band-anchoring, and it stays in the suite
  even though D3 chose the day cell, because it is what would fail if anyone ever moves the ids to
  the bands.
- **`CalendarRedactionSecurityTest`** — an anonymous request through the real security chain whose
  body `doesNotContain` the panel's class, the trigger's id, and a private title that only the
  overlay would carry.
- **`js` tier (`JsBehaviorTest`)** — open, close by each of the four routes, that choosing a month
  moves the scroll position, and **D7**: scroll the page to a month in the middle of the range, open
  the panel, and assert *that* month is the one marked — the case that fails if "you are here" is
  ever quietly reduced to "today". Plus the D7 fallback: open at scroll 0 and assert the first month
  is marked rather than none. (Whether there is an `aria-expanded` to assert on depends on Q4.)
- **Visual check** — the panel over a scrolled page at wide and narrow widths, per
  `feedback_verify_visual_changes_headlessly`. Note headless Chrome clamps its viewport to a 500px
  floor; measure with `--dump-dom` rather than trusting a narrow screenshot.

## Open questions

The three from the first draft were answered by Ted on 2026-09-01 and have moved into D5, D7 and D8:
the trigger is always live; the overlay opens on the month you are looking at; days are display-only,
with no link and no hover text.

Five more came out of the 2026-09-01 code review. **Three are answered**, by Ted the same day, and
have moved into the decisions above:

- **Q1 → D3.** The **month-start day cell** carries `id="m-YYYY-MM"`; the bands are untouched and
  keep their own job (D7).
- **Q2 → D5.** All seven kinds get a glyph, in a renderer-side exhaustive `switch`; three of the
  seven were already the app's de facto symbols.
- **Q5 → D5.** `FLIGHT > TRAIN > CONFERENCE > GATHERING > PRIVATE_EVENT > GROUND_TRANSFER > LODGING`
  — edges before identity, filler last, and deliberately *not* `EntryKind`'s declaration order.

**Both answered by Ted, 2026-09-01, and both built** — the `viewNav` slot (varargs, so the ten
existing callers were untouched) and `<details>` (reusing `DisclosureMenu`, with the panel pulled
out of its absolute positioning by one CSS override):

- **Q3. Where the trigger lives**, given D9's finding that there is no toolbar and `Page.viewNav` has
  no slot for one. **The cost was measured 2026-09-01** rather than guessed, because the first draft
  of this review overstated it as "touches a component shared by 11 pages":
  - `Page.viewNav(NavAudience, String)` has **10 production call sites and 5 in `PageTest`**. An
    overload the two-arg method delegates to changes **none of them** — only `CalendarRenderer`'s
    single line moves to the three-arg form. The shared-component cost is one method and one line.
  - The genuine costs are (a) styling a button to sit in a `flex-wrap` row that is
    `align-items: baseline` over plain text links, and (b) the OWNER bar, already 2–3 lines at
    ~390px, possibly gaining another — measured via `--nav-height`, so nothing breaks, but the page
    this feature exists to make navigable loses a little height.
  - `viewNav`'s javadoc promises the bar depends only on viewer tier, *"never on what the linked
    pages happen to contain."* A trailing slot is page-specific content, so that comment has to say
    the slot is for the current page's own controls and not for more navigation — otherwise a later
    change parks a second page's controls in a shared bar.
  - The alternative, a calendar-only sticky toolbar, changes **three** sticky offsets
    (`.calendar-header`, `.calendar-month-header`, and the anchors' `scroll-margin-top`) and adds a
    second measured variable to `StickyNavScript`. Strictly more moving parts, and the anchor offset
    is the one number a jump gets visibly wrong.
- **Q4. `<details>` or a hand-rolled `<button>`.** `DisclosureMenu` is native `<details>/<summary>`,
  which conveys expanded state implicitly — there is no `aria-expanded` to set or track, and an
  `aria-controls` pointing at a child of the `<details>` says nothing. So the first draft's
  "`aria-expanded` and `aria-controls`" and its "check whether `DisclosureMenu` can be reused" cannot
  both stand. **Leaning `<details>`** — it is one CSS override (`position: fixed` on the list instead
  of `absolute`) in exchange for outside-click, Escape and one-open-at-a-time already written and
  already tested, and it drops the `aria-expanded` requirement rather than failing it. An
  implementation call unless Ted wants otherwise.

## Why there is no "linear calendar" fallback

Worth stating because it will look like an omission: there is no `?from=`/`?to=` link anywhere in
the overlay, and no server round trip on any path. If a month is in the overlay it is on the page,
by D2. If that ever stops being true, this feature has a hole in it — which is precisely what the
convention test is there to catch.
