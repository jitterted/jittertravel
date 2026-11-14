package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.PastedItinerary;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.OverlapsAnotherItineraryLeg;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * An {@link ItineraryEvaluation} in the Book page's words. The evaluation decides; this only says it.
 * <p>
 * Problems land where they are fixed (the rejected-form rules, CLAUDE.md): what could not be read
 * goes under the text box, since re-pasting fixes it; an unknown airport goes under its own zone
 * picker; a leg the booking rules refuse is marked on its own row, because the fix — cancelling the
 * flight it collides with, say — is somewhere else, and the overlap links to it. The count at the
 * top uses the shared sentence from {@link FormErrors}.
 * <p>
 * A code already on the books is not a problem with the paste but a wrong door: {@code onTheBooks}
 * says which itinerary it is and {@code changePath} where its change page is, and the page offers
 * to carry the email there ({@code docs/FlightItineraryRebookingPlan.md} D4, D11).
 */
public record ItineraryPreview(
        String parseProblem,
        String confirmationCode,
        List<Row> rows,
        List<String> unresolvedAirports,
        boolean bookable,
        String onTheBooks,
        String changePath
) {

    static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    /**
     * One leg. Times are the wall clock at each airport, as the email prints them.
     * {@code problem}, {@code linkPath} and {@code linkLabel} are {@code ""} when there is nothing
     * to say; the link is only ever an overlapping flight or train already booked.
     * {@code status} is {@code ""} on the Book page and says what happens to the leg on the change
     * page; {@code number} is 0 for a leg the change drops.
     */
    public record Row(int number, String flightNumber, String route, String departs, String arrives,
                      String problem, String linkPath, String linkLabel, String status, Before before) {
        public boolean hasProblem() {
            return !problem.isEmpty();
        }

        public boolean hasLink() {
            return !linkPath.isEmpty();
        }

        public boolean hasStatus() {
            return !status.isEmpty();
        }

        /** The status as a CSS-safe word: {@code moved}, {@code added}, … */
        public String statusClass() {
            return status.toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * What a moved leg used to say, one field per cell it is shown in; {@code ""} where that part
     * did not change, so only the cells that differ carry a struck-through old value.
     */
    public record Before(String flightNumber, String route, String departs, String arrives) {
        static final Before NONE = new Before("", "", "", "");

        public boolean hasFlightNumber() {
            return !flightNumber.isEmpty();
        }

        public boolean hasRoute() {
            return !route.isEmpty();
        }

        public boolean hasDeparts() {
            return !departs.isEmpty();
        }

        public boolean hasArrives() {
            return !arrives.isEmpty();
        }
    }

    public static ItineraryPreview from(ItineraryEvaluation evaluation) {
        if (evaluation.isOnTheBooks()) {
            ItineraryEvaluation.OnTheBooks known = evaluation.onTheBooks();
            return new ItineraryPreview("", evaluation.confirmationCode(), List.of(), List.of(), false,
                    evaluation.confirmationCode() + " is already booked: " + legs(known.legCount()) + ", "
                    + DAY.format(known.departs().atEntryZone()) + " to " + DAY.format(known.arrives().atEntryZone())
                    + ". To record a change to it, open the itinerary instead. The email you pasted comes with you.",
                    "/booked-itineraries/" + known.itineraryId().id() + "/change");
        }
        return new ItineraryPreview(
                String.join("; ", evaluation.parseProblems()),
                evaluation.confirmationCode(),
                evaluation.legs().stream().map(ItineraryPreview::row).toList(),
                evaluation.unresolvedAirports().stream().map(AirportCode::code).toList(),
                evaluation.bookable(), "", "");
    }

    private static String legs(int count) {
        return count + (count == 1 ? " leg" : " legs");
    }

    public boolean isOnTheBooks() {
        return !onTheBooks.isEmpty();
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
        return row(pasted.number(), pasted.flightNumber(),
                pasted.departureAirport(), pasted.arrivalAirport(),
                pasted.departureLocal(), pasted.arrivalLocal(), leg.refusal(), "", Before.NONE);
    }

    /** Shared with {@link ItineraryChangePreview}: one wording for a refused leg, on both pages. */
    static Row row(int number, String flightNumber, AirportCode departure, AirportCode arrival,
                   LocalDateTime departs, LocalDateTime arrives, RuntimeException refusal,
                   String status, Before before) {
        String problem = "";
        String linkPath = "";
        String linkLabel = "";
        if (refusal != null) {
            switch (refusal) {
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
                default -> problem = refusal.getMessage();
            }
        }
        return new Row(number, flightNumber, departure.code() + "→" + arrival.code(),
                WHEN.format(departs), WHEN.format(arrives), problem, linkPath, linkLabel, status, before);
    }
}
