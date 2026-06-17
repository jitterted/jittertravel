package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

/**
 * Full snapshot of a train trip after an in-place edit. Identical shape to {@link TrainBooked};
 * every train projection overwrites its entry keyed by {@link TrainTripId} when it sees this.
 */
public record TrainChanged(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        LocalDateTime departureDateTime,
        TrainStationAddress arrivalStation,
        LocalDateTime arrivalDateTime,
        String serviceId
) implements Event {
    public TrainChanged {
        serviceId = serviceId != null ? serviceId : "";
    }
}