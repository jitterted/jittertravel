package dev.ted.jittertravel.infrastructure;

import java.util.List;

public record TimelineEntry(
        TimelineCommand command,
        List<TimelineEvent> events,
        boolean failed,
        boolean outOfOrder
) {
}
