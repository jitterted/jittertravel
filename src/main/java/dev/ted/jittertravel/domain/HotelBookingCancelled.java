package dev.ted.jittertravel.domain;

/**
 * A booked hotel stay was cancelled. {@code reason} is optional free text ({@code ""} when none was
 * given), recorded for the traveler's own recall — nothing keys off it.
 * <p>
 * Cancellation is a hard removal: every read model drops the booking entirely rather than keeping a
 * "cancelled" row, so this event and the log are the only record that the stay ever existed. The
 * cancellation deadline is deliberately <em>not</em> copied here — it is advisory, and the gate that
 * actually governs cancelling is check-in (see {@link CancelHotelCommand}).
 */
public record HotelBookingCancelled(
        HotelBookingId hotelBookingId,
        String reason
) implements Event {
    public HotelBookingCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
