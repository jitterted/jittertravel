package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.BookFlightRequest;

import java.util.UUID;

public class BookFlightHandler {

    private final AirportZoneResolver airportZoneResolver;

    public BookFlightHandler(AirportZoneResolver airportZoneResolver) {
        this.airportZoneResolver = airportZoneResolver;
    }

    public BookFlightCommand handle(BookFlightRequest request) {
        AirportCode departureAirport = AirportCode.of(request.getDepartureAirport());
        AirportCode arrivalAirport = AirportCode.of(request.getArrivalAirport());
        FlightEndpointZone endpointZone = new FlightEndpointZone(airportZoneResolver);
        return new BookFlightCommand(
                FlightId.of(UUID.fromString(request.getFlightId())),
                request.getAirline(),
                request.getFlightNumber(),
                departureAirport,
                ZonedTimestamp.fromLocal(request.getDepartureDateTime(),
                        endpointZone.resolve(request.getDepartureZone(), departureAirport)),
                arrivalAirport,
                ZonedTimestamp.fromLocal(request.getArrivalDateTime(),
                        endpointZone.resolve(request.getArrivalZone(), arrivalAirport))
        );
    }
}
