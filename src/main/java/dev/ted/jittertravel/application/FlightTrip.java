package dev.ted.jittertravel.application;

/**
 * What the flights list says about the itinerary one flight belongs to: the itinerary as a whole,
 * and where this leg sits in it.
 * <p>
 * OWNER-only, like the list.
 *
 * @param legNumber this flight's place among the itinerary's legs still flying, from 1, so after a
 *                  rebooking the legs read 1, 2, 3 of 3 rather than 1, 2, 5 of 5 (Ted, 2026-10-13).
 *                  0 for a leg that is not flying: it has no place among them
 */
public record FlightTrip(
        ItinerarySummary itinerary,
        int legNumber
) {
    public boolean flying() {
        return legNumber > 0;
    }
}
