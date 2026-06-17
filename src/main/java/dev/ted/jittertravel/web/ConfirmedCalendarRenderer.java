package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.CalendarEntryRedactor;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static j2html.TagCreator.*;

public class ConfirmedCalendarRenderer {

    private static final CalendarEntryRedactor REDACTOR = new CalendarEntryRedactor();

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
                --calendar-past-hatch: rgba(0, 0, 0, 0.1);
                --calendar-today-tint: #eef2ff;
                --entry-conference-bg: #e0e7ff; --entry-conference-fg: #4f46e5;
                --entry-gathering-bg: #f5f3ff;  --entry-gathering-fg: #7c3aed;
                --entry-flight-bg: #cfeafd;     --entry-flight-fg: #075985;
                --entry-train-bg: #ffedd5;      --entry-train-fg: #9a3412;
                --entry-lodging-bg: #dcfce7;    --entry-lodging-fg: #166534;
            }
            .calendar-outer {
                margin: 2rem 4rem;
                font-family: system-ui, -apple-system, sans-serif;
            }
            .calendar-container {
                border-left: 1px solid var(--calendar-border-strong);
                border-top: 1px solid var(--calendar-border-strong);
            }
            .calendar-header {
                display: grid; grid-template-columns: repeat(7, 1fr);
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
            .calendar-week {
                display: grid; grid-template-columns: repeat(7, 1fr);
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
            .day-number.is-month-start {
                font-size: 1.25rem; font-weight: 700;
                color: var(--calendar-month-start-color); letter-spacing: 0.02em;
            }
            .lane-cell { border-right: 1px solid var(--calendar-border); min-height: 64px; box-sizing: border-box; }
            .lane-cell.month-tint-even { background-color: var(--calendar-tint-even-lane); }
            .lane-cell.month-tint-odd  { background-color: var(--calendar-tint-odd); }
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
            .entry {
                position: relative;
                margin: 4px 6px; padding: 6px 10px; border-radius: 8px;
                box-sizing: border-box; font-size: 0.9rem; line-height: 1.3;
                min-height: 52px; display: flex; flex-direction: column; justify-content: center;
            }
            .entry-title { font-weight: 700; font-size: 0.95rem; letter-spacing: 0.01em; }
            .entry-subtitle { font-size: 0.8rem; font-weight: 500; opacity: 0.95; margin-top: 2px; }
            .entry--conference { background-color: var(--entry-conference-bg); color: var(--entry-conference-fg); }
            .entry--gathering  { background-color: var(--entry-gathering-bg);  color: var(--entry-gathering-fg); }
            .entry--flight     { background-color: var(--entry-flight-bg);     color: var(--entry-flight-fg); }
            .entry--train      { background-color: var(--entry-train-bg);      color: var(--entry-train-fg); }
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

    public static String render(List<CalendarEntry> rawEntries, LocalDate today, boolean isPublicUser) {
        List<CalendarEntry> entries = rawEntries.stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .map(e -> isPublicUser ? REDACTOR.redact(e) : e)
                .toList();

        LocalDate rangeStart;
        LocalDate rangeEnd;
        if (entries.isEmpty()) {
            rangeStart = today.minusWeeks(2);
            rangeEnd = today.plusWeeks(2);
        } else {
            rangeStart = entries.stream()
                    .map(e -> e.start().toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElseThrow()
                    .minusDays(5);
            rangeEnd = entries.stream()
                    .map(e -> e.end().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElseThrow()
                    .plusDays(5);
        }

        String calendarMarkup = CalendarViewBuilder.render(entries, rangeStart, rangeEnd, today, isPublicUser);

        return "<!DOCTYPE html>\n" + html(
                Page.head("Confirmed Calendar", CSS),
                body(
                        nav(
                                a("JitterTravel")
                                        .withHref("/")
                                        .withStyle("font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI'; color: #4f46e5;")
                        ).withStyle("margin-left: 4rem; font-size: 0.9rem;"),
                        rawHtml(calendarMarkup),
                        rawHtml("<script>" + TOGGLE_SCRIPT + "</script>")
                )
        ).withLang("en").render();
    }
}
