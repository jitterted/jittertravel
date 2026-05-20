package dev.ted.jittertravel.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TimelineCommand(
        UUID commandId,
        OffsetDateTime timestamp,
        String type,
        String payloadJson
) {
    public String simpleType() {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }
}
