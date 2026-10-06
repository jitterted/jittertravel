package dev.ted.jittertravel.domain;

import java.util.UUID;

/**
 * What a family notification is about: the kind of thing, and its id. Two fields rather than a
 * sealed hierarchy because the value is stored in an event and Jackson would need type annotations
 * to rebuild a sealed one, and {@code domain} may not depend on Jackson ({@code DomainIsPureTest}).
 * The {@link Kind} enum is what a {@code switch} is exhaustive over, so a new kind is still a
 * decision the compiler asks for.
 */
public record NotifiedSubject(Kind kind, UUID id) {

    public enum Kind {
        /** One flight, entered on its own. */
        FLIGHT,
        /** A whole itinerary: the legs one confirmation booked together, told as one trip. */
        FLIGHT_ITINERARY,
        /** One conference, whatever has happened to it: the subject outlives its commitment. */
        CONFERENCE
    }

    public NotifiedSubject {
        if (kind == null) {
            throw new IllegalArgumentException("A notification's subject has a kind");
        }
        if (id == null) {
            throw new IllegalArgumentException("A notification's subject has an id");
        }
    }

    public static NotifiedSubject flight(FlightId flightId) {
        return new NotifiedSubject(Kind.FLIGHT, flightId.id());
    }

    public static NotifiedSubject conference(ConferenceId conferenceId) {
        return new NotifiedSubject(Kind.CONFERENCE, conferenceId.id());
    }

    public static NotifiedSubject itinerary(FlightItineraryId itineraryId) {
        return new NotifiedSubject(Kind.FLIGHT_ITINERARY, itineraryId.id());
    }
}
