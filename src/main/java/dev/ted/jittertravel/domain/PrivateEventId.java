package dev.ted.jittertravel.domain;

import java.util.UUID;

public record PrivateEventId(UUID id) {
    public PrivateEventId {
        if (id == null) {
            throw new IllegalArgumentException("Private event id is required");
        }
    }

    public static PrivateEventId random() {
        return new PrivateEventId(UUID.randomUUID());
    }

    public static PrivateEventId of(UUID id) {
        return new PrivateEventId(id);
    }
}
