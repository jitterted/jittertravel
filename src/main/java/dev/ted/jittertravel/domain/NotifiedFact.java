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
    ITINERARY_BOOKED
}
