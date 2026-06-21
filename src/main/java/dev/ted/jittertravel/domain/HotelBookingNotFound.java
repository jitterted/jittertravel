package dev.ted.jittertravel.domain;

public class HotelBookingNotFound extends RuntimeException {
    public HotelBookingNotFound(String message) {
        super(message);
    }
}