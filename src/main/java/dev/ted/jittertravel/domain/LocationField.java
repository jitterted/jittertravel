package dev.ted.jittertravel.domain;

/**
 * Which part of an entered location a rejection is about: the building's own name (a station,
 * a hotel), the city it stands in ({@link EnteredLocation}), or its country and state
 * ({@link EnteredCountry}). Paired with a {@link LocationRole} it identifies exactly one input on
 * the form that submitted the booking.
 */
public enum LocationField {
    VENUE_NAME,
    CITY,
    COUNTRY,
    REGION
}
