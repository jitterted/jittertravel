package dev.ted.jittertravel.infrastructure;

import java.time.LocalDateTime;

public record FlightLookupResult(
        String airline,
        String flightNumber,
        String departureAirport,
        LocalDateTime departureDateTime,
        String departureZoneId,
        String arrivalAirport,
        LocalDateTime arrivalDateTime,
        String arrivalZoneId
) {
}
