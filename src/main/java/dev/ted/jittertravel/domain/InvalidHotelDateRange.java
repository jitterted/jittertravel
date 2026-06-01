package dev.ted.jittertravel.domain;

public class InvalidHotelDateRange extends RuntimeException {
    public InvalidHotelDateRange(String message) {
        super(message);
    }
}
