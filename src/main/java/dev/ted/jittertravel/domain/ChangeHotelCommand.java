package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Changes an existing booked hotel in place, keeping the same {@link HotelBookingId}. Validation
 * rules (same as booking, plus existence):
 * <ul>
 *   <li>The booking must already exist ({@link HotelBookingNotFound} otherwise).</li>
 *   <li>The new check-in date/time must be in the future ({@link CheckInNotInFuture}).</li>
 *   <li>Check-out must be at least one calendar day after check-in ({@link InvalidHotelDateRange}).</li>
 * </ul>
 * Emits a single {@link HotelChanged} event carrying the full new snapshot.
 */
public record ChangeHotelCommand(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent bookingIntent,
        String mapsUrl
) implements DomainCommand<ChangeHotelContext> {

    @Override
    public Stream<HotelChanged> execute(ChangeHotelContext context) {
        if (!context.bookingExists()) {
            throw new HotelBookingNotFound("No hotel booking exists with that id");
        }
        if (checkIn == null || !checkIn.isAfter(context.now())) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        if (checkOut == null || !checkOut.toLocalDate().isAfter(checkIn.toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }
        return Stream.of(new HotelChanged(hotelBookingId, hotelName, address, checkIn, checkOut,
                bookingIntent, mapsUrl));
    }
}