package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.util.List;

/**
 * Decision facts for {@link ChangeFlightItineraryCommand}, folded from the authoritative event
 * stream (R1).
 *
 * @param itineraryLive    whether this itinerary was booked and has not been cancelled
 * @param liveMembers      the itinerary's flights still on the books, as they currently stand
 * @param cancelledMembers the itinerary's flights Ted cancelled, as they stood when they were; a
 *                         pasted leg matching one is refused
 * @param droppedMembers   the itinerary's flights an earlier schedule change dropped; a pasted leg
 *                         matching one is the airline putting it back, and is simply added
 * @param scheduledLegs   everything live on the schedule, the itinerary's own legs included
 * @param now              captured at the boundary; a leg whose departure is not after it has left
 */
public record ChangeFlightItineraryContext(
        boolean itineraryLive,
        List<ItineraryLeg> liveMembers,
        List<ItineraryLeg> cancelledMembers,
        List<ItineraryLeg> droppedMembers,
        ScheduledLegs scheduledLegs,
        Instant now
) implements DecisionContext {
    public ChangeFlightItineraryContext {
        liveMembers = List.copyOf(liveMembers);
        cancelledMembers = List.copyOf(cancelledMembers);
        droppedMembers = List.copyOf(droppedMembers);
    }

    public ChangeFlightItineraryContext(boolean itineraryLive, List<ItineraryLeg> liveMembers,
                                        List<ItineraryLeg> cancelledMembers,
                                        ScheduledLegs scheduledLegs, Instant now) {
        this(itineraryLive, liveMembers, cancelledMembers, List.of(), scheduledLegs, now);
    }
}
