package dev.ted.jittertravel.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A calendar entry view, pre-formatted by a projector for rendering by
 * {@code CalendarViewBuilder}. All strings are display-ready.
 * <p>
 * Multi-week / multi-day entries render as one segment per week. The segment that
 * contains the entry's {@code start} day uses {@code mainTitle} / {@code subTitle};
 * subsequent (continuation) segments use {@code continuationTitle} /
 * {@code continuationSubTitle}, skipping any field that is {@code null}.
 */
public record CalendarEntry(
        EntryKind kind,
        LocalDateTime start,
        LocalDateTime end,
        String mainTitle,
        List<String> subTitle,
        String continuationTitle,
        List<String> continuationSubTitle,
        String mapsUrl,
        String editPath
) {
    /**
     * Convenience constructor for entries with no owner edit link (everything except booked
     * flights and trains). Keeps the many existing call sites that predate {@code editPath}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<String> subTitle,
                         String continuationTitle, List<String> continuationSubTitle, String mapsUrl) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, null);
    }
}
