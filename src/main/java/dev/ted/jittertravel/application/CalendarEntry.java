package dev.ted.jittertravel.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A calendar entry view, pre-formatted by a projector for rendering by
 * {@code CalendarViewBuilder}. Titles are display-ready strings; subtitles are
 * {@link SubtitleLine}s, so a line that names a moment can keep its
 * {@link dev.ted.jittertravel.domain.ZonedTimestamp} through to the {@code <time>} element.
 * <p>
 * {@code start}/{@code end} stay entry-zone wall-clock: they place the segment in a day
 * column, and that column is always the local day at the location (decision 7).
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
        List<SubtitleLine> subTitle,
        String continuationTitle,
        List<SubtitleLine> continuationSubTitle,
        String mapsUrl,
        String editPath
) {
    /**
     * Convenience constructor for entries with no owner edit link (everything except booked
     * flights and trains). Keeps the many existing call sites that predate {@code editPath}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle, String mapsUrl) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, null);
    }
}
