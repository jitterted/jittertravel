package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

/**
 * A list-view row that occupies a point or span in time, so the shared
 * FUTURE/ALL toggle can decide whether it is still "upcoming".
 * <p>
 * {@link #relevantUntil()} returns the instant after which the item is in the
 * past. For point-in-time items (a train or flight departure) that is simply
 * the departure; for multi-day items (a hotel stay, a multi-day conference, a
 * gathering) it is the <em>end</em> — the item stays visible under FUTURE
 * while it is still in progress.
 *
 * @see TimeView#includes(TemporalView, java.time.LocalDateTime)
 */
public interface TemporalView {
    LocalDateTime relevantUntil();
}