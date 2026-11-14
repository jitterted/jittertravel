package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookFlightItineraryCommand;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.util.List;

/**
 * Everything the paste-an-itinerary page needs to say about one paste, in the order it is decided:
 * whether the text could be read at all ({@code parseProblems}), which airports still need a zone
 * picked ({@code unresolvedAirports}), and — once both are clear — what the booking rules make of
 * each leg ({@link Leg#refusal}). {@code command} is present only when every one of those is clear,
 * and is exactly what booking would execute.
 * <p>
 * <strong>A paste whose confirmation code is already a live itinerary is refused, never applied</strong>
 * ({@code docs/FlightItineraryRebookingPlan.md} D4, D11): {@code onTheBooks} is then present and
 * names it, so the page can send Ted to that itinerary's change page with the email. Book only ever
 * books. Until 2026-11-14 such a paste quietly became a schedule change here, which Ted found
 * "completely non-intuitive".
 */
public record ItineraryEvaluation(
        List<String> parseProblems,
        String confirmationCode,
        List<Leg> legs,
        List<AirportCode> unresolvedAirports,
        BookFlightItineraryCommand command,
        boolean alreadyBooked,
        OnTheBooks onTheBooks
) {

    /** Every evaluation but "this form was already booked" — the common shape. */
    public ItineraryEvaluation(List<String> parseProblems, String confirmationCode, List<Leg> legs,
                               List<AirportCode> unresolvedAirports, BookFlightItineraryCommand command) {
        this(parseProblems, confirmationCode, legs, unresolvedAirports, command, false, null);
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
     * The live itinerary a pasted code already names: enough to say which one it is ("4 legs, Nov 7
     * to Nov 26") and where to change it. {@code legCount} counts the legs still flying, from the
     * first one's departure to the last one's arrival.
     */
    public record OnTheBooks(FlightItineraryId itineraryId, int legCount,
                             ZonedTimestamp departs, ZonedTimestamp arrives) {
    }

    static ItineraryEvaluation unparseable(List<String> problems) {
        return new ItineraryEvaluation(problems, "", List.of(), List.of(), null);
    }

    static ItineraryEvaluation alreadyOnTheBooks(String confirmationCode, OnTheBooks onTheBooks) {
        return new ItineraryEvaluation(List.of(), confirmationCode, List.of(), List.of(), null, false, onTheBooks);
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

    /** The paste's code names an itinerary already booked, so Book refuses it and points at its change page. */
    public boolean isOnTheBooks() {
        return onTheBooks != null;
    }
}
