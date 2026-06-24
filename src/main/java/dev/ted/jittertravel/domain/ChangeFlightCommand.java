package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record ChangeFlightCommand(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime,
        String reason
) implements DomainCommand<ChangeFlightContext> {

    public ChangeFlightCommand {
        if (reason == null) reason = "";
    }

    @Override
    public Stream<FlightChanged> execute(ChangeFlightContext context) {
        if (!context.flightExists()) {
            throw new FlightNotFound("No flight exists with id " + flightId.id());
        }
        if (!departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (!arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
        return Stream.of(new FlightChanged(flightId, airline, flightNumber,
                departureAirport, departureDateTime, arrivalAirport, arrivalDateTime, reason));
    }
}
