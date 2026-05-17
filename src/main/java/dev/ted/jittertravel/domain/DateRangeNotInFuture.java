package dev.ted.jittertravel.domain;

public class DateRangeNotInFuture extends RuntimeException {
    public DateRangeNotInFuture(String message) {
        super(message);
    }
}
