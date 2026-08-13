package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

/**
 * A booked hotel as shown on {@code /booked-hotels}.
 * <p>
 * {@code cancelBy} is the free-cancellation deadline ({@code null} when none was recorded), and
 * {@code cancelDeadlinePassed} says whether it is behind us. The flag is resolved in
 * {@link BookedHotelsProjector#views} rather than by the renderer because {@code now} enters at the
 * controller boundary and renderers take no clock. Both are display-only: a stay is cancellable at
 * any time no matter what the deadline says.
 * <p>
 * {@code cancelled} marks a stay that has been cancelled. Unlike every other hotel read model, this
 * one keeps a tombstone row rather than dropping the booking, so the list can show what happened;
 * {@code cancellationReason} is the free text from the cancellation ({@code ""} when none was
 * given).
 */
public record BookedHotelView(
        HotelBookingId hotelBookingId,
        String hotelName,
        String city,
        String country,
        ZonedTimestamp checkIn,
        ZonedTimestamp checkOut,
        BookingIntent status,
        String mapsUrl,
        ZonedTimestamp cancelBy,
        boolean cancelDeadlinePassed,
        boolean cancelled,
        String cancellationReason
) implements TemporalView {

    public BookedHotelView {
        if (cancellationReason == null) {
            cancellationReason = "";
        }
    }

    /**
     * A hotel stay is "upcoming" until the guest checks out — cancelled stays included, so a
     * cancellation you just made is visible in the default FUTURE view instead of only under ALL.
     * Each {@link ZonedTimestamp} keeps both the UTC instant (used here, so the FUTURE filter is
     * correct no matter where the hotel is or what zone the server runs in) and the entry zone
     * (used to render the wall-clock the traveler entered).
     */
    @Override
    public Instant relevantUntil() {
        return checkOut.utc();
    }

    /** A copy with the advisory deadline evaluated against {@code now}. */
    BookedHotelView withDeadlineEvaluatedAt(Instant now) {
        return new BookedHotelView(hotelBookingId, hotelName, city, country, checkIn, checkOut,
                status, mapsUrl, cancelBy,
                cancelBy != null && !now.isBefore(cancelBy.utc()),
                cancelled, cancellationReason);
    }

    /** A copy marked cancelled, carrying the reason the cancellation recorded. */
    BookedHotelView cancelledWith(String reason) {
        return new BookedHotelView(hotelBookingId, hotelName, city, country, checkIn, checkOut,
                status, mapsUrl, cancelBy, cancelDeadlinePassed,
                true, reason);
    }
}
