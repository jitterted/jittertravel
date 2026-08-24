package dev.ted.jittertravel.domain;

/**
 * A planned private social event was cancelled — the dinner is off, or (more often) the entry was
 * wrong and has to go.
 * <p>
 * Cancellation is a hard removal: every read model drops the event entirely, so this event and the
 * log are the only record that it was ever planned. That is the point rather than a shortcoming —
 * a private event is an occupancy in {@code ScheduleGapProjector} exactly as a gathering is,
 * asserting that Ted is in a city between two instants, and a wrong one left in place goes on
 * feeding that false presence fact into away days and the missing-hotel check.
 * <p>
 * {@code reason} is optional free text ({@code ""} when none was given), recorded for the
 * traveler's own recall — nothing keys off it. Unlike {@link GroundTransferCancelled}, which has
 * none, a note here is worth having even though a private event has no booking to explain away:
 * "rescheduled to Friday" is a fact about the evening. Because the removal is hard, the note
 * survives only in the event log — which is what its wording on the page promises, and no less than
 * a cancelled hotel's reason gets (Ted, 2026-08-24).
 */
public record PrivateEventCancelled(
        PrivateEventId privateEventId,
        String reason
) implements Event {
    public PrivateEventCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
