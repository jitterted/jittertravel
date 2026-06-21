package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record HotelChanged(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements Event {
    public HotelChanged {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}