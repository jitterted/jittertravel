package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HotelItineraryEntry(
        String hotelName,
        Address address,
        BookingIntent bookingIntent,
        HotelDayRole dayRole,
        LocalDateTime anchorDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.LODGING; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
