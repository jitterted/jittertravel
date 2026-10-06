package dev.ted.jittertravel.domain;

/**
 * What family were told, as a decision the notifier made rather than a sentence it wrote. Stored on
 * {@link FamilyNotified} so "has what family believe changed?" is a read of the last one, instead
 * of a recomputation that a later change to the folding rules could silently alter. The words they
 * actually read are built elsewhere and are never stored.
 */
public enum NotifiedFact {
    /** A flight booked on its own: a new trip. */
    FLIGHT_BOOKED,
    /** An itinerary booked as one paste: a new trip with several legs, told once. */
    ITINERARY_BOOKED,
    /**
     * A whole itinerary was cancelled, after family were told it was booked: a trip they believe is
     * on is off. Only ever sent after an {@link #ITINERARY_BOOKED} for the same itinerary, so a trip
     * family never heard about stays silent (Ted, 2026-10-05).
     */
    ITINERARY_CANCELLED,
    /** Ted is going to a conference: committed, whether by a ticket, an invitation or an accepted talk. */
    CONFERENCE_GOING,
    /**
     * Ted is no longer going to a conference family were told he was going to: he declined, the
     * organizers cancelled it, or a rejection dropped it. Only ever sent after a
     * {@link #CONFERENCE_GOING} for the same conference, so a conference family never heard about
     * stays silent (positive-first, the same rule as {@link #ITINERARY_CANCELLED}).
     */
    CONFERENCE_NOT_GOING
}
