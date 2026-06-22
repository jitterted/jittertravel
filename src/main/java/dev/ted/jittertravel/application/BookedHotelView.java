package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;

import java.time.Instant;
import java.time.LocalDateTime;

public record BookedHotelView(
        HotelBookingId hotelBookingId,
        String hotelName,
        String city,
        String country,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        Instant checkOutInstant,
        BookingIntent status,
        String mapsUrl
) implements TemporalView {

    /**
     * A hotel stay is "upcoming" until the guest checks out. {@code checkOut} is
     * the entry-local wall-clock for display; {@code checkOutInstant} is the same
     * moment as a zone-independent {@link Instant}, so the FUTURE filter is correct
     * no matter where the hotel is or what zone the server runs in.
     */
    @Override
    public Instant relevantUntil() {
        return checkOutInstant;
    }
}
