package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record FlightBooked(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        LocalDateTime departureDateTime,
        AirportCode arrivalAirport,
        LocalDateTime arrivalDateTime
) implements Event {
}
