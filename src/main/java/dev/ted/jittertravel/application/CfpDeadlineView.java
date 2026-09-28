package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

/**
 * A conference view that carries a recorded CFP deadline, so every page asks "is this CFP still
 * open?" the same way.
 * <p>
 * Two surfaces ask it: the {@code /conferences} dashboard, which groups by it and hides the Submit
 * link by it, and the conference's detail page, which counts down to it and offers "Submit a talk"
 * by it. Each did its own {@code isAfter(now)} until 2026-09-28, and the two copies were one edit
 * away from disagreeing about the same conference at the same moment.
 * <p>
 * <strong>Open means a deadline is recorded and has not passed.</strong> An unrecorded deadline is
 * not open (there is nothing to count down), and the deadline instant itself is already closed.
 * {@code now} comes from the boundary, like every other clock read.
 */
public interface CfpDeadlineView {

    /** The recorded deadline, or {@code null} when none has been recorded. */
    ZonedTimestamp cfpClosesOn();

    default boolean cfpOpenAt(Instant now) {
        return cfpClosesOn() != null
               && cfpClosesOn().utc().isAfter(now);
    }
}
