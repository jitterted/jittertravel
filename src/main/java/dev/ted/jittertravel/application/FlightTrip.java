package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightItineraryId;

/**
 * What the flights list says about the itinerary one flight belongs to: which it is, where this leg
 * sits in it, and whether the itinerary can still be cancelled as a whole.
 * <p>
 * OWNER-only, like the list: {@code confirmationCode} is a booking reference.
 *
 * @param legNumber    this flight's place among the legs the confirmation booked, from 1
 * @param legCount     how many legs the confirmation booked. It counts the whole booking, so a leg
 *                     later cancelled on its own or hidden by the date filter still counts, and
 *                     "leg 3 of 4" tells the reader there are others they are not looking at
 * @param departedLegs how many of the itinerary's live legs have already left. Cancelling the
 *                     itinerary is refused once this is above zero
 * @param hue          which of the list's two trip tints the row wears. Chosen by order of first
 *                     appearance in the list, so two trips side by side never share one; the code
 *                     remains the identifier and the tint only helps the eye
 */
public record FlightTrip(
        FlightItineraryId itineraryId,
        String confirmationCode,
        int legNumber,
        int legCount,
        int departedLegs,
        int hue
) {
    public static final int HUES = 2;

    /** A trip's tint is assigned by the list, not by the trip. */
    FlightTrip withHue(int newHue) {
        return new FlightTrip(itineraryId, confirmationCode, legNumber, legCount, departedLegs, newHue);
    }

    public boolean canBeCancelledWhole() {
        return departedLegs == 0;
    }
}
