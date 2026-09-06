package dev.ted.jittertravel.web;

import java.util.Optional;

/**
 * Where a fix link was clicked from, carried on the link as {@code ?from=} so the page it lands on
 * can offer the way back to the surface Ted was actually reading.
 * <p>
 * Three surfaces render fix links today, and they are not all the same page: the two views of
 * {@code /schedule-problems}, and the missing-hotel row on {@code /itinerary}. Landing back in the
 * list after clicking a calendar band reads as the app losing your place, and landing on the report
 * at all when you came from the itinerary is simply wrong.
 * <p>
 * Every caller of {@link ProblemFix#forProblem} names its origin — there is no default overload, so
 * a new surface offering fixes has to decide where its links come back to.
 */
public enum FixOrigin {
    PROBLEM_LIST("list", "Back to schedule problems", "/schedule-problems?view=list"),
    PROBLEM_CALENDAR("calendar", "Back to schedule problems", "/schedule-problems?view=calendar"),
    ITINERARY("itinerary", "Back to itinerary", "/itinerary");

    private final String param;
    private final String backLabel;
    private final String backHref;

    FixOrigin(String param, String backLabel, String backHref) {
        this.param = param;
        this.backLabel = backLabel;
        this.backHref = backHref;
    }

    /**
     * Resolves a {@code ?from=} value, falling back to the problem calendar when it is absent or
     * unrecognized — the same default {@link ProblemView#fromParam} takes, so a hand-typed link
     * comes back to the view the report itself opens in. Case-insensitive.
     */
    public static FixOrigin fromParam(String value) {
        if (value == null) {
            return PROBLEM_CALENDAR;
        }
        for (FixOrigin origin : values()) {
            if (origin.param.equalsIgnoreCase(value)) {
                return origin;
            }
        }
        return PROBLEM_CALENDAR;
    }

    /**
     * Where a fix action should send Ted <em>after it succeeds</em> — empty when he did not arrive
     * from a fix link at all, in which case the controller keeps its own default.
     * <p>
     * <strong>Absent is not the same as {@code calendar} here</strong>, which is why this is not
     * {@link #fromParam}. That method defaults a missing value to the calendar so a hand-typed link
     * still renders a sensible Back link; doing the same on a redirect would send every ordinary
     * booking — made from the nav card or the calendar day-menu — to the schedule-problems report,
     * which is not where those journeys end.
     * <p>
     * <strong>The value is a query parameter, so it is resolved through the enum and never used as
     * a path.</strong> An unrecognized {@code ?from=} comes back as the calendar, not as itself: a
     * hand-edited one cannot become an open redirect.
     */
    public static Optional<String> returnTo(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(fromParam(value).backHref());
    }

    public String param() {
        return param;
    }

    public String backLabel() {
        return backLabel;
    }

    public String backHref() {
        return backHref;
    }
}
