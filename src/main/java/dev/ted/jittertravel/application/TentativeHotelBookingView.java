package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.HotelBookingId;

import java.time.LocalDateTime;

public record TentativeHotelBookingView(
        HotelBookingId hotelBookingId,
        String hotelName,
        String city,
        String country,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        boolean hasOverlap
) {
}
