package dev.ted.jittertravel.domain;

public class InvalidGroundTransferTimeRange extends RuntimeException {
    public InvalidGroundTransferTimeRange(String message) {
        super(message);
    }
}
