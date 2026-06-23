package dev.ted.jittertravel.domain;

public record TrainBooked(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        ZonedTimestamp departureDateTime,
        TrainStationAddress arrivalStation,
        ZonedTimestamp arrivalDateTime,
        String serviceId
) implements Event {
    public TrainBooked {
        serviceId = serviceId != null ? serviceId : "";
    }
}
