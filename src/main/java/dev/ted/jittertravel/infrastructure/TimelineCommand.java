package dev.ted.jittertravel.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TimelineCommand(
        UUID commandId,
        OffsetDateTime timestamp,
        String type,
        String payloadJson,
        String status
) {
    public String simpleType() {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }

    public boolean pending() {
        return "PENDING".equals(status);
    }

    public boolean failed() {
        return status != null && status.startsWith("FAILED");
    }

    public boolean abandoned() {
        return "ABANDONED".equals(status);
    }

    public String statusLabel() {
        return switch (status) {
            case "SUCCEEDED" -> "Succeeded";
            case "PENDING" -> "Pending";
            case "FAILED_DOMAIN" -> "Failed: domain";
            case "FAILED_PERSIST" -> "Failed: persist";
            case "ABANDONED" -> "Abandoned";
            case null -> "Unknown";
            default -> status;
        };
    }
}
