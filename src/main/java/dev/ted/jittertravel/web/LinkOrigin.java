package dev.ted.jittertravel.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Where a link was clicked from, carried on it as {@code ?from=} (and, for a surface laid out by
 * day, {@code &day=}) so the page it lands on can send Ted back to the surface he was actually
 * reading — by its Back or "Keep it" link, and after its action succeeds.
 * <p>
 * Two kinds of link carry one. A {@link ProblemFix} names its origin on every href: the two views
 * of {@code /schedule-problems}, and the missing-hotel row on {@code /itinerary}. Every cancel bin
 * on {@code /calendar} and {@code /itinerary} names its surface and the day it is drawn on, so
 * cancelling a stay from its check-out day lands back on that day
 * ({@code docs/CancelReturnsWhereYouCameFromPlan.md}).
 * <p>
 * Every caller of {@link ProblemFix#forProblem} names its origin — there is no default overload, so
 * a new surface offering fixes has to decide where its links come back to.
 * <p>
 * <strong>Both values are query parameters, so neither is ever used as text in a path.</strong>
 * {@code from} resolves through this enum, and an unrecognized one comes back as the problem
 * calendar, not as itself. {@code day} is parsed as a {@link LocalDate} and dropped if it is not
 * one. A hand-edited link therefore cannot become an open redirect.
 */
public enum LinkOrigin {
    PROBLEM_LIST("problems-list", "Back to schedule problems", "schedule problems",
            "/schedule-problems?view=list", null),
    PROBLEM_CALENDAR("problems-calendar", "Back to schedule problems", "schedule problems",
            "/schedule-problems?view=calendar", null),
    ITINERARY("itinerary", "Back to itinerary", "the itinerary", "/itinerary", "date"),
    CALENDAR("calendar", "Back to calendar", "the calendar", "/calendar", "day");

    private final String param;
    private final String backLabel;
    private final String place;
    private final String backHref;
    /** The surface's own name for "open on this day", or {@code null} where it has none. */
    private final String dayParam;

    LinkOrigin(String param, String backLabel, String place, String backHref, String dayParam) {
        this.param = param;
        this.backLabel = backLabel;
        this.place = place;
        this.backHref = backHref;
        this.dayParam = dayParam;
    }

    /**
     * Resolves a {@code ?from=} value, falling back to the problem calendar when it is absent or
     * unrecognized — the same default {@link ProblemView#fromParam} takes, so a hand-typed link
     * comes back to the view the report itself opens in. Case-insensitive.
     */
    public static LinkOrigin fromParam(String value) {
        if (value == null) {
            return PROBLEM_CALENDAR;
        }
        for (LinkOrigin origin : values()) {
            if (origin.param.equalsIgnoreCase(value)) {
                return origin;
            }
        }
        return PROBLEM_CALENDAR;
    }

    /**
     * Where a page's way out leads: to the surface the link was clicked on, or to the page's own
     * {@code fallback} when it was reached with no origin at all — from a list, or a typed URL.
     * <p>
     * <strong>Absent is not the same as {@code problems-calendar} here</strong>, which is why this
     * is not {@link #fromParam}. That method defaults a missing value so a hand-typed fix link still
     * renders a sensible Back link; doing the same here would send every ordinary booking and
     * cancel to the schedule-problems report, which is not where those journeys end.
     * <p>
     * One value for both ways out of a cancel page — its "Keep it" link and its redirect after a
     * successful cancel — so the two cannot disagree.
     */
    public static WayBack wayBack(String from, String day, WayBack fallback) {
        if (from == null || from.isBlank()) {
            return fallback;
        }
        LinkOrigin origin = fromParam(from);
        return new WayBack(origin.backHref(parse(day)), origin.place);
    }

    /**
     * Where a fix action should send Ted <em>after it succeeds</em> — empty when he did not arrive
     * from a link that named an origin, in which case the controller keeps its own default.
     */
    public static Optional<String> returnTo(String from) {
        if (from == null || from.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(fromParam(from).backHref());
    }

    /**
     * A carried {@code day}, passed on only when it is a date — so a form can put it back on its
     * POST without echoing whatever was typed into the URL.
     */
    public static String validDay(String day) {
        LocalDate parsed = parse(day);
        return parsed == null ? null : parsed.toString();
    }

    /**
     * {@code path} as a link clicked on this surface, on {@code day}: the page it opens reads both
     * back through {@link #wayBack}.
     */
    public String linkFrom(String path, LocalDate day) {
        return path + "?from=" + param + "&day=" + day;
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

    private String backHref(LocalDate day) {
        return dayParam == null || day == null ? backHref : backHref + "?" + dayParam + "=" + day;
    }

    private static LocalDate parse(String day) {
        if (day == null || day.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(day);
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }
}
