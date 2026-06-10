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
                --entry-conference-bg: #e0e7ff; --entry-conference-fg: #4f46e5;
                --entry-gathering-bg: #f5f3ff;  --entry-gathering-fg: #7c3aed;
                --entry-flight-bg: #cfeafd;     --entry-flight-fg: #075985;
                --entry-train-bg: #ffedd5;      --entry-train-fg: #9a3412;
                --entry-lodging-bg: #dcfce7;    --entry-lodging-fg: #166534;
            }
            .calendar-container {
                margin: 2rem 4rem;
                font-family: system-ui, -apple-system, sans-serif;
                border-left: 1px solid var(--calendar-border-strong);
                border-top: 1px solid var(--calendar-border-strong);
            }
            .calendar-header { display: grid; grid-template-columns: repeat(7, 1fr); }
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
                font-size: 0.9rem; font-weight: 500;
                color: var(--calendar-text-secondary);
                text-align: left; text-decoration: none; display: block;
            }
            .day-number:hover { text-decoration: underline; }
            .day-number.is-month-start {
                font-size: 1.25rem; font-weight: 700;
                color: var(--calendar-month-start-color); letter-spacing: 0.02em;
            }
            .lane-cell { border-right: 1px solid var(--calendar-border); min-height: 64px; box-sizing: border-box; }
            .lane-cell.month-tint-even { background-color: var(--calendar-tint-even-lane); }
            .lane-cell.month-tint-odd  { background-color: var(--calendar-tint-odd); }
            .entry {
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
            """;

    public static String render(List<CalendarEntry> rawEntries, boolean isPublicUser) {
        List<CalendarEntry> entries = rawEntries.stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .map(e -> isPublicUser ? REDACTOR.redact(e) : e)
                .toList();

        LocalDate rangeStart;
        LocalDate rangeEnd;
        if (entries.isEmpty()) {
            LocalDate today = LocalDate.now();
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

        String calendarMarkup = CalendarViewBuilder.render(entries, rangeStart, rangeEnd, isPublicUser);

        return "<!DOCTYPE html>\n" + html(
                head(
                        meta().withCharset("UTF-8"),
                        title("Confirmed Calendar"),
                        rawHtml("<style>" + CSS + "</style>")
                ),
                body(
                        nav(
                                a("JitterTravel")
                                        .withHref("/")
                                        .withStyle("font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI'; color: #4f46e5;")
                        ).withStyle("margin-left: 4rem; font-size: 0.9rem;"),
                        rawHtml(calendarMarkup)
                )
        ).withLang("en").render();
    }
}
