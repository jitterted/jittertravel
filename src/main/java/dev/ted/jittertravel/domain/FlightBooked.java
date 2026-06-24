package dev.ted.jittertravel.domain;

public record FlightBooked(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime
) implements Event {
}
