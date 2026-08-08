package dev.ted.jittertravel.domain;

public class CannotCancelAfterCheckIn extends RuntimeException {
    public CannotCancelAfterCheckIn(String message) {
        super(message);
    }
}
