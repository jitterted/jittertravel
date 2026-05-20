package dev.ted.jittertravel.domain;

import java.util.UUID;

public record FlightId(UUID id) {
    public FlightId {
        if (id == null) {
            throw new IllegalArgumentException("Flight id is required");
        }
    }

    public static FlightId random() {
        return new FlightId(UUID.randomUUID());
    }

    public static FlightId of(UUID id) {
        return new FlightId(id);
    }
}
