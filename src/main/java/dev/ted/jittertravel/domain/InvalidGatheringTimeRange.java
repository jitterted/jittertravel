package dev.ted.jittertravel.domain;

public class InvalidGatheringTimeRange extends RuntimeException {
    public InvalidGatheringTimeRange(String message) {
        super(message);
    }
}
