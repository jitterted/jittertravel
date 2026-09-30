package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelFlightCommand;
import dev.ted.jittertravel.domain.CancelFlightContext;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelFlightRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Cancels a booked flight. Mirrors {@link CancelTrain}: the one decision fact is folded from the
 * event stream rather than a projector (R1), and the write goes through {@link CommandExecutor}.
 * <p>
 * commandId and {@code now} are captured at the boundary and passed in. {@code now} is only
 * recorded on the event, never checked: cancelling is not time-gated.
 */
public class CancelFlight {
    private final CommandExecutor commandExecutor;

    public CancelFlight(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelFlight(UUID commandId, CancelFlightRequest request, Instant now) {
        FlightId flightId = FlightId.of(request.flightId());
        commandExecutor.execute(commandId, request, contextFor(flightId),
                new CancelFlightCommand(flightId, request.reason(), now));
    }

    /**
     * Folds whether the flight is live. {@code FlightChanged} is deliberately not consulted: a change
     * cannot resurrect a cancelled flight. An explicit loop rather than {@code reduce}, because the
     * fold is order-dependent (see {@link CancelTrain}).
     */
    private CancelFlightContext contextFor(FlightId flightId) {
        boolean exists = false;
        for (StoredEvent stored : commandExecutor.eventsForDecision().toList()) {
            exists = stillBooked(exists, flightId, stored.payload());
        }
        return new CancelFlightContext(exists);
    }

    private boolean stillBooked(boolean current, FlightId wanted, Object event) {
        return switch (event) {
            case FlightBooked e when e.flightId().equals(wanted) -> true;
            case FlightCancelled e when e.flightId().equals(wanted) -> false;
            default -> current;
        };
    }
}
