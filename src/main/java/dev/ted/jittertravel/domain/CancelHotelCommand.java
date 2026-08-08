package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Cancels a booked hotel stay. The one hard gate is check-in: once you have arrived (or the moment
 * has passed) there is nothing left to cancel, so the command is refused. The free-cancellation
 * deadline ({@code cancelBy}) is deliberately not consulted — it is advisory, carries no fee
 * concept, and a stay past its deadline is still cancellable right up to check-in.
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
        // A null check-in means the caller has no gate to apply (import replay); see
        // CancelHotelContext. Otherwise compare instants, so the gate is right regardless of the
        // hotel's zone or the server's.
        if (context.checkIn() != null && !context.now().isBefore(context.checkIn().utc())) {
            throw new CannotCancelAfterCheckIn(
                    "Check-in has passed; this stay can no longer be cancelled");
        }
        return Stream.of(new HotelBookingCancelled(hotelBookingId, reason));
    }
}
