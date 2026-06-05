package dev.ted.jittertravel.domain;

import java.util.UUID;

public record GatheringId(UUID id) {
    public GatheringId {
        if (id == null) {
            throw new IllegalArgumentException("Gathering id is required");
        }
    }

    public static GatheringId random() {
        return new GatheringId(UUID.randomUUID());
    }

    public static GatheringId of(UUID id) {
        return new GatheringId(id);
    }
}
