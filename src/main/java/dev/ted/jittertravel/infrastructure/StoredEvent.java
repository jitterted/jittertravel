package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import java.time.Instant;
import java.util.UUID;

public record StoredEvent(
        long sequence,
        Class<? extends Event> type,
        UUID eventId,
        Instant timestamp,
        Event payload
) {
}
