package dev.ted.jittertravel.domain;

import java.util.UUID;

public record ConferenceId(UUID id) {
    public ConferenceId {
        if (id == null) {
            throw new IllegalArgumentException("Conference id is required");
        }
    }

    public static ConferenceId random() {
        return new ConferenceId(UUID.randomUUID());
    }

    public static ConferenceId of(UUID id) {
        return new ConferenceId(id);
    }
}
