package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Cancels a planned private social event. The only refusal is an event that does not exist (or was
 * already cancelled).
 * <p>
 * There deliberately is no time gate, even though {@link PlanPrivateEventCommand} refuses a date
 * that is not in the future: a wrong entry is worth removing whenever it is found, and a past one
 * is the entry still telling {@code ScheduleGapProjector} that Ted was in a city he was not.
 * The consequence is accepted rather than worked around — a cancelled past event cannot be planned
 * again, so that one cancellation is not reversible from inside the app (Ted, 2026-08-24).
 * <p>
 * {@code reason} is carried onto the event and never inspected: no rule reads it, and none should
 * start.
 */
public record CancelPrivateEventCommand(
        PrivateEventId privateEventId,
        String reason
) implements DomainCommand<CancelPrivateEventContext> {

    @Override
    public Stream<PrivateEventCancelled> execute(CancelPrivateEventContext context) {
        if (!context.privateEventExists()) {
            throw new PrivateEventNotFound(
                    "No private event found to cancel: " + privateEventId);
        }
        return Stream.of(new PrivateEventCancelled(privateEventId, reason));
    }
}
