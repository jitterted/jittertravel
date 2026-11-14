package dev.ted.jittertravel.domain;

/**
 * Who changed an itinerary, which the confirmation email cannot say: United's looks the same
 * either way. Ted picks it on the change page, and it decides which fact is recorded
 * ({@code docs/FlightItineraryRebookingPlan.md} D1, D2).
 */
public enum ItineraryChangeKind {
    /** The airline changed the schedule: recorded as {@link FlightItineraryChanged}. */
    SCHEDULE_CHANGE,
    /** Ted rebooked it himself, under the same confirmation code: {@link FlightItineraryRebooked}. */
    REBOOKING
}
