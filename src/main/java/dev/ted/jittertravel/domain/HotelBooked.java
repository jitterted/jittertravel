package dev.ted.jittertravel.domain;

/**
 * A hotel stay was booked. {@code cancelBy} is the free-cancellation deadline, in the hotel's own
 * zone (the same zone as {@link #checkIn()}); {@code null} means no deadline was recorded. It is
 * advisory only — nothing keys off it but the display.
 */
public record HotelBooked(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl,
        ZonedTimestamp cancelBy
) implements Event {
    public HotelBooked {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}
