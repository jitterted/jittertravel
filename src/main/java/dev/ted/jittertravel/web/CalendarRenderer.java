package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ZoneDisplay;

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
                --entry-conference-bg: #e0e7ff; --entry-conference-fg: #4f46e5;
                --entry-gathering-bg: #f5f3ff;  --entry-gathering-fg: #7c3aed;
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
                nav.view-nav { margin: 1rem 0.5rem 0; }
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
            .calendar-header {
                display: grid; grid-template-columns: repeat(7, minmax(0, 1fr));
                position: sticky; top: 0; z-index: 10;
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
            .day-label-cell.is-month-start {
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
            .entry--conference { background-color: var(--entry-conference-bg); color: var(--entry-conference-fg); }
            .entry--gathering  { background-color: var(--entry-gathering-bg);  color: var(--entry-gathering-fg); }
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

        return "<!DOCTYPE html>\n" + BrowserZoneScript.markRoot(html(
                Page.head("Calendar", CSS + DisclosureMenu.CSS),
                body(
                        Page.viewNav(Page.NavAudience.of(isPublicUser, isOwner), "/calendar"),
                        ZoneToggle.render(zoneDisplay),
                        rawHtml(calendarMarkup),
                        rawHtml("<script>" + TOGGLE_SCRIPT + DisclosureMenu.SCRIPT + "</script>"),
                        BrowserZoneScript.render(zoneDisplay)
                )
        ), zoneDisplay).withLang("en").render();
    }
}
