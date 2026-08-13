package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Cancels a booked hotel stay. The only refusal is a booking that does not exist (or was already
 * cancelled) — there is no time gate.
 * <p>
 * There deliberately is no "cannot cancel after check-in" rule: the real-world cancellation happens
 * with the hotel, and telling JitterTravel about it is a separate manual step that routinely lags
 * behind. Refusing a late entry would block recording something that already happened. The
 * free-cancellation deadline ({@code cancelBy}) is likewise not consulted — it is advisory and
 * carries no fee concept.
 */
public record CancelHotelCommand(
        HotelBookingId hotelBookingId,
        String reason
) implements DomainCommand<CancelHotelContext> {

    public CancelHotelCommand {
        if (reason == null) {
            reason = "";
        }
    }

    @Override
    public Stream<HotelBookingCancelled> execute(CancelHotelContext context) {
        if (!context.bookingExists()) {
            throw new HotelBookingNotFound(
                    "No hotel booking found to cancel: " + hotelBookingId);
        }
        return Stream.of(new HotelBookingCancelled(hotelBookingId, reason));
    }
}
