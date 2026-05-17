package dev.ted.jittertravel.domain;

public class InvalidDateRange extends RuntimeException {
    public InvalidDateRange(String message) {
        super(message);
    }
}
