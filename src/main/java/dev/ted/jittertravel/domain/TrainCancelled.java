package dev.ted.jittertravel.domain;

/**
 * A booked train trip was cancelled — refunded or rebooked, or (more often) entered wrongly.
 * <p>
 * A hard removal: every read model drops the trip rather than keeping a "cancelled" row, so this
 * event and the log are the only record it was booked. A hotel keeps a greyed tombstone on
 * {@code /booked-hotels} because that row is where undo will live; a train has no cancellation
 * deadline and no money story here, and the entry most often removed is a <em>duplicate</em>, which
 * a tombstone would preserve on the one screen it is in the way on.
 * <p>
 * Removal matters beyond a tidy list. A wrong leg is a {@code ScheduleTimeline.Movement} asserting
 * Ted travelled between two cities, and left in place its arrival becomes the location walk's last
 * word on where he is — feeding the missing-hotel sweep, the away band and {@code atHomeOn}.
 * <p>
 * {@code reason} is optional free text ({@code ""} when none), for recall only — nothing keys off
 * it. Unlike {@link GroundTransferCancelled} a note is worth having here ("rebooked for the 17th"),
 * and because the removal is hard it survives only in the event log.
 */
public record TrainCancelled(
        TrainTripId tripId,
        String reason
) implements Event {
    public TrainCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
