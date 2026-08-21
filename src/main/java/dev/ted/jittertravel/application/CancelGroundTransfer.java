package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelGroundTransferCommand;
import dev.ted.jittertravel.domain.CancelGroundTransferContext;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelGroundTransferRequest;

import java.util.UUID;

/**
 * Cancels a planned ground transfer.
 * <p>
 * Like {@link CancelHotel}, it folds its one decision fact from the authoritative event stream
 * rather than reading a projector (R1 in {@code EventSourcingRulesHeuristics.md}), and goes through
 * {@link CommandExecutor} — never {@code EventStore} directly.
 * <p>
 * commandId is captured at the boundary and passed in; this service does no clock or UUID I/O of
 * its own, and there is no {@code now} because cancelling a transfer is not time-gated.
 */
public class CancelGroundTransfer {
    private final CommandExecutor commandExecutor;

    public CancelGroundTransfer(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelGroundTransfer(UUID commandId, CancelGroundTransferRequest request) {
        GroundTransferId groundTransferId = GroundTransferId.of(request.groundTransferId());
        commandExecutor.execute(commandId, request, contextFor(groundTransferId),
                new CancelGroundTransferCommand(groundTransferId));
    }

    /**
     * Folds whether the transfer is live from the event stream. A cancellation clears the fact, so
     * a second cancel of the same transfer is refused as not-found rather than silently emitting a
     * duplicate event.
     */
    private CancelGroundTransferContext contextFor(GroundTransferId groundTransferId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, groundTransferId, event),
                        (first, second) -> second);
        return new CancelGroundTransferContext(exists);
    }

    private boolean stillPlanned(boolean current, GroundTransferId wanted, Object event) {
        return switch (event) {
            case GroundTransferPlanned e when e.groundTransferId().equals(wanted) -> true;
            case GroundTransferCancelled e when e.groundTransferId().equals(wanted) -> false;
            default -> current;
        };
    }
}
