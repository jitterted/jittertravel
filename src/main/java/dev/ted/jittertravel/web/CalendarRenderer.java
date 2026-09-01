package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ZoneDisplay;
import j2html.tags.DomContent;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static j2html.TagCreator.*;

public class CalendarRenderer {

    private static final String CSS = """
            :root {
                --calendar-border: #dee2e6;
                --calendar-border-strong: darkgray;
                --calendar-surface: #ffffff;
                --calendar-header-bg: #f8f9fa;
                --calendar-text-secondary: #495057;
                --calendar-tint-even-day-label: #faf7f7;
                --calendar-tint-even-lane: #faf7f0;
                --calendar-tint-odd: #ffffff;
                --calendar-month-start-color: #b45309;
                --calendar-month-start-border-width: 3px;
                --calendar-away-color: turquoise;
                --calendar-away-border-width: 4px;
                --calendar-past-hatch: rgba(0, 0, 0, 0.1);
                --calendar-today-tint: #eef2ff;
                --calendar-empty-band-min-height: 120px;
                /* The weekday header is ~47px tall and sticks at 0, so the month band parks
                   directly under it; a pixel out either way costs a hairline, not a bug. */
                --calendar-weekday-header-height: 47px;
                --entry-conference-bg: #e0e7ff; --entry-conference-fg: #4f46e5;
                --entry-gathering-bg: #f5f3ff;  --entry-gathering-fg: #7c3aed;
                /* Periwinkle: hue between the conference's indigo and the gathering's violet, and
                   lighter than the conference's fill — a deeper tint (#e4e2fd, tried first) read
                   as a conference at week-grid scale. What separates it from the two neighbours is
                   the utensils icon on the title, not the fill alone; see kindIcon in
                   CalendarViewBuilder. That icon is OWNER-side only, but this colour is also what
                   an anonymous viewer's `Busy` bar wears (EntryDetails.Busy reports PRIVATE_EVENT),
                   so a future private kind shares this lane rather than earning one of its own. */
                --entry-private_event-bg: #ece9fe; --entry-private_event-fg: #5b4bd6;
                --entry-flight-bg: #cfeafd;     --entry-flight-fg: #075985;
                --entry-train-bg: #ffedd5;      --entry-train-fg: #9a3412;
                /* Taxi yellow, deliberately distinct from the train's orange: sharing the train
                   lane's colour was rejected (D5 reason 2) precisely so a taxi cannot be mistaken
                   for a booked leg. */
                --entry-ground_transfer-bg: #fef9c3; --entry-ground_transfer-fg: #854d0e;
                --entry-lodging-bg: #dcfce7;    --entry-lodging-fg: #166534;
            }
            .calendar-outer {
                margin: 2rem 4rem;
                font-family: system-ui, -apple-system, sans-serif;
            }
            /* Align the shared view-nav with the calendar body (which sits at 4rem);
               base .view-nav styling lives in site.css. */
            nav.view-nav { margin: 1.5rem 4rem 0; }
            /* Narrow screens (phone, tablet portrait): the 4rem side gutters cost 8rem of a
               ~390px viewport — more than a whole day column out of seven. Give that width
               back to the grid; the container's own borders still frame it at the edge. */
            @media (max-width: 900px) {
                .calendar-outer { margin: 1rem 0; }
                /* The 0.5rem inset moves from margin to padding now that the bar is sticky:
                   the calendar itself runs to margin 0 here, so a bar narrower than the grid
                   would let week rows scroll past in the gap on either side of it. */
                nav.view-nav { margin: 1rem 0 0; padding-left: 0.5rem; padding-right: 0.5rem; }
            }
            .calendar-container {
                border-left: 1px solid var(--calendar-border-strong);
                border-top: 1px solid var(--calendar-border-strong);
                border-bottom: 1px solid var(--calendar-border-strong);
            }
            /* minmax(0, 1fr), never a bare 1fr: `1fr` is `minmax(auto, 1fr)`, so a track's floor
               is its widest item's min-content width. Every week below is its *own* grid, so one
               un-shrinkable entry (a route label, a long venue name) would widen that week's
               column alone and knock it out of registration with the other weeks and this header.
               Pinning the min to 0 makes each track exactly 1/7 of the container at every width,
               so the columns align by construction rather than by content happening to be short. */
            /* Parks under the sticky nav, whose height is published as --nav-height by
               StickyNavScript because the bar wraps. The 0 fallback is what a page with no nav
               (or with scripting off) gets: the header then sticks to the very top, which is
               where it stuck before the nav did. */
            .calendar-header {
                display: grid; grid-template-columns: repeat(7, minmax(0, 1fr));
                position: sticky; top: var(--nav-height, 0px); z-index: 10;
            }
            .calendar-header div {
                text-align: center; font-weight: 600;
                background-color: var(--calendar-header-bg);
                border-bottom: 1px solid var(--calendar-border);
                border-right: 1px solid var(--calendar-border);
                padding: 12px 0; font-size: 0.9rem;
                color: var(--calendar-text-secondary);
            }
            /* minmax(0, 1fr) for the same reason as .calendar-header above — this is the grid
               whose tracks would otherwise drift week to week. */
            .calendar-week {
                display: grid; grid-template-columns: repeat(7, minmax(0, 1fr));
                background-color: var(--calendar-surface);
            }
            .day-label-cell {
                grid-row: 1;
                border-top: 1px solid var(--calendar-border-strong);
                border-bottom: 1px solid var(--calendar-border);
                border-right: 1px solid var(--calendar-border);
                min-height: 40px; padding: 6px 8px;
                background-color: var(--calendar-surface); box-sizing: border-box;
            }
            .day-label-cell.month-tint-even { background-color: var(--calendar-tint-even-day-label); }
            .day-label-cell.month-tint-odd  { background-color: var(--calendar-tint-odd); }
            /* Away from home: one thick turquoise stripe along the bottom of the day label,
               replacing that cell's 1px border. Bottom edge only — no end caps — so the month
               border keeps the left edge and the cells' right edges stay uniform. */
            .day-label-cell.is-away {
                border-bottom: var(--calendar-away-border-width) solid var(--calendar-away-color);
            }
            /* The year overview's scroll target (see CalendarViewBuilder.monthAnchorId). It must
               clear the whole sticky stack, which is the same offset the month band parks at — keep
               the two in step, because a jump landing under the bars is the one number this feature
               gets visibly wrong. */
            .day-label-cell.is-month-start {
                scroll-margin-top: calc(var(--nav-height, 0px) + var(--calendar-weekday-header-height));
                border-top: var(--calendar-month-start-border-width) solid var(--calendar-month-start-color);
                border-left: var(--calendar-month-start-border-width) solid var(--calendar-month-start-color);
            }
            .day-number {
                font-size: 0.9rem; font-weight: 700;
                color: var(--calendar-text-secondary);
                text-align: left; text-decoration: none; display: block;
            }
            .day-number:hover { text-decoration: underline; }
            .day-label-cell.is-past .day-number { font-weight: 500; }
            /* The owner's future-day "Add ..." menu is a DisclosureMenu; its popup mechanics
               live there, shared with the fix menus on /schedule-problems. */
            .day-number.is-month-start {
                font-size: 1.25rem; font-weight: 700;
                color: var(--calendar-month-start-color); letter-spacing: 0.02em;
            }
            .lane-cell { border-right: 1px solid var(--calendar-border); min-height: 64px; box-sizing: border-box; }
            .lane-cell.month-tint-even { background-color: var(--calendar-tint-even-lane); }
            .lane-cell.month-tint-odd  { background-color: var(--calendar-tint-odd); }
            /* An otherwise-empty (non-collapsed) week's single filler band: taller than a
               normal lane so a free week reads as open vertical space, not a thin strip. */
            .lane-cell--empty { min-height: var(--calendar-empty-band-min-height); }
            /* Past days: diagonal hatch layered over the month tint. */
            .day-label-cell.is-past, .lane-cell.is-past {
                background-image: repeating-linear-gradient(
                    -45deg,
                    transparent 0, transparent 7px,
                    var(--calendar-past-hatch) 7px, var(--calendar-past-hatch) 8px
                );
            }
            /* Today: full-height tinted column. */
            .day-label-cell.is-today, .lane-cell.is-today {
                background-color: var(--calendar-today-tint);
            }
            /* min-width: 0 so an entry can shrink with its track instead of refusing to go below
               its own min-content and spilling over the day boundary (the page must never scroll
               sideways). Belt-and-braces alongside the minmax(0, 1fr) tracks above, and the thing
               that keeps this safe if those tracks ever go back to an intrinsic minimum. */
            .entry {
                position: relative;
                margin: 4px 6px; padding: 6px 10px; border-radius: 8px;
                box-sizing: border-box; font-size: 0.9rem; line-height: 1.3;
                min-width: 0;
                min-height: 52px; display: flex; flex-direction: column; justify-content: center;
            }
            .entry-title { font-weight: 700; font-size: 0.95rem; letter-spacing: 0.01em; }
            .entry-subtitle { font-size: 0.8rem; font-weight: 500; opacity: 0.95; margin-top: 2px; }
            /* Public "speaking" chip on a gathering: a solid high-contrast pill (near-black
               background, white text) so it stands out sharply against the gathering tint. */
            .entry-speaking-badge {
                align-self: flex-start; margin-top: 3px;
                font-size: 0.6rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em;
                padding: 2px 6px; border-radius: 4px; background: #111827; color: #ffffff;
            }
            /* Public "Maybe" chip on a speculative conference. Amber rather than the speaking
               chip's near-black so the two read as different statements, and solid rather than
               muted: muted conventionally reads as *cancelled*, and a distinction nobody knows
               the convention for is no distinction at all. */
            .entry-maybe-badge {
                align-self: flex-start; margin-top: 3px;
                font-size: 0.6rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em;
                padding: 2px 6px; border-radius: 4px; background: #b45309; color: #ffffff;
            }
            .edit-pencil { margin-left: 0.4rem; color: inherit; opacity: 0.65; text-decoration: none; vertical-align: middle; }
            .edit-pencil:hover { opacity: 1; }
            .edit-pencil svg { width: 12px; height: 12px; }
            /* The cancel bin sits in the pencil's slot on the kinds that have no edit page, and
               matches it exactly — no red: removing one ground transfer is recoverable by entering
               it again, and red is reserved for what cannot be undone. */
            .cancel-bin { margin-left: 0.4rem; color: inherit; opacity: 0.65; text-decoration: none; vertical-align: middle; }
            .cancel-bin:hover { opacity: 1; }
            .cancel-bin svg { width: 12px; height: 12px; }
            /* Kind glyph before the title. Sized to the title's cap height and inheriting
               currentColor, so it tints with the lane rather than sitting on it as a second
               colour. Slightly under full opacity: it marks the lane, it is not the content. */
            .entry-kind-icon { margin-right: 0.35em; vertical-align: middle; opacity: 0.75; }
            /* Height only, width auto: the utensils viewBox is 448x512, so forcing a square
               would squash the fork. Sized in em so it tracks the title, as the pencil does not
               need to (the pencil is a fixed-size affordance; this is part of the text). */
            .entry-kind-icon svg { height: 0.95em; width: auto; vertical-align: middle; }
            .entry--conference { background-color: var(--entry-conference-bg); color: var(--entry-conference-fg); }
            .entry--gathering  { background-color: var(--entry-gathering-bg);  color: var(--entry-gathering-fg); }
            .entry--private_event { background-color: var(--entry-private_event-bg); color: var(--entry-private_event-fg); }
            .entry--flight     { background-color: var(--entry-flight-bg);     color: var(--entry-flight-fg); }
            .entry--train      { background-color: var(--entry-train-bg);      color: var(--entry-train-fg); }
            /* The class name comes from EntryKind.name().toLowerCase(), underscore and all. */
            .entry--ground_transfer { background-color: var(--entry-ground_transfer-bg); color: var(--entry-ground_transfer-fg); }
            .entry--lodging    { background-color: var(--entry-lodging-bg);    color: var(--entry-lodging-fg); }
            .entry--continuation { opacity: 0.9; }
            /* Entries spanning a week boundary: square the continuing edge and run it flush
               to the boundary so the bar visibly carries over into the adjacent week. */
            .entry--from-left {
                border-top-left-radius: 0; border-bottom-left-radius: 0; margin-left: 0;
            }
            .entry--to-right {
                border-top-right-radius: 0; border-bottom-right-radius: 0; margin-right: 0;
                padding-right: 20px;
            }
            .entry--to-right::after {
                content: "\\2192";  /* rightwards arrow: this entry continues next week */
                position: absolute; right: 5px; top: 50%; transform: translateY(-50%);
                font-size: 1rem; font-weight: 700; opacity: 0.75;
            }
            /* Collapsed prior weeks: hide the lane rows + entries so only the day-label
               row shows (the auto track sizes to 0 with no content). The markup stays in
               place so a click can reveal it. */
            .calendar-week--collapsed .lane-cell,
            .calendar-week--collapsed .entry { display: none; }
            .calendar-week--collapsed { cursor: pointer; }
            /* Revealed weeks: is-expanded is the single source of truth, set per-week by a
               click or for every week by the global toggle. */
            .calendar-week--collapsed.is-expanded .lane-cell { display: block; }
            .calendar-week--collapsed.is-expanded .entry { display: flex; }
            .calendar-week--collapsed.is-expanded { cursor: default; }
            /* Per-day count badge: only visible on a collapsed week, hidden once expanded. */
            .day-badge {
                display: none;
                float: right;
                min-width: 1.1rem; padding: 0 5px; margin-top: 1px;
                border-radius: 9px; background-color: #e5e7eb;
                color: #6b7280; font-size: 0.7rem; font-weight: 700;
                line-height: 1.25rem; text-align: center;
            }
            .calendar-week--collapsed .day-badge { display: inline-block; }
            .calendar-week--collapsed.is-expanded .day-badge { display: none; }
            /* The sticky month band lived here until 2026-09-01; see the note in CalendarViewBuilder
               for why it went. --calendar-weekday-header-height stays: the jump anchors' own
               scroll-margin-top is still measured off it. */
            /* Acknowledges a jump from the year overview. Scrolling a long page to a place that
               looks like every other place is disorienting, so the arrived-at week says so briefly.
               On the week, not on the one day cell that carries the id. */
            .calendar-week.is-jump-target { animation: jump-target-flash 1.2s ease-out; }
            @keyframes jump-target-flash {
                from { background-color: var(--calendar-today-tint); }
                to   { background-color: var(--calendar-surface); }
            }
            .toggle-all-weeks {
                display: block; margin: 0 0 6px auto;
                background: none; border: none; padding: 2px 4px;
                color: var(--calendar-text-secondary); font-size: 0.75rem;
                cursor: pointer; text-decoration: underline;
            }
            .toggle-all-weeks:hover { color: var(--calendar-month-start-color); }
            """;

