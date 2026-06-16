package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;

import java.time.LocalDateTime;

public record BookedHotelView(
        HotelBookingId hotelBookingId,
        String hotelName,
        String city,
        String country,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent status,
        String mapsUrl
) implements TemporalView {

    /** A hotel stay is "upcoming" until the guest checks out. */
    @Override
    public LocalDateTime relevantUntil() {
        return checkOut;
    }
}
