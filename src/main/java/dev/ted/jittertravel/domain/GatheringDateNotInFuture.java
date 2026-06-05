package dev.ted.jittertravel.domain;

public class GatheringDateNotInFuture extends RuntimeException {
    public GatheringDateNotInFuture(String message) {
        super(message);
    }
}
