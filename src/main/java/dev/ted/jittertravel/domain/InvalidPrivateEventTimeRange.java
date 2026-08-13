package dev.ted.jittertravel.domain;

public class InvalidPrivateEventTimeRange extends RuntimeException {
    public InvalidPrivateEventTimeRange(String message) {
        super(message);
    }
}
