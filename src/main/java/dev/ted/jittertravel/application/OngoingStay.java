package dev.ted.jittertravel.application;

/**
 * Where the traveler is on an itinerary day that has no entries of its own: the stay that is
 * already under way, checked in before this day and checking out after it.
 * <p>
 * Not an {@link ItineraryEntry}: nothing happens on such a day, and the check-in and check-out
 * days already carry the stay's own entries. This is the answer to "where am I today", which is
 * the one thing a blank column cannot say.
 */
public record OngoingStay(String hotelName, String city, String country) {

    /**
     * The first of the row's two lines, and the one that answers the question: <em>where</em>.
     * The hotel name is the second line rather than a tail on this one — together they wrapped.
     */
    public String locationLabel() {
        return "In " + city + (country.isBlank() ? "" : ", " + country);
    }
}
