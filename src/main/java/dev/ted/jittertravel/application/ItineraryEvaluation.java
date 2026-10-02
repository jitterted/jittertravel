package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;

import java.util.List;

/**
 * Everything the paste-an-itinerary page needs to say about one paste, in the order it is decided:
 * whether the text could be read at all ({@code parseProblems}), which airports still need a zone
 * picked ({@code unresolvedAirports}), and — once both are clear — what the booking rules make of
 * each leg ({@link Leg#refusal}). {@code command} is present only when every one of those is clear,
 * and is exactly what booking would execute.
 */
public record ItineraryEvaluation(
        List<String> parseProblems,
        String confirmationCode,
        List<Leg> legs,
        List<AirportCode> unresolvedAirports,
        BookFlightItineraryCommand command,
        boolean alreadyBooked
) {

    /** Every evaluation but "this form was already booked" — the common shape. */
    public ItineraryEvaluation(List<String> parseProblems, String confirmationCode, List<Leg> legs,
                               List<AirportCode> unresolvedAirports, BookFlightItineraryCommand command) {
        this(parseProblems, confirmationCode, legs, unresolvedAirports, command, false);
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

    static ItineraryEvaluation unparseable(List<String> problems) {
        return new ItineraryEvaluation(problems, "", List.of(), List.of(), null);
    }

    /**
     * This form's itinerary id was already booked: nothing was evaluated and nothing was written,
     * because the trip the reader asked for is already on the books. Not {@link #bookable()} — there is
     * no command to run — but not a refusal either.
     */
    static ItineraryEvaluation theFormWasAlreadyBooked() {
        return new ItineraryEvaluation(List.of(), "", List.of(), List.of(), null, true);
    }

    public boolean bookable() {
        return command != null;
    }
}
