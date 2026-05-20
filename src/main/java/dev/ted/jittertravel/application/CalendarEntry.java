package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

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
        String subTitle,
        String continuationTitle,
        String continuationSubTitle
) {
}
