package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

public record BookedTrainView(
        TrainTripId tripId,
        String serviceId,
        String departureStationName,
        String departureCity,
        String departureMapsUrl,
        ZonedTimestamp departureDateTime,
        String arrivalStationName,
        String arrivalCity,
        String arrivalMapsUrl,
        ZonedTimestamp arrivalDateTime
) implements TemporalView {

    /**
     * A train trip is "upcoming" until it departs. Each {@link ZonedTimestamp} keeps both the UTC
     * instant (used here, so the FUTURE filter is correct no matter which zones the trip spans or
     * what zone the server runs in) and the endpoint zone (used to render the wall-clock the
     * traveler entered).
     */
    @Override
    public Instant relevantUntil() {
        return departureDateTime.utc();
    }
}
