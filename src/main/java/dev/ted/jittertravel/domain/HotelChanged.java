package dev.ted.jittertravel.domain;

public record HotelChanged(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements Event {
    public HotelChanged {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}