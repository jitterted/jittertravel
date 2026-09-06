package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Cancels a booked train trip. The only refusal is a trip that does not exist (or was already
 * cancelled).
 * <p>
 * There deliberately is no time gate, even though {@link ChangeTrainCommand} refuses dates that are
 * not in the future: a wrong entry is worth removing whenever it is found, and a past one is the
 * leg still telling {@code ScheduleGapProjector} that Ted travelled between two cities he did not.
 * The consequence is accepted rather than worked around — a cancelled past trip cannot be booked
 * again with the same dates, so that one cancellation is not reversible from inside the app.
 * <p>
 * {@code reason} is carried onto the event and never inspected: no rule reads it, and none should
 * start.
 */
public record CancelTrainCommand(
        TrainTripId tripId,
        String reason
) implements DomainCommand<CancelTrainContext> {

    @Override
    public Stream<TrainCancelled> execute(CancelTrainContext context) {
        if (!context.trainExists()) {
            throw new TrainNotFound("No train trip found to cancel: " + tripId);
        }
        return Stream.of(new TrainCancelled(tripId, reason));
    }
}
