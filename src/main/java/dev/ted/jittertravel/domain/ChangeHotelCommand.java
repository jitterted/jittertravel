package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Changes an existing booked hotel in place, keeping the same {@link HotelBookingId}. Validation
 * rules (same as booking, plus existence), in two places:
 * <ul>
 *   <li>As the command is built — the hotel must have a name, and a city that is really a city
 *       ({@link InvalidLocationEntry}; see {@link EnteredLocation}); check-out must be at least
 *       one calendar day after check-in ({@link InvalidHotelDateRange}); an optional cancel-by
 *       deadline must not fall after check-in ({@link InvalidCancelByDate}). These need nothing
 *       beyond the form, so they are data entry and never reach the command log (Ted,
 *       2026-10-22; {@code docs/archived/ValidateBeforeTheWalPlan.md}).</li>
 *   <li>In {@link #execute}, recorded — the booking must already exist
 *       ({@link HotelBookingNotFound}) and the new check-in must be in the future
 *       ({@link CheckInNotInFuture}).</li>
 * </ul>
 * A data-entry mistake is therefore reported even for a booking that does not exist. That only
 * matters for a hand-edited URL, since the edit form is reached from a row that exists, and Ted
 * accepted it for the sake of one rule for every command.
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

    public ChangeHotelCommand {
        EnteredLocation.of(hotelName, address).check(LocationRole.STAY);
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        // The calendar-day range is checked in the entry zone, never UTC, so a stay never
        // collapses across a UTC midnight.
        if (!checkOut.localDateTime().toLocalDate().isAfter(checkIn.localDateTime().toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }
        if (cancelBy != null && cancelBy.utc().isAfter(checkIn.utc())) {
            throw new InvalidCancelByDate("Cancel-by deadline must not be after check-in");
        }
    }

    @Override
    public Stream<HotelChanged> execute(ChangeHotelContext context) {
        if (!context.bookingExists()) {
            throw new HotelBookingNotFound("No hotel booking exists with that id");
        }
        // "In the future" is an instant comparison (zone-independent).
        if (!checkIn.utc().isAfter(context.now())) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        return Stream.of(new HotelChanged(hotelBookingId, hotelName, address, checkIn, checkOut,
                bookingIntent, mapsUrl, cancelBy));
    }
}
