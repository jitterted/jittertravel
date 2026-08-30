package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Changes an existing booked hotel in place, keeping the same {@link HotelBookingId}. Validation
 * rules (same as booking, plus existence):
 * <ul>
 *   <li>The booking must already exist ({@link HotelBookingNotFound} otherwise).</li>
 *   <li>The hotel must have a name, and a city that is really a city
 *       ({@link InvalidLocationEntry}; see {@link EnteredLocation}).</li>
 *   <li>The new check-in date/time must be in the future ({@link CheckInNotInFuture}).</li>
 *   <li>Check-out must be at least one calendar day after check-in ({@link InvalidHotelDateRange}).</li>
 *   <li>An optional cancel-by deadline must not fall after check-in ({@link InvalidCancelByDate}).</li>
 * </ul>
 * Emits a single {@link HotelChanged} event carrying the full new snapshot — including
 * {@code cancelBy}, which a caller that omits it therefore clears.
 */
public record ChangeHotelCommand(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl,
        ZonedTimestamp cancelBy
) implements DomainCommand<ChangeHotelContext> {

    @Override
    public Stream<HotelChanged> execute(ChangeHotelContext context) {
        if (!context.bookingExists()) {
            throw new HotelBookingNotFound("No hotel booking exists with that id");
        }
        EnteredLocation.of(hotelName, address).check(LocationRole.STAY);
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
        if (cancelBy != null && cancelBy.utc().isAfter(checkIn.utc())) {
            throw new InvalidCancelByDate("Cancel-by deadline must not be after check-in");
        }
        return Stream.of(new HotelChanged(hotelBookingId, hotelName, address, checkIn, checkOut,
                bookingIntent, mapsUrl, cancelBy));
    }
}
