package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.BookFlightRequest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class BookFlightCommand {

    public Stream<FlightBooked> execute(BookFlightRequest dto, LocalDateTime now) {
        if (dto.getDepartureDateTime() == null || !dto.getDepartureDateTime().isAfter(now)) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (dto.getArrivalDateTime() == null || !dto.getArrivalDateTime().isAfter(dto.getDepartureDateTime())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }

        FlightId flightId = FlightId.of(UUID.fromString(dto.getFlightId()));
        return Stream.of(new FlightBooked(
                flightId,
                dto.getAirline(),
                dto.getFlightNumber(),
                AirportCode.of(dto.getDepartureAirport()),
                dto.getDepartureDateTime(),
                AirportCode.of(dto.getArrivalAirport()),
                dto.getArrivalDateTime()
        ));
    }
}
