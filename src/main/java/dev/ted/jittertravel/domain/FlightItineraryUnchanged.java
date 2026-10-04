package dev.ted.jittertravel.domain;

/**
 * Every leg in the paste already matches what is booked, so there is nothing to record. Thrown
 * rather than emitting an empty change, which would only be noise in the log.
 */
public class FlightItineraryUnchanged extends RuntimeException {
    public FlightItineraryUnchanged(FlightItineraryId itineraryId) {
        super("Nothing to change in itinerary " + itineraryId);
    }
}
