package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record HotelBooked(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements Event {
    public HotelBooked {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}
