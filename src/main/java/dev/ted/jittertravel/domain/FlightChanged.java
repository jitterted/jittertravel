package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

/**
 * Recorded when an existing flight's details are changed. Full snapshot:
 * carries the complete new set of field values for every field except
 * {@link FlightId}, which is immutable.
 * <p>
 * Because this is a full snapshot, projections may simply overwrite the row
 * keyed by {@code flightId}.
 */
public record FlightChanged(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        LocalDateTime departureDateTime,
        AirportCode arrivalAirport,
        LocalDateTime arrivalDateTime,
        String reason
) implements Event {
}
