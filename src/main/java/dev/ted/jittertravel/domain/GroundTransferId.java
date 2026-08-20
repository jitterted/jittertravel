package dev.ted.jittertravel.domain;

import java.util.UUID;

public record GroundTransferId(UUID id) {
    public GroundTransferId {
        if (id == null) {
            throw new IllegalArgumentException("Ground transfer id is required");
        }
    }

    public static GroundTransferId random() {
        return new GroundTransferId(UUID.randomUUID());
    }

    public static GroundTransferId of(UUID id) {
        return new GroundTransferId(id);
    }
}
