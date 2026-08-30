package dev.ted.jittertravel.domain;

/**
 * Which half of an {@link EnteredLocation} a rejection is about: the building's own name (a station,
 * a hotel) or the city it stands in. Paired with a {@link LocationRole} it identifies exactly one
 * input on the form that submitted the booking.
 */
public enum LocationField {
    VENUE_NAME,
    CITY
}
