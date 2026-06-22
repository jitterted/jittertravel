package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record BookHotelCommand(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements DomainCommand<BookHotelContext> {

    @Override
    public Stream<HotelBooked> execute(BookHotelContext context) {
        // "In the future" is an instant comparison (zone-independent). The calendar-day range is
        // checked in the entry zone, never UTC, so a stay never collapses across a UTC midnight.
        if (checkIn == null || !checkIn.utc().isAfter(context.now())) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        if (checkOut == null
                || !checkOut.localDateTime().toLocalDate().isAfter(checkIn.localDateTime().toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }
        return Stream.of(new HotelBooked(hotelBookingId, hotelName, address, checkIn, checkOut, bookingIntent, mapsUrl));
    }
}
