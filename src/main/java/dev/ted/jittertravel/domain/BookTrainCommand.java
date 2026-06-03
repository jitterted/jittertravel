package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;
import java.util.stream.Stream;

public record BookTrainCommand(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        LocalDateTime departureDateTime,
        TrainStationAddress arrivalStation,
        LocalDateTime arrivalDateTime,
        String serviceId
) implements DomainCommand<BookTrainContext> {

    @Override
    public Stream<TrainBooked> execute(BookTrainContext context) {
        if (departureDateTime == null || !departureDateTime.isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (arrivalDateTime == null || !arrivalDateTime.isAfter(departureDateTime)) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
        return Stream.of(new TrainBooked(tripId, departureStation, departureDateTime,
                arrivalStation, arrivalDateTime, serviceId));
    }
}
