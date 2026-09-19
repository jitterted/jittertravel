package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangePrivateEventMatchingLocationCommand;
import dev.ted.jittertravel.domain.ChangePrivateEventMatchingLocationContext;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ChangePrivateEventMatchingLocationRequest;

import java.util.UUID;

/**
 * Corrects the place the schedule reasons about a private event in.
 * <p>
 * Like {@link CancelPrivateEvent}, it folds its one decision fact from the authoritative event
 * stream rather than reading a projector (R1 in {@code EventSourcingRulesHeuristics.md}), and goes
 * through {@link CommandExecutor} — never {@code EventStore} directly.
 * <p>
 * commandId is captured at the boundary and passed in; this service does no clock or UUID I/O of
 * its own, and there is no {@code now} because re-matching a private event is not time-gated.
 */
public class ChangePrivateEventMatchingLocation {
    private final CommandExecutor commandExecutor;

    public ChangePrivateEventMatchingLocation(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void changeMatchingLocation(UUID commandId,
                                       ChangePrivateEventMatchingLocationRequest request) {
        PrivateEventId privateEventId = PrivateEventId.of(request.privateEventId());
        commandExecutor.execute(commandId, request, contextFor(privateEventId),
                new ChangePrivateEventMatchingLocationCommand(
                        privateEventId, request.locationForMatching()));
    }

    /**
     * Folds whether the private event is live from the event stream. A cancellation clears the
     * fact, so re-matching an evening that has since been cancelled is refused as not-found rather
     * than writing an override no read model would ever apply.
     * <p>
     * A previous {@code PrivateEventMatchingLocationChanged} is deliberately not folded: it changes
     * where the evening is matched, never whether it exists, so it cannot move this answer.
     */
    private ChangePrivateEventMatchingLocationContext contextFor(PrivateEventId privateEventId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillPlanned(current, privateEventId, event),
                        (first, second) -> second);
        return new ChangePrivateEventMatchingLocationContext(exists);
    }

    private boolean stillPlanned(boolean current, PrivateEventId wanted, Object event) {
        return switch (event) {
            case PrivateEventPlanned e when e.privateEventId().equals(wanted) -> true;
            case PrivateEventCancelled e when e.privateEventId().equals(wanted) -> false;
            default -> current;
        };
    }
}
