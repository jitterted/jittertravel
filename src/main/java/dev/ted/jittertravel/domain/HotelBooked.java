package dev.ted.jittertravel.domain;

public record HotelBooked(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements Event {
    public HotelBooked {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}
