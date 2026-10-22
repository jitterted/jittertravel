package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

public record BookHotelCommand(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent bookingIntent,
        String mapsUrl,
        ZonedTimestamp cancelBy
) implements DomainCommand<BookHotelContext> {

    /**
     * What the form alone can get wrong is refused here, before the command exists, so it never
     * reaches the command log: a command that was never valid is invalid data, not a domain
     * refusal (Ted, 2026-10-22; {@code docs/ValidateBeforeTheWalPlan.md}). Only what needs
     * state — the check-in being in the future — stays in {@link #execute}, where the refusal is
     * recorded.
     */
    public BookHotelCommand {
        // Checked before the dates: a hotel name pasted into the city field makes the stay match
        // the wrong place on /schedule-problems, and unlike a bad date it looks right on the page.
        EnteredLocation.of(hotelName, address).check(LocationRole.STAY);
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        // The calendar-day range is checked in the entry zone, never UTC, so a stay never
        // collapses across a UTC midnight.
        if (!checkOut.localDateTime().toLocalDate().isAfter(checkIn.localDateTime().toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }
        // The deadline is optional; when present it is a moment, so compare instants — a hotel and
        // its deadline share one zone today, but the rule must not depend on that.
        if (cancelBy != null && cancelBy.utc().isAfter(checkIn.utc())) {
            throw new InvalidCancelByDate("Cancel-by deadline must not be after check-in");
        }
    }

    @Override
    public Stream<HotelBooked> execute(BookHotelContext context) {
        // "In the future" is an instant comparison (zone-independent).
        if (!checkIn.utc().isAfter(context.now())) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        return Stream.of(new HotelBooked(hotelBookingId, hotelName, address, checkIn, checkOut,
                bookingIntent, mapsUrl, cancelBy));
    }
}
