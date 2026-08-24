package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelPrivateEventCommand;
import dev.ted.jittertravel.domain.CancelPrivateEventContext;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelPrivateEventRequest;

import java.util.UUID;

/**
 * Cancels a planned private social event.
 * <p>
 * Like {@link CancelGroundTransfer}, it folds its one decision fact from the authoritative event
 * stream rather than reading a projector (R1 in {@code EventSourcingRulesHeuristics.md}), and goes
 * through {@link CommandExecutor} — never {@code EventStore} directly.
 * <p>
 * commandId is captured at the boundary and passed in; this service does no clock or UUID I/O of
 * its own, and there is no {@code now} because cancelling a private event is not time-gated.
 */
public class CancelPrivateEvent {
    private final CommandExecutor commandExecutor;

    public CancelPrivateEvent(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelPrivateEvent(UUID commandId, CancelPrivateEventRequest request) {
        PrivateEventId privateEventId = PrivateEventId.of(request.privateEventId());
        commandExecutor.execute(commandId, request, contextFor(privateEventId),
                new CancelPrivateEventCommand(privateEventId, request.reason()));
    }

    /**
     * Folds whether the private event is live from the event stream. A cancellation clears the
     * fact, so a second cancel of the same event is refused as not-found rather than silently
     * emitting a duplicate event.
     */
    private CancelPrivateEventContext contextFor(PrivateEventId privateEventId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, privateEventId, event),
                        (first, second) -> second);
        return new CancelPrivateEventContext(exists);
    }

    private boolean stillPlanned(boolean current, PrivateEventId wanted, Object event) {
        return switch (event) {
            case PrivateEventPlanned e when e.privateEventId().equals(wanted) -> true;
            case PrivateEventCancelled e when e.privateEventId().equals(wanted) -> false;
            default -> current;
        };
    }
}
