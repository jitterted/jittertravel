package dev.ted.jittertravel.web;

import java.util.Locale;

/**
 * Which of the two views of the schedule-problems report is showing: the four-column card
 * {@link #LIST}, or the week-row {@link #CALENDAR}. Both live at {@code /schedule-problems},
 * selected by {@code ?view=} — see {@code docs/archived/ProblemCalendarPlan.md}.
 */
public enum ProblemView {
    LIST,
    CALENDAR;

    /**
     * Resolves a request parameter to a view, falling back to {@link #CALENDAR} when the value is
     * absent or unrecognized. Case-insensitive.
     * <p>
     * The calendar is the default (Ted, 2026-08-21). The plan shipped with the list as the default
     * to keep existing links showing the page they always had; now that the calendar reads
     * correctly — all bands amber, each saying what to do about itself — it is the view that
     * answers the question a problem actually raises, which is <em>when</em>, and how it sits
     * against the rest of the trip. The list is a click away and still linked from the toggle.
     */
    public static ProblemView fromParam(String value) {
        if (value == null) {
            return CALENDAR;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException unrecognized) {
            return CALENDAR;
        }
    }

    String param() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
