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
     * Resolves a request parameter to a view, falling back to {@link #LIST} when the value is
     * absent or unrecognized. Case-insensitive. The list stays the default so every existing link
     * to {@code /schedule-problems} keeps the page it has always shown.
     */
    public static ProblemView fromParam(String value) {
        if (value == null) {
            return LIST;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException unrecognized) {
            return LIST;
        }
    }

    String param() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
