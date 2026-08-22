package dev.ted.jittertravel.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A calendar entry view, pre-formatted by a projector for rendering by
 * {@code CalendarViewBuilder}. Titles are display-ready strings; subtitles are
 * {@link SubtitleLine}s, so a line that names a moment can keep its
 * {@link dev.ted.jittertravel.domain.ZonedTimestamp} through to the {@code <time>} element.
 * <p>
 * The fields here are the ones <em>every</em> kind has. Everything that applies to some kinds only
 * — a maps link, an edit path, a speaking marker, an attendance commitment — lives in
 * {@link EntryDetails}, of which an entry carries exactly one. {@link #kind()} is read off those
 * details rather than stored, so the entry cannot disagree with itself about what it is
 * (decision E2, {@code docs/RendererVsProjectorResponsibilities.md}, 2026-08-19).
 * <p>
 * {@code start}/{@code end} stay entry-zone wall-clock: they place the segment in a day
 * column, and that column is always the local day at the location (decision 7).
 * <p>
 * Multi-week / multi-day entries render as one segment per week. The segment that
 * contains the entry's {@code start} day uses {@code mainTitle} / {@code subTitle};
 * subsequent (continuation) segments use {@code continuationTitle} /
 * {@code continuationSubTitle}, skipping any field that is {@code null}. These stay core rather
 * than moving into the details: they are read by layout, which must not have to know the kind to
 * draw a week boundary.
 */
public record CalendarEntry(
        LocalDateTime start,
        LocalDateTime end,
        String mainTitle,
        List<SubtitleLine> subTitle,
        String continuationTitle,
        List<SubtitleLine> continuationSubTitle,
        EntryDetails details
) {
    /**
     * Convenience constructor for an entry that never spans a week boundary, and so needs no
     * continuation title or subtitle. Unlike the overloads this replaced, it omits nothing that
     * applies to the kind — every kind-specific field is named inside {@code details}.
     */
    public CalendarEntry(LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         EntryDetails details) {
        this(start, end, mainTitle, subTitle, null, null, details);
    }

    /** The lane this entry renders in, derived from its details — see {@link EntryDetails}. */
    public EntryKind kind() {
        return details.kind();
    }
}
