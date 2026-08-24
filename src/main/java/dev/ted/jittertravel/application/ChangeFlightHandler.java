package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.ChangeFlightCommand;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.ChangeFlightRequest;

import java.util.UUID;

public class ChangeFlightHandler {

    private final AirportZoneResolver airportZoneResolver;

    public ChangeFlightHandler(AirportZoneResolver airportZoneResolver) {
        this.airportZoneResolver = airportZoneResolver;
    }

    public ChangeFlightCommand handle(ChangeFlightRequest request) {
        AirportCode departureAirport = AirportCode.of(request.getDepartureAirport());
        AirportCode arrivalAirport = AirportCode.of(request.getArrivalAirport());
        FlightEndpointZone endpointZone = new FlightEndpointZone(airportZoneResolver);
        return new ChangeFlightCommand(
                FlightId.of(UUID.fromString(request.getFlightId())),
                request.getAirline(),
                request.getFlightNumber(),
                departureAirport,
                ZonedTimestamp.fromLocal(request.getDepartureDateTime(),
                        endpointZone.resolve(request.getDepartureZone(), departureAirport)),
                arrivalAirport,
                ZonedTimestamp.fromLocal(request.getArrivalDateTime(),
                        endpointZone.resolve(request.getArrivalZone(), arrivalAirport)),
                normalizeReason(request.getReason())
        );
    }

    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }
}
