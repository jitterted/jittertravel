package dev.ted.jittertravel.domain;

public class FlightItineraryNotFound extends RuntimeException {
    public FlightItineraryNotFound(String message) {
        super(message);
    }
}
