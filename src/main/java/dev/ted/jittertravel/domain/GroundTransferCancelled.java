package dev.ted.jittertravel.domain;

/**
 * A planned ground transfer was cancelled — the taxi is not being taken, or (far more often) the
 * entry was wrong and has to go.
 * <p>
 * Cancellation is a hard removal: every read model drops the transfer entirely, so this event and
 * the log are the only record that it was ever planned. That is the whole point — a wrong transfer
 * left in place keeps feeding a false presence fact into {@code ScheduleGapProjector}, where it can
 * mask a real missing-travel gap.
 * <p>
 * No reason field, unlike {@link HotelBookingCancelled}: a hotel's reason records something that
 * happened in the real world with a booking, and a transfer has no booking to explain away
 * (Ted, 2026-08-20).
 */
public record GroundTransferCancelled(
        GroundTransferId groundTransferId
) implements Event {
}
