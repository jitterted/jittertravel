package dev.ted.jittertravel.web;

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
