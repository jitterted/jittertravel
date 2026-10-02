package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.PastedItinerary;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.OverlapsAnotherItineraryLeg;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * An {@link ItineraryEvaluation} in the page's words. The evaluation decides; this only says it.
 * <p>
 * Problems land where they are fixed (the rejected-form rules, CLAUDE.md): what could not be read
 * goes under the text box, since re-pasting fixes it; an unknown airport goes under its own zone
 * picker; a leg the booking rules refuse is marked on its own row, because the fix — cancelling the
 * flight it collides with, say — is somewhere else, and the overlap links to it. The count at the
 * top uses the shared sentence from {@link FormErrors}.
 */
public record ItineraryPreview(
        String parseProblem,
        String confirmationCode,
        List<Row> rows,
        List<String> unresolvedAirports,
        boolean bookable
) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);

    /**
     * One leg. Times are the wall clock at each airport, as the email prints them.
     * {@code problem}, {@code linkPath} and {@code linkLabel} are {@code ""} when there is nothing
     * to say; the link is only ever an overlapping flight or train already booked.
     */
    public record Row(int number, String flightNumber, String route, String departs, String arrives,
                      String problem, String linkPath, String linkLabel) {
        public boolean hasProblem() {
            return !problem.isEmpty();
        }

        public boolean hasLink() {
            return !linkPath.isEmpty();
        }
    }

    public static ItineraryPreview from(ItineraryEvaluation evaluation) {
        return new ItineraryPreview(
                String.join("; ", evaluation.parseProblems()),
                evaluation.confirmationCode(),
                evaluation.legs().stream().map(ItineraryPreview::row).toList(),
                evaluation.unresolvedAirports().stream().map(AirportCode::code).toList(),
                evaluation.bookable());
    }

    /** Every problem on the page: the paste's (one input), each unknown airport, each refused leg. */
    public int problemCount() {
        return (parseProblem.isEmpty() ? 0 : 1)
               + unresolvedAirports.size()
               + (int) rows.stream().filter(Row::hasProblem).count();
    }

    public String problemSummary() {
        return problemCount() == 0 ? "" : FormErrors.countSentence(problemCount());
    }

    public int legCount() {
        return rows.size();
    }

    private static Row row(ItineraryEvaluation.Leg leg) {
        PastedItinerary.Leg pasted = leg.pasted();
        String problem = "";
        String linkPath = "";
        String linkLabel = "";
        if (leg.refused()) {
            switch (leg.refusal()) {
                case OverlappingLegRefused overlap -> {
                    OverlappingLegNotice notice = OverlappingLegNotice.from(overlap);
                    problem = notice.message();
                    linkPath = notice.path();
                    linkLabel = notice.label();
                }
                case OverlapsAnotherItineraryLeg other ->
                        problem = "Overlaps flight " + other.otherLegNumber() + " of this itinerary";
                case DepartureNotInFuture ignored -> problem = "Already departed";
                case InvalidDateRange ignored -> problem = "Arrives before it departs";
                default -> problem = leg.refusal().getMessage();
            }
        }
        return new Row(pasted.number(), pasted.flightNumber(),
                pasted.departureAirport().code() + "→" + pasted.arrivalAirport().code(),
                WHEN.format(pasted.departureLocal()), WHEN.format(pasted.arrivalLocal()),
                problem, linkPath, linkLabel);
    }
}
