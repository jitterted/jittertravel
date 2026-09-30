package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Cancels a booked flight. The only refusal is a flight that does not exist (or was already
 * cancelled).
 * <p>
 * No time gate, as with {@link CancelTrainCommand}: a wrong entry is worth removing whenever it is
 * found. The accepted consequence is that a cancelled past flight cannot be booked again.
 * {@code cancelledOn} is recorded, never checked against anything.
 * <p>
 * {@code reason} is carried onto the event and never inspected.
 */
public record CancelFlightCommand(
        FlightId flightId,
        String reason,
        Instant cancelledOn
) implements DomainCommand<CancelFlightContext> {

    @Override
    public Stream<FlightCancelled> execute(CancelFlightContext context) {
        if (!context.flightExists()) {
            throw new FlightNotFound("No flight found to cancel: " + flightId);
        }
        return Stream.of(new FlightCancelled(flightId, reason, cancelledOn));
    }
}
