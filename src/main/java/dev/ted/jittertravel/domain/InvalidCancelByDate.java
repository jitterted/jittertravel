package dev.ted.jittertravel.domain;

public class InvalidCancelByDate extends RuntimeException {
    public InvalidCancelByDate(String message) {
        super(message);
    }
}
