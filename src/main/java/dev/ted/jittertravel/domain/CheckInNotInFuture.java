package dev.ted.jittertravel.domain;

public class CheckInNotInFuture extends RuntimeException {
    public CheckInNotInFuture(String message) {
        super(message);
    }
}