    private static final String TOGGLE_SCRIPT = """
            var collapsedWeeks = document.querySelectorAll('.calendar-week--collapsed');
            var toggleAll = document.getElementById('toggle-all-weeks');
            function anyWeekCollapsed() {
                return Array.prototype.some.call(collapsedWeeks, function (week) {
                    return !week.classList.contains('is-expanded');
                });
            }
            function syncToggleAllLabel() {
                if (toggleAll) {
                    toggleAll.textContent = anyWeekCollapsed() ? 'Show past weeks' : 'Hide past weeks';
                }
            }
            collapsedWeeks.forEach(function (week) {
                week.addEventListener('click', function (event) {
                    if (event.target.closest('a')) return;  // let day links navigate
                    week.classList.toggle('is-expanded');
                    syncToggleAllLabel();
                });
            });
            if (toggleAll) {
                toggleAll.addEventListener('click', function () {
                    var expandAll = anyWeekCollapsed();  // any still collapsed -> show all, else hide all
                    collapsedWeeks.forEach(function (week) {
                        week.classList.toggle('is-expanded', expandAll);
                    });
                    syncToggleAllLabel();
                });
            }
            """;

    // The owner future-day disclosure menus are native <details>, which on their own never
    // dismiss: clicking away leaves them open, Escape does nothing, and opening a second day
    // leaves the first open so the absolutely-positioned menus stack and overlap. This adds the
    // three behaviors a popup is expected to have — only one open at a time, close on
    // outside-click, close on Escape. Harmless when no day menus are present (owner-only render).
    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser) {
        return render(rawEntries, today, isPublicUser, false, null, null);
    }

    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser, boolean isOwner) {
        return render(rawEntries, today, isPublicUser, isOwner, null, null);
    }

    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser, boolean isOwner,
                                LocalDate from, LocalDate to) {
        return render(rawEntries, today, isPublicUser, isOwner, from, to, ZoneDisplay.entryOnly());
    }

    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser, boolean isOwner,
                                LocalDate from, LocalDate to, ZoneDisplay zoneDisplay) {
        return render(rawEntries, today, isPublicUser, isOwner, from, to, zoneDisplay, Set.of());
    }

    /**
     * @param rawEntries already the right entries for this viewer. Nothing is stripped here: an
     *                   anonymous viewer's entries come from {@code PublicCalendarProjector}, which
     *                   never built a private value in the first place, and the controller chose
     *                   between the two read models at the boundary where the viewer is known.
     *                   {@code isPublicUser} still reaches the builder, but only to decide the
     *                   day-cell affordances (no create menus for strangers).
     * @param awayDays   the days to stripe with the away band, from {@code ScheduleGapProjector}.
     *                   Passed through untouched for every viewer, the band being public by
     *                   decision.
     */
    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser, boolean isOwner,
                                LocalDate from, LocalDate to, ZoneDisplay zoneDisplay, Set<LocalDate> awayDays) {
        List<CalendarEntry> entries = rawEntries.stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();

        // Default window: from one week before today (so the calendar always opens near "now",
        // never scrolled back to the earliest historical entry) through at least two weeks out,
        // extended to cover the last entry when trips run further ahead. Past entries before the
        // start are reached via an explicit ?from=.
        LocalDate rangeStart = today.minusWeeks(1);
        LocalDate rangeEnd = today.plusWeeks(2);
        if (!entries.isEmpty()) {
            LocalDate lastEntryEnd = entries.stream()
                    .map(e -> e.end().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElseThrow()
                    .plusDays(5);
            if (lastEntryEnd.isAfter(rangeEnd)) {
                rangeEnd = lastEntryEnd;
            }
        }
        // When both endpoints are given explicitly but reversed, swap them so the viewer
        // sees the intended window regardless of param order (a reversed range renders empty).
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        if (from != null) {
            rangeStart = from;
        }
        if (to != null) {
            rangeEnd = to;
        }

        String calendarMarkup = CalendarViewBuilder.render(entries, rangeStart, rangeEnd, today, isPublicUser, isOwner, awayDays);

        // The overlay spans exactly the days the grid drew, so every month in it is a scroll rather
        // than a page load. Both sides take the rounding from CalendarViewBuilder rather than
        // repeating it.
        DomContent yearOverview = YearOverview.render(
                entries,
                CalendarViewBuilder.gridStart(rangeStart), CalendarViewBuilder.gridEnd(rangeEnd),
                today, awayDays, isPublicUser);
        // The overlay's CSS and script are withheld from an anonymous render too, not just its
        // markup. Both name the panel's classes and its "Jump to month" label, and a stylesheet
        // describing an owner-only surface is itself a disclosure — the same reason CLAUDE.md says
        // a viewer who could never trigger an action gets nothing rather than a greyed control.
        // Pinned by CalendarRedactionSecurityTest, which caught the CSS half of this.
        String overlayCss = isPublicUser ? "" : YearOverview.CSS;
        String overlayScript = isPublicUser ? "" : YearOverview.SCRIPT;

        return "<!DOCTYPE html>\n" + BrowserZoneScript.markRoot(html(
                Page.head("Calendar", CSS + DisclosureMenu.CSS + overlayCss),
                body(
                        Page.viewNav(Page.NavAudience.of(isPublicUser, isOwner), "/calendar", yearOverview),
                        ZoneToggle.render(zoneDisplay),
                        rawHtml(calendarMarkup),
                        rawHtml("<script>" + TOGGLE_SCRIPT + DisclosureMenu.SCRIPT + overlayScript + "</script>"),
                        BrowserZoneScript.render(zoneDisplay)
                )
        ), zoneDisplay).withLang("en").render();
    }
}
