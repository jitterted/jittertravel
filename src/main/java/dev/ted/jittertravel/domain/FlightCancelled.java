package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * A booked flight was cancelled — refunded, rebooked, or entered wrongly.
 * <p>
 * Removed from every read model that plans or places Ted — the calendars, the itinerary, schedule
 * problems, the overlap check — because a leftover leg would go on asserting he flew between two
 * cities, feeding the location walk, the away band and {@code atHomeOn}. The one exception is
 * {@code /booked-flights}, which keeps it as a record behind {@code ?cancelled=show}: a cancelled
 * flight often leaves a travel credit or a refund behind, and that is where Ted looks it up.
 * <p>
 * {@code reason} is optional free text ({@code ""} when none), for recall only — nothing keys off
 * it. {@code cancelledOn} is when Ted recorded the cancellation, captured at the boundary; it is a
 * payload field because a displayed time never comes from the store's envelope (R11).
 * <p>
 * {@code cause} says whether Ted cancelled it or an airline schedule change dropped it. Events
 * stored before it existed carry none and read as {@link FlightCancellationCause#MANUAL}, so no
 * leg already cancelled becomes reinstatable.
 */
public record FlightCancelled(
        FlightId flightId,
        String reason,
        Instant cancelledOn,
        FlightCancellationCause cause
) implements Event {
    public FlightCancelled {
        if (reason == null) {
            reason = "";
        }
        if (cause == null) {
            cause = FlightCancellationCause.MANUAL;
        }
    }

    public FlightCancelled(FlightId flightId, String reason, Instant cancelledOn) {
        this(flightId, reason, cancelledOn, FlightCancellationCause.MANUAL);
    }
}
