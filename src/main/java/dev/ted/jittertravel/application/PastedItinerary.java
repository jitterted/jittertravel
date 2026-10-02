package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What {@link UnitedItineraryParser} read out of a pasted confirmation email: the confirmation code
 * and each leg's wall-clock times, before any zone is known. Zones are settled later, at the
 * boundary, because an airport the curated table does not know needs Ted to pick one.
 */
public record PastedItinerary(String confirmationCode, List<Leg> legs) {

    public PastedItinerary {
        legs = List.copyOf(legs);
    }

    /** {@code number} is the email's own "Flight N of M", 1-based. */
    public record Leg(
            int number,
            String airline,
            String flightNumber,
            AirportCode departureAirport,
            LocalDateTime departureLocal,
            AirportCode arrivalAirport,
            LocalDateTime arrivalLocal
    ) {
    }
}
