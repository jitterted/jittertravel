package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;

import java.time.LocalDateTime;

public record HotelItineraryEntry(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        BookingIntent bookingIntent,
        HotelDayRole dayRole,
        LocalDateTime anchorDateTime,
        String mapsUrl
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.LODGING; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
