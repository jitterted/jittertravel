package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record BookFlightCommand(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime
) implements DomainCommand<BookFlightContext> {

    @Override
    public Stream<FlightBooked> execute(BookFlightContext context) {
        if (!departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (!arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
        return Stream.of(new FlightBooked(flightId, airline, flightNumber,
                departureAirport, departureDateTime, arrivalAirport, arrivalDateTime));
    }
}
