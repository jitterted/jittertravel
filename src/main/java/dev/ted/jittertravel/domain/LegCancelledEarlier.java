package dev.ted.jittertravel.domain;

/**
 * The paste lists a leg that was cancelled on its own earlier. A cancellation cannot be undone, so
 * the leg is not quietly booked again by a schedule change (Ted, 2026-10-03): it is refused, and
 * Ted decides what to do about it by hand.
 */
public class LegCancelledEarlier extends RuntimeException {
    public LegCancelledEarlier() {
        super("Cancelled earlier; it cannot be added back here");
    }
}
