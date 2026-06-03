package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainTripId;

import java.time.LocalDateTime;

public record BookedTrainView(
        TrainTripId tripId,
        String serviceId,
        String departureStationName,
        String departureCity,
        String departureMapsUrl,
        LocalDateTime departureDateTime,
        String departureDateTimeDisplay,
        String arrivalStationName,
        String arrivalCity,
        String arrivalMapsUrl,
        LocalDateTime arrivalDateTime,
        String arrivalDateTimeDisplay
) {
}
