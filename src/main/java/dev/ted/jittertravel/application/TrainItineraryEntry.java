package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

public record TrainItineraryEntry(
        TrainDayRole role,
        String serviceId,
        String departureStationName,
        String departureCity,
        String departureMapsUrl,
        LocalDateTime departureDateTime,
        String arrivalStationName,
        String arrivalCity,
        String arrivalMapsUrl,
        LocalDateTime arrivalDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.TRAIN; }
    @Override public LocalDateTime anchorTime() {
        return role == TrainDayRole.ARRIVAL ? arrivalDateTime : departureDateTime;
    }
}
