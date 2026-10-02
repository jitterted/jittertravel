package dev.ted.jittertravel.domain;

import java.util.UUID;

/**
 * Identifies a flight itinerary: the legs one confirmation booked together. Minted at the boundary,
 * like every id — deliberately no {@code random()} factory, since the standing exception for the
 * seven older ones does not extend to new ids.
 */
public record FlightItineraryId(UUID id) {
    public FlightItineraryId {
        if (id == null) {
            throw new IllegalArgumentException("Flight itinerary id is required");
        }
    }

    public static FlightItineraryId of(UUID id) {
        return new FlightItineraryId(id);
    }
}
