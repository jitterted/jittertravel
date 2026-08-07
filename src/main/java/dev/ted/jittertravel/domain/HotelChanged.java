package dev.ted.jittertravel.domain;

/**
 * The full new snapshot of a hotel booking after an edit. {@code cancelBy} carries the same meaning
 * as on {@link HotelBooked}: the free-cancellation deadline in the hotel's zone, {@code null} when
 * none is recorded. Because this event is a full snapshot, an edit that omits {@code cancelBy}
 * clears it — the edit form must always round-trip the current value.
 */
public record HotelChanged(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl,
        ZonedTimestamp cancelBy
) implements Event {
    public HotelChanged {
        if (mapsUrl == null) {
            mapsUrl = "";
        }
    }
}
