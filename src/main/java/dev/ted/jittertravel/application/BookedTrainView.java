package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainTripId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
) implements TemporalView {

    /**
     * A train trip is "upcoming" until it departs. STOPGAP: train events still store
     * bare wall-clock times, so the departure is interpreted in the server zone to
     * preserve pre-migration behavior. Once TrainBooked/TrainChanged carry a
     * {@code ZonedTimestamp}, return its {@code utc()} directly (see
     * {@link TemporalView}).
     */
    @Override
    public Instant relevantUntil() {
        return departureDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
