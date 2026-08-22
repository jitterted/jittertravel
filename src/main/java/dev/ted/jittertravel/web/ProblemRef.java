package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A stable reference to one {@link ScheduleProblem}, carried on a fix link as {@code ?problem=} so
 * the page it lands on can say <em>why you are here</em>.
 * <p>
 * A problem has no id, and inventing one would mean persisting identity for something that is
 * <em>derived</em> — {@code ScheduleGapProjector} recomputes every problem from the event stream on
 * every batch. So the reference is a function of the problem's own content, per variant, as an
 * exhaustive switch over the sealed interface — the same shape as {@link ProblemFix#forProblem} and
 * {@link ProblemBand#from}, so a new problem type cannot be added without deciding how it is
 * referenced.
 * <p>
 * The link carries the reference and never the words: a URL can be edited, and a banner that prints
 * whatever the URL says is a page that will confidently state something false about the schedule.
 * It also goes stale — fix the flight in another tab, come back, and the sentence would still
 * describe a problem that no longer exists. {@link ProblemContextLookup} resolves the key against
 * the live report instead, and renders nothing when it does not match.
 */
public record ProblemRef(String key) {

    private static final String SEPARATOR = "|";

    /**
     * The reference for {@code problem}. Two different problems must not collide, so each variant
     * names enough of itself to be unique — the ids where it has them, the cities and instants
     * where it does not.
     */
    public static ProblemRef of(ScheduleProblem problem) {
        return new ProblemRef(switch (problem) {
            case ScheduleProblem.MissingHotel missingHotel -> join(
                    "hotel", missingHotel.city(),
                    missingHotel.checkIn().toString(), missingHotel.checkOut().toString());
            case ScheduleProblem.MissingTravel missingTravel -> join(
                    "travel", missingTravel.fromCity(), missingTravel.arrivedAt().utc().toString(),
                    missingTravel.toCity(), missingTravel.nextDepartureAt().utc().toString());
            // The nights alone are not unique — two cities can be double-booked over the same run —
            // so the stays name themselves. They are already in a stable order: the detector builds
            // them in one pass over the same state.
            case ScheduleProblem.DuplicateHotel duplicateHotel -> join(
                    "dup", duplicateHotel.firstNight().toString(), duplicateHotel.lastNight().toString(),
                    duplicateHotel.stays().stream()
                            .map(stay -> stay.bookingId().id().toString())
                            .collect(Collectors.joining(",")));
            case ScheduleProblem.DifferentCityConflict cityConflict -> join(
                    "city", cityConflict.gatheringId().id().toString(),
                    cityConflict.conferenceId().id().toString(),
                    cityConflict.date().toString());
            // No fix link reaches this today — its two sides carry no ids, which is the
            // cause-linking gap — but the switch is exhaustive, and a future fix link for it will
            // want exactly this.
            case ScheduleProblem.SchedulingConflict clash -> join(
                    "clash", clash.first().name(), clash.first().startsAt().utc().toString(),
                    clash.second().name(), clash.second().startsAt().utc().toString());
        });
    }

    /** Whether {@code problem} is the one this reference names. */
    public boolean matches(ScheduleProblem problem) {
        return key.equals(of(problem).key());
    }

    /**
     * The problem this reference names, out of the ones the report currently holds — empty when the
     * key matches none of them, which is the ordinary outcome for a stale or hand-edited link.
     * <p>
     * Two callers read it: the banner ({@link ProblemContextLookup}) and the ground-transfer form,
     * which preselects its endpoints from the gap. They must agree about which problem a link names,
     * so they resolve it the same way.
     */
    public Optional<ScheduleProblem> findIn(List<ScheduleProblem> problems) {
        return problems.stream().filter(this::matches).findFirst();
    }

    private static String join(String... parts) {
        return String.join(SEPARATOR, parts);
    }
}
