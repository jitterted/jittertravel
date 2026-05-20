package dev.ted.jittertravel.domain;

public class FlightNotFound extends RuntimeException {
    public FlightNotFound(String message) {
        super(message);
    }
}
