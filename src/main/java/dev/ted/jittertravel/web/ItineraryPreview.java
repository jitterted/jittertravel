package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.PastedItinerary;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.ItineraryChangePlan;
import dev.ted.jittertravel.domain.ItineraryChangePlan.LegDiff;
import dev.ted.jittertravel.domain.ItineraryLeg;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.OverlapsAnotherItineraryLeg;

import java.time.LocalDateTime;
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
 * <p>
 * A schedule change ({@code scheduleChange}) is the same table read as a diff: each row says what
 * happens to that leg, and a moved leg shows what it was struck through above what it is now. {@code changeable} is the only state that
 * earns the Apply button; a change with nothing different says so instead.
 */
public record ItineraryPreview(
        String parseProblem,
        String confirmationCode,
        List<Row> rows,
        List<String> unresolvedAirports,
        boolean bookable,
        boolean scheduleChange,
        boolean changeable
) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);

    /**
     * One leg. Times are the wall clock at each airport, as the email prints them.
     * {@code problem}, {@code linkPath} and {@code linkLabel} are {@code ""} when there is nothing
     * to say; the link is only ever an overlapping flight or train already booked.
     * {@code status} is {@code ""} outside a schedule change; {@code number} is 0 for a leg the
     * paste no longer mentions.
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
        if (evaluation.isScheduleChange()) {
            ItineraryEvaluation.ScheduleChange change = evaluation.scheduleChange();
            return new ItineraryPreview("", evaluation.confirmationCode(),
                    change.plan().diffs().stream().map(ItineraryPreview::row).toList(),
                    List.of(), false, true, change.applicable());
        }
        return new ItineraryPreview(
                String.join("; ", evaluation.parseProblems()),
                evaluation.confirmationCode(),
                evaluation.legs().stream().map(ItineraryPreview::row).toList(),
                evaluation.unresolvedAirports().stream().map(AirportCode::code).toList(),
                evaluation.bookable(), false, false);
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

    /** A schedule change whose paste matches what is booked: no problem, and nothing to apply. */
    public boolean nothingToChange() {
        return scheduleChange && !changeable && problemCount() == 0;
    }

    private static Row row(ItineraryEvaluation.Leg leg) {
        PastedItinerary.Leg pasted = leg.pasted();
        return row(pasted.number(), pasted.flightNumber(),
                pasted.departureAirport(), pasted.arrivalAirport(),
                pasted.departureLocal(), pasted.arrivalLocal(), leg.refusal(), "", Before.NONE);
    }

    private static Row row(LegDiff diff) {
        ItineraryLeg shown = diff.effective();
        return row(diff.pasteNumber(), shown.flightNumber(),
                shown.departureAirport(), shown.arrivalAirport(),
                shown.departureDateTime().localDateTime(), shown.arrivalDateTime().localDateTime(),
                diff.refusal(), statusOf(diff.kind()), before(diff));
    }

    private static String statusOf(ItineraryChangePlan.Kind kind) {
        return switch (kind) {
            case UNCHANGED -> "Unchanged";
            case CHANGED -> "Moved";
            case ADDED -> "Added";
            case REMOVED -> "Removed";
        };
    }

    /** What a changed leg used to be, only for the parts that differ. */
    private static Before before(LegDiff diff) {
        if (diff.kind() != ItineraryChangePlan.Kind.CHANGED) {
            return Before.NONE;
        }
        ItineraryLeg before = diff.before();
        ItineraryLeg after = diff.after();
        boolean routeDiffers = !before.departureAirport().equals(after.departureAirport())
                               || !before.arrivalAirport().equals(after.arrivalAirport());
        boolean departsDiffers = !before.departureDateTime().utc().equals(after.departureDateTime().utc());
        boolean arrivesDiffers = !before.arrivalDateTime().utc().equals(after.arrivalDateTime().utc());
        return new Before(
                before.flightNumber().equals(after.flightNumber()) ? "" : before.flightNumber(),
                routeDiffers ? before.departureAirport().code() + "→" + before.arrivalAirport().code() : "",
                departsDiffers ? WHEN.format(before.departureDateTime().localDateTime()) : "",
                arrivesDiffers ? WHEN.format(before.arrivalDateTime().localDateTime()) : "");
    }

    private static Row row(int number, String flightNumber, AirportCode departure, AirportCode arrival,
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
