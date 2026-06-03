package dev.ted.jittertravel.domain;

import java.util.UUID;

public record TrainTripId(UUID id) {
    public TrainTripId {
        if (id == null) {
            throw new IllegalArgumentException("Train trip id is required");
        }
    }

    public static TrainTripId random() {
        return new TrainTripId(UUID.randomUUID());
    }

    public static TrainTripId of(UUID id) {
        return new TrainTripId(id);
    }
}
