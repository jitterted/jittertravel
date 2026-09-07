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
 * from the event stream rather than a projector (R1), and writes through {@link CommandExecutor},
 * never {@code EventStore}. Its sibling {@link ChangeTrain} reads existence from
 * {@link TrainDetailsViewProjector} instead: a change corrects something the user is looking at,
 * while a cancellation is the removal, and only the stream cannot be one batch stale.
 * <p>
 * commandId is captured at the boundary and passed in. There is no {@code now}: cancelling is not
 * time-gated.
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
     * Folds whether the trip is live. A cancellation clears the fact, so a second cancel of the
     * same trip is refused as not-found rather than emitting a duplicate event.
     * <p>
     * {@code TrainChanged} is deliberately not consulted: a change cannot resurrect a cancelled
     * trip, and cannot be the first thing said about one either.
     * <p>
     * An explicit loop rather than {@code reduce}, because this fold is order-dependent: the
     * combiner {@code reduce} needs for a parallel stream cannot be written correctly here, and
     * supplying a plausible-looking one would hide that.
     */
    private CancelTrainContext contextFor(TrainTripId tripId) {
        boolean exists = false;
        for (StoredEvent stored : commandExecutor.eventsForDecision().toList()) {
            exists = stillBooked(exists, tripId, stored.payload());
        }
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
