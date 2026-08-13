package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * One line of a {@link CalendarEntry}'s subtitle.
 * <p>
 * Most lines are plain, already-formatted text (a city, a service id). The flight and train
 * lanes, though, put actual moments on the calendar, and those keep their {@link ZonedTimestamp}
 * all the way to the renderer so it can emit a {@code <time>} element carrying the UTC instant
 * alongside the entry-zone wall-clock — the same treatment the list views get.
 * <p>
 * Day bucketing is unaffected: an entry still sits in the column of its entry-zone local day
 * (decision 7 of {@code docs/UtcDatetimeStoragePlan.md}); only the rendered time can be
 * re-localized.
 */
public sealed interface SubtitleLine {

    /** Already-formatted text with no moment in it. */
    record Text(String value) implements SubtitleLine {}

    /** A label and one moment, e.g. {@code Departs 9:00 AM}. */
    record At(String label, ZonedTimestamp moment) implements SubtitleLine {}

    /**
     * Two moments, e.g. {@code 9:00 AM → 5:15 PM}. The endpoints may be in different zones
     * (a Frankfurt→Paris train), so each is rendered in its own.
     */
    record Range(ZonedTimestamp from, ZonedTimestamp to) implements SubtitleLine {}

    /**
     * A start–end range shown in the event's own zone with a zone-abbreviation label
     * (e.g. {@code 7:00 PM → 10:00 PM EDT}), rendered as plain text so the browser-zone script
     * (which only rewrites {@code <time data-fmt>}) never re-localizes it. Used only for the
     * redacted private-event time, which is public in the event's own zone by decision — unlike
     * {@link Range}, whose {@code <time>} elements re-localize to the viewer's zone. See
     * {@code docs/PrivateSocialEventPlan.md}.
     */
    record FixedRange(ZonedTimestamp from, ZonedTimestamp to) implements SubtitleLine {}
}
