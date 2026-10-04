package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryCommand;
import dev.ted.jittertravel.domain.ChangeFlightItineraryContext;
import dev.ted.jittertravel.domain.ItineraryChangePlan;

import java.util.List;

/**
 * Everything the paste-an-itinerary page needs to say about one paste, in the order it is decided:
 * whether the text could be read at all ({@code parseProblems}), which airports still need a zone
 * picked ({@code unresolvedAirports}), and — once both are clear — what the booking rules make of
 * each leg ({@link Leg#refusal}). {@code command} is present only when every one of those is clear,
 * and is exactly what booking would execute.
 * <p>
 * A paste whose confirmation code is already a live itinerary is a schedule change, not a booking:
 * {@code scheduleChange} is then present, {@code legs} and {@code command} are empty, and the diff
 * stands in for both.
 */
public record ItineraryEvaluation(
        List<String> parseProblems,
        String confirmationCode,
        List<Leg> legs,
        List<AirportCode> unresolvedAirports,
        BookFlightItineraryCommand command,
        boolean alreadyBooked,
        ScheduleChange scheduleChange
) {

    /** Every evaluation but "this form was already booked" — the common shape. */
    public ItineraryEvaluation(List<String> parseProblems, String confirmationCode, List<Leg> legs,
                               List<AirportCode> unresolvedAirports, BookFlightItineraryCommand command) {
        this(parseProblems, confirmationCode, legs, unresolvedAirports, command, false, null);
    }

    public ItineraryEvaluation(List<String> parseProblems, String confirmationCode, List<Leg> legs,
                               List<AirportCode> unresolvedAirports, BookFlightItineraryCommand command,
                               boolean alreadyBooked) {
        this(parseProblems, confirmationCode, legs, unresolvedAirports, command, alreadyBooked, null);
    }

    public ItineraryEvaluation {
        parseProblems = List.copyOf(parseProblems);
        legs = List.copyOf(legs);
        unresolvedAirports = List.copyOf(unresolvedAirports);
        if (confirmationCode == null) {
            confirmationCode = "";
        }
    }

    /** One leg as read, and the rule it broke; {@code refusal} is null while it breaks none. */
    public record Leg(PastedItinerary.Leg pasted, RuntimeException refusal) {
        public boolean refused() {
            return refusal != null;
        }
    }

    /**
     * What applying the paste to the itinerary it matched would do. {@code command} is what
     * applying would execute; {@code plan} is the same computation, shown as a diff.
     */
    public record ScheduleChange(ChangeFlightItineraryCommand command, ChangeFlightItineraryContext context,
                                 ItineraryChangePlan plan) {
        /** Nothing is refused and something differs: the only state worth a button. */
        public boolean applicable() {
            return plan.clean() && plan.hasChanges();
        }
    }

    static ItineraryEvaluation unparseable(List<String> problems) {
        return new ItineraryEvaluation(problems, "", List.of(), List.of(), null);
    }

    static ItineraryEvaluation scheduleChange(String confirmationCode, ScheduleChange change) {
        return new ItineraryEvaluation(List.of(), confirmationCode, List.of(), List.of(), null, false, change);
    }

    /**
     * This form's itinerary id was already booked: nothing was evaluated and nothing was written,
     * because the trip the reader asked for is already on the books. Not {@link #bookable()} — there is
     * no command to run — but not a refusal either.
     */
    static ItineraryEvaluation theFormWasAlreadyBooked() {
        return new ItineraryEvaluation(List.of(), "", List.of(), List.of(), null, true, null);
    }

    public boolean bookable() {
        return command != null;
    }

    /** The paste named an itinerary that is already booked, so it is a change to it. */
    public boolean isScheduleChange() {
        return scheduleChange != null;
    }

    /** The paste is a change that can be applied. */
    public boolean changeable() {
        return scheduleChange != null && scheduleChange.applicable();
    }
}
