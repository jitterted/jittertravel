package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record TrainBooked(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        LocalDateTime departureDateTime,
        TrainStationAddress arrivalStation,
        LocalDateTime arrivalDateTime,
        String serviceId
) implements Event {
    public TrainBooked {
        serviceId = serviceId != null ? serviceId : "";
    }
}
