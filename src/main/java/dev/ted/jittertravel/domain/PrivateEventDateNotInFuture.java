package dev.ted.jittertravel.domain;

public class PrivateEventDateNotInFuture extends RuntimeException {
    public PrivateEventDateNotInFuture(String message) {
        super(message);
    }
}
