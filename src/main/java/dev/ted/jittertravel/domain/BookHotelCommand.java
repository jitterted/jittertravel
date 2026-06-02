package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;
import java.util.stream.Stream;

public record BookHotelCommand(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent bookingIntent
) implements DomainCommand<BookHotelContext> {

    @Override
    public Stream<HotelBooked> execute(BookHotelContext context) {
        if (checkIn == null || !checkIn.isAfter(context.now())) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        if (checkOut == null || !checkOut.toLocalDate().isAfter(checkIn.toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }
        return Stream.of(new HotelBooked(hotelBookingId, hotelName, address, checkIn, checkOut, bookingIntent));
    }
}
