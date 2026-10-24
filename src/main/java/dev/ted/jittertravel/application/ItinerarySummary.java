package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.Journey;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.util.List;

/**
 * What the flights list says about one itinerary as a whole, on the band above its legs: which it
 * is, where it goes, when, and whether it can still be cancelled whole.
 * <p>
 * OWNER-only, like the list: {@code confirmationCode} is a booking reference.
 * <p>
 * Everything is worked out from the legs <strong>still flying</strong>, so a leg dropped by a
 * schedule change or cancelled on its own leaves the route, the dates and the count. An itinerary
 * with no leg left flying (cancelled whole, or every leg cancelled on its own) is described by all
 * of its legs instead, so the band still says what it was.
 *
 * @param journeys     the legs split into journeys, in departure order; never empty
 * @param legCount     how many legs {@code journeys} holds
 * @param departedLegs how many of the itinerary's flying legs have already left. Cancelling the
 *                     itinerary is refused once this is above zero
 * @param flying       whether any leg is still flying
 * @param cancelled    whether the itinerary itself has been cancelled
 * @param hue          which of the list's two trip tints the itinerary wears. Chosen by order of
 *                     first appearance in the list, so two trips side by side never share one; the
 *                     code remains the identifier and the tint only helps the eye
 */
public record ItinerarySummary(
        FlightItineraryId itineraryId,
        String confirmationCode,
        List<Journey> journeys,
        int legCount,
        int departedLegs,
        boolean flying,
        boolean cancelled,
        int hue
) {
    public static final int HUES = 2;

    public ItinerarySummary {
        // Refused here, as Journey refuses an empty leg list: departs() and arrives() read the
        // first and last journey, and a band with nothing to say is a log naming flights the
        // flights read model never saw, which should be loud rather than skipped.
        if (journeys == null || journeys.isEmpty()) {
            throw new IllegalArgumentException("An itinerary summary has at least one journey");
        }
        journeys = List.copyOf(journeys);
    }

    /** An itinerary's tint is assigned by the list, not by the itinerary. */
    ItinerarySummary withHue(int newHue) {
        return new ItinerarySummary(itineraryId, confirmationCode, journeys, legCount, departedLegs,
                flying, cancelled, newHue);
    }

    public ZonedTimestamp departs() {
        return journeys.getFirst().departs();
    }

    public ZonedTimestamp arrives() {
        return journeys.getLast().arrives();
    }

    public boolean canBeCancelledWhole() {
        return !cancelled && flying && departedLegs == 0;
    }
}
