package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Cancels a planned ground transfer. The only refusal is a transfer that does not exist (or was
 * already cancelled).
 * <p>
 * There deliberately is no time gate, for the same reason {@link PlanGroundTransferCommand} has no
 * future-date rule: a transfer is entered — and corrected — on a trip already under way, and a past
 * transfer that never happened is exactly the entry most worth removing.
 */
public record CancelGroundTransferCommand(
        GroundTransferId groundTransferId
) implements DomainCommand<CancelGroundTransferContext> {

    @Override
    public Stream<GroundTransferCancelled> execute(CancelGroundTransferContext context) {
        if (!context.transferExists()) {
            throw new GroundTransferNotFound(
                    "No ground transfer found to cancel: " + groundTransferId);
        }
        return Stream.of(new GroundTransferCancelled(groundTransferId));
    }
}
