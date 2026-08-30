package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record BookTrainCommand(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        ZonedTimestamp departureDateTime,
        TrainStationAddress arrivalStation,
        ZonedTimestamp arrivalDateTime,
        String serviceId
) implements DomainCommand<BookTrainContext> {

    @Override
    public Stream<TrainBooked> execute(BookTrainContext context) {
        // Checked before the times: a station pasted into the city field makes the trip match the
        // wrong place on /schedule-problems, and unlike a bad date it looks right on the page.
        EnteredLocation.of(departureStation).check(LocationRole.DEPARTURE);
        EnteredLocation.of(arrivalStation).check(LocationRole.ARRIVAL);
        // Past/future and ordering are instant comparisons (zone-independent), so a Frankfurt→Paris
        // trip is judged by the actual moments, not wall-clock across two zones.
        if (departureDateTime == null || !departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (arrivalDateTime == null || !arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
        return Stream.of(new TrainBooked(tripId, departureStation, departureDateTime,
                arrivalStation, arrivalDateTime, serviceId));
    }
}
