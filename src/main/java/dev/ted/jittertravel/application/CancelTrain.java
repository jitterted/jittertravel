package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelTrainCommand;
import dev.ted.jittertravel.domain.CancelTrainContext;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelTrainRequest;

import java.util.UUID;

/**
 * Cancels a booked train trip.
 * <p>
 * Like {@link CancelPrivateEvent} and {@link CancelGroundTransfer}, it folds its one decision fact
 * from the authoritative event stream rather than reading a projector (R1 in
 * {@code EventSourcingRulesHeuristics.md}), and goes through {@link CommandExecutor} — never
 * {@code EventStore} directly. Note the contrast with its sibling {@link ChangeTrain}, which reads
 * existence from {@link TrainDetailsViewProjector}: a change is a correction to something the user
 * is looking at, while a cancellation is the removal itself, and the stream is the only source that
 * cannot be one batch stale.
 * <p>
 * commandId is captured at the boundary and passed in; this service does no clock or UUID I/O of
 * its own, and there is no {@code now} because cancelling a train is not time-gated.
 */
public class CancelTrain {
    private final CommandExecutor commandExecutor;

    public CancelTrain(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelTrain(UUID commandId, CancelTrainRequest request) {
        TrainTripId tripId = TrainTripId.of(request.tripId());
        commandExecutor.execute(commandId, request, contextFor(tripId),
                new CancelTrainCommand(tripId, request.reason()));
    }

    /**
     * Folds whether the trip is live from the event stream. A cancellation clears the fact, so a
     * second cancel of the same trip is refused as not-found rather than silently emitting a
     * duplicate event.
     * <p>
     * {@code TrainChanged} is deliberately not consulted: a change cannot resurrect a cancelled
     * trip, and it cannot be the first thing said about one either.
     */
    private CancelTrainContext contextFor(TrainTripId tripId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillBooked(current, tripId, event),
                        (first, second) -> second);
        return new CancelTrainContext(exists);
    }

    private boolean stillBooked(boolean current, TrainTripId wanted, Object event) {
        return switch (event) {
            case TrainBooked e when e.tripId().equals(wanted) -> true;
            case TrainCancelled e when e.tripId().equals(wanted) -> false;
            default -> current;
        };
    }
}
