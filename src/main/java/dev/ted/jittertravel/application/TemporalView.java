package dev.ted.jittertravel.application;

import java.time.Instant;

/**
 * A list-view row that occupies a point or span in time, so the shared
 * FUTURE/ALL toggle can decide whether it is still "upcoming".
 * <p>
 * {@link #relevantUntil()} returns the {@link Instant} after which the item is
 * in the past. For point-in-time items (a train or flight departure) that is
 * simply the departure; for multi-day items (a hotel stay, a multi-day
 * conference, a gathering) it is the <em>end</em> — the item stays visible
 * under FUTURE while it is still in progress.
 * <p>
 * Comparing instants (rather than wall-clock {@code LocalDateTime}s) is what
 * makes the FUTURE/past boundary correct regardless of where the item is or
 * what zone the server runs in: a hotel that has already been checked out of
 * drops off FUTURE the moment its checkout instant passes. Views backed by a
 * {@code ZonedTimestamp} return its {@code utc()} directly; views whose events
 * still store bare wall-clock times interpret them in the server zone as a
 * documented stopgap until those events migrate.
 *
 * @see TimeView#includes(TemporalView, Instant)
 */
public interface TemporalView {
    Instant relevantUntil();
}