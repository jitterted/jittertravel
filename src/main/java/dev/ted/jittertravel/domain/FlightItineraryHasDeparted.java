package dev.ted.jittertravel.domain;

/**
 * The itinerary cannot be cancelled as a whole because a leg has already left. Cancelling the rest
 * is still possible, one flight at a time with Cancel Flight (Ted, 2026-09-30).
 */
public class FlightItineraryHasDeparted extends RuntimeException {
    public FlightItineraryHasDeparted(String message) {
        super(message);
    }
}
