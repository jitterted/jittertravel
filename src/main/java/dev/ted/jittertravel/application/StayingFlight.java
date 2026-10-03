package dev.ted.jittertravel.application;

/**
 * A live flight that Cancel Itinerary leaves booked even though it falls between the itinerary's
 * own flights: a separate itinerary nested in a gap, or a flight entered by hand.
 *
 * @param confirmationCode the code of the itinerary it belongs to, or {@code ""} for a flight
 *                         entered by hand
 */
public record StayingFlight(
        BookedFlightView flight,
        String confirmationCode
) {
    public StayingFlight {
        if (confirmationCode == null) {
            confirmationCode = "";
        }
    }
}
