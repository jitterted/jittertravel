package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.List;

/**
 * Decision facts for {@link CancelFlightItineraryCommand}, folded from the authoritative event
 * stream (R1).
 *
 * @param itineraryLive whether this itinerary was booked and has not been cancelled
 * @param liveLegs      the itinerary's legs that are still on the books: a leg already cancelled on
 *                      its own is not here, and is not cancelled twice
 * @param now           captured at the boundary; a leg whose departure is not after it has departed
 */
public record CancelFlightItineraryContext(
        boolean itineraryLive,
        List<ScheduledLeg> liveLegs,
        Instant now
) implements DecisionContext {
    public CancelFlightItineraryContext {
        liveLegs = List.copyOf(liveLegs);
    }
}
