package dev.ted.jittertravel.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A moment in time stored as a UTC {@link Instant} together with the IANA {@link ZoneId} of the
 * place it refers to (the airport, hotel, venue...). Storing both lets us:
 * <ul>
 *   <li>evaluate past/future by comparing {@link #utc()} — zone-independent and always correct;</li>
 *   <li>render the original wall-clock the traveler entered ({@link #atEntryZone()}); and</li>
 *   <li>re-display the same moment in any other zone ({@link #at(ZoneId)}), e.g. a viewer's
 *       browser zone.</li>
 * </ul>
 *
 * <p>Construct from a typed wall-clock with {@link #fromLocal(LocalDateTime, ZoneId)}; DST is
 * resolved leniently by {@link LocalDateTime#atZone(ZoneId)} (a non-existent spring-forward time
 * shifts forward, a fall-back ambiguity picks the earlier offset).
 */
public record ZonedTimestamp(Instant utc, ZoneId zone) {

    public ZonedTimestamp {
        if (utc == null) {
            throw new IllegalArgumentException("utc must not be null");
        }
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
    }

    /** Interpret a wall-clock time as occurring in {@code zone} and capture the resulting instant. */
    public static ZonedTimestamp fromLocal(LocalDateTime wallClock, ZoneId zone) {
        return new ZonedTimestamp(wallClock.atZone(zone).toInstant(), zone);
    }

    /** The moment in the entry's own zone (the wall-clock originally entered). */
    public ZonedDateTime atEntryZone() {
        return utc.atZone(zone);
    }

    /** The moment rendered in an arbitrary display zone. */
    public ZonedDateTime at(ZoneId displayZone) {
        return utc.atZone(displayZone);
    }

    /** The entry-zone local date-time (the wall-clock the traveler entered). */
    public LocalDateTime localDateTime() {
        return atEntryZone().toLocalDateTime();
    }
}
