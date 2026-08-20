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
 * <p>
 * {@code commitment} applies to {@link EntryKind#CONFERENCE} alone and is {@code null} —
 * "not applicable" — on every other kind. It is one of several fields here that apply to some kinds
 * only; the agreed fix is the sealed {@code EntryDetails} of
 * {@code docs/RendererVsProjectorResponsibilities.md} (decision S2 + E2, 2026-08-19), which this
 * field is expected to move into.
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
        boolean speaking,
        String editPath,
        AttendanceCommitment commitment
) {
    /**
     * Convenience constructor for entries with no owner edit link and no public "speaking"
     * marker (hotels, flights sharing a day, private events, and non-speaking gatherings).
     * Keeps the many call sites that predate {@code editPath} and {@code speaking}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle, String mapsUrl) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, false, null, null);
    }

    /**
     * Convenience constructor for entries that carry an owner edit link (booked flights and
     * trains) but are not speaking events. {@code speaking} defaults to {@code false}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle,
                         String mapsUrl, String editPath) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, false, editPath, null);
    }
}
