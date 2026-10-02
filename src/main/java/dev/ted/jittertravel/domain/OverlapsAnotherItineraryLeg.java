package dev.ted.jittertravel.domain;

/**
 * Two legs of the same itinerary collide in time. {@link ScheduledLegs#overlapping} cannot see this:
 * it knows only what is already booked, and neither leg is yet.
 */
public class OverlapsAnotherItineraryLeg extends RuntimeException {

    private final int otherLegNumber;

    public OverlapsAnotherItineraryLeg(int otherLegNumber) {
        super("Overlaps leg " + otherLegNumber + " of this itinerary");
        this.otherLegNumber = otherLegNumber;
    }

    public int otherLegNumber() {
        return otherLegNumber;
    }
}
