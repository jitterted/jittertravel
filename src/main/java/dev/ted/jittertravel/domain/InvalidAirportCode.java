package dev.ted.jittertravel.domain;

public class InvalidAirportCode extends RuntimeException {
    public InvalidAirportCode(String message) {
        super(message);
    }
}
