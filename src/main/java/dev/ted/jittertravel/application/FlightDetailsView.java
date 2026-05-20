package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.FlightId;

import java.time.LocalDateTime;

/**
 * Full details for a single flight, used to hydrate the edit form.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input
 * controls directly. The list view ({@link BookedFlightView}) is what does
 * the pre-formatting; this view is for editing.
 */
public record FlightDetailsView(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        LocalDateTime departureDateTime,
        AirportCode arrivalAirport,
        LocalDateTime arrivalDateTime
) {
}
