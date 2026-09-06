package dev.ted.jittertravel.domain;

/**
 * A booked train trip was cancelled — the ticket is refunded or rebooked, or (more often) the trip
 * was entered wrongly and has to go.
 * <p>
 * Cancellation is a hard removal: every read model drops the trip entirely rather than keeping a
 * "cancelled" row, so this event and the log are the only record that it was ever booked. A hotel
 * keeps a greyed tombstone on {@code /booked-hotels} because a mistaken cancel there has no undo
 * and the row is where undo will live; a train has no cancellation deadline and no money story in
 * this model, and the entry most often removed is a <em>duplicate</em> — which a tombstone would
 * preserve on the one screen it is in the way on.
 * <p>
 * Removing it matters beyond a tidy list. A wrong leg is a {@code ScheduleTimeline.Movement}
 * asserting that Ted travelled between two cities at two instants, and left in place it silently
 * moves the location walk: a stray arrival becomes the last word on where he is, and the city it
 * invents then feeds the missing-hotel sweep, the away band and {@code atHomeOn}.
 * <p>
 * {@code reason} is optional free text ({@code ""} when none was given), recorded for the
 * traveler's own recall — nothing keys off it. Unlike {@link GroundTransferCancelled}, which has
 * none, a note here is worth having: "rebooked for the 17th" is a fact about the journey. Because
 * the removal is hard, the note survives only in the event log, which is what its wording on the
 * page promises and no less than a cancelled hotel's reason gets.
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
