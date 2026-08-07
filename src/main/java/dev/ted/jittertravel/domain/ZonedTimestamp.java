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

    /**
     * {@link #fromLocal} for an <em>optional</em> wall-clock: absent stays absent all the way down,
     * rather than becoming a timestamp or tripping the compact constructor. Optional event fields
     * (a hotel's free-cancellation deadline) use this so their null survives the conversion.
     */
    public static ZonedTimestamp fromNullableLocal(LocalDateTime wallClock, ZoneId zone) {
        return wallClock == null ? null : fromLocal(wallClock, zone);
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

    /**
     * Whether this timestamp's calendar day falls after the day {@code reference} lands on, read in
     * <em>this</em> timestamp's zone — so the answer depends only on the entry location and never
     * on where the server runs: a gathering in Tokyo is judged against the date it currently is in
     * Tokyo.
     * <p>
     * This is a day-granularity question ("is it at least tomorrow?"), deliberately coarser than
     * comparing {@link #utc()} — see the gathering commands, whose rule is that a gathering must be
     * planned for a later date, not merely a later moment.
     * <p>
     * Expressed as "reference falls before midnight of this day" rather than by converting the
     * reference to a local date. The two are equivalent, and this form never converts
     * {@code reference} to a zoned date — which matters because import replays pass
     * {@code Instant.MIN} as a bypass sentinel, and that overflows when given a zone.
     */
    public boolean isOnDayAfter(Instant reference) {
        Instant startOfThisDay = atEntryZone().toLocalDate()
                .atStartOfDay(zone)
                .toInstant();
        return reference.isBefore(startOfThisDay);
    }
}
