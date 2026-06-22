package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

public record BookedHotelView(
        HotelBookingId hotelBookingId,
        String hotelName,
        String city,
        String country,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent status,
        String mapsUrl
) implements TemporalView {

    /**
     * A hotel stay is "upcoming" until the guest checks out. Each {@link ZonedTimestamp} keeps
     * both the UTC instant (used here, so the FUTURE filter is correct no matter where the hotel
     * is or what zone the server runs in) and the entry zone (used to render the wall-clock the
     * traveler entered).
     */
    @Override
    public Instant relevantUntil() {
        return checkOut.utc();
    }
}
