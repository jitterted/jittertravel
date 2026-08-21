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
 * <p>
 * {@code publicRoute} is the same shape of field for {@link EntryKind#GROUND_TRANSFER} alone, and
 * another passenger for that refactor. It is <strong>never rendered</strong>: it carries the
 * publishable form of the route ({@code DEN → Lone Tree, CO, US}) purely so
 * {@link CalendarEntryRedactor} has something true to publish, since it cannot derive a city from
 * the owner's title — which names a hotel. The owner's own view is the title plus the times, and
 * repeating the route as a second line there was noise (Ted, 2026-08-20).
 * <p>
 * {@code cancelPath} is the owner-only link to the entry's cancel page, and the third such
 * kind-specific passenger: only {@link EntryKind#GROUND_TRANSFER} sets it, because a transfer has
 * nothing to edit — correcting one means removing it and entering it again. It is a sibling of
 * {@code editPath}, not a replacement: an entry that can be edited is not thereby one that can be
 * cancelled from the calendar. Like {@code editPath} it is dropped by
 * {@link CalendarEntryRedactor} on every branch.
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
        AttendanceCommitment commitment,
        String publicRoute,
        String cancelPath
) {
    /**
     * Convenience constructor for entries with no owner edit link and no public "speaking"
     * marker (hotels, flights sharing a day, private events, and non-speaking gatherings).
     * Keeps the many call sites that predate {@code editPath} and {@code speaking}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle, String mapsUrl) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, false, null, null, null, null);
    }

    /**
     * Convenience constructor for every kind that carries no publishable route — that is, all of
     * them except {@link EntryKind#GROUND_TRANSFER}, whose redaction has to publish something and
     * cannot get it from a title naming a hotel.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle,
                         String mapsUrl, boolean speaking, String editPath,
                         AttendanceCommitment commitment) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle,
                mapsUrl, speaking, editPath, commitment, null, null);
    }

    /**
     * Convenience constructor for entries that carry an owner edit link (booked flights and
     * trains) but are not speaking events. {@code speaking} defaults to {@code false}.
     */
    public CalendarEntry(EntryKind kind, LocalDateTime start, LocalDateTime end,
                         String mainTitle, List<SubtitleLine> subTitle,
                         String continuationTitle, List<SubtitleLine> continuationSubTitle,
                         String mapsUrl, String editPath) {
        this(kind, start, end, mainTitle, subTitle, continuationTitle, continuationSubTitle, mapsUrl, false, editPath, null, null, null);
    }
}
