package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

public record TrainItineraryEntry(
        TrainTripId tripId,
        TrainDayRole role,
        String serviceId,
        String departureStationName,
        String departureCity,
        String departureMapsUrl,
        ZonedTimestamp departureDateTime,
        String arrivalStationName,
        String arrivalCity,
        String arrivalMapsUrl,
        ZonedTimestamp arrivalDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.TRAIN; }
    @Override public LocalDateTime anchorTime() {
        return anchor().localDateTime();
    }

    /** The endpoint this entry is filed under; each end keeps its own station zone. */
    public ZonedTimestamp anchor() {
        return role == TrainDayRole.ARRIVAL ? arrivalDateTime : departureDateTime;
    }
}
