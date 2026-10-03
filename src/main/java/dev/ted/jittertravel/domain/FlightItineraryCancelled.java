package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * A whole itinerary was cancelled, before any of its legs had departed.
 * <p>
 * Written in the same append as one {@link FlightCancelled} per leg that was still live, so every
 * read model that plans or places a flight already lets go of each leg without knowing this event
 * exists. It exists to say <em>why those legs went together</em>, and to mark the itinerary itself
 * cancelled so a second cancel is refused rather than emitting an empty one.
 * <p>
 * {@code reason} is optional free text ({@code ""} when none), for recall only. {@code cancelledOn}
 * is when Ted recorded it, captured at the boundary: a displayed time is a payload field (R11).
 * Like {@link FlightItineraryBooked} it is private — {@code PublicCalendarProjector} never reads it.
 */
public record FlightItineraryCancelled(
        FlightItineraryId itineraryId,
        String reason,
        Instant cancelledOn
) implements Event {
    public FlightItineraryCancelled {
        if (reason == null) {
            reason = "";
        }
    }
}
