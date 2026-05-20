package dev.ted.jittertravel.domain;

public class DepartureNotInFuture extends RuntimeException {
    public DepartureNotInFuture(String message) {
        super(message);
    }
}
