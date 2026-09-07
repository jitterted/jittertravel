package dev.ted.jittertravel.domain;

/**
 * The proposed journey collides with one already booked — Ted can only be on one of them, so it is
 * refused rather than written.
 * <p>
 * Refusal, not a warning (Ted, 2026-09-06): {@code /schedule-problems} catches an overlap only if
 * Ted visits it, and this catches it as it is typed. The objection — that refusing forces "cancel
 * the old one first" — is answered by cancel now being one link from three surfaces, and by this
 * refusal naming the blocking leg as a link.
 * <p>
 * It carries the blocking {@link ScheduledLeg} and nothing else; the boundary makes the words.
 */
public class OverlappingLegRefused extends RuntimeException {

    private final transient ScheduledLeg blocking;

    public OverlappingLegRefused(ScheduledLeg blocking) {
        super("Proposed journey overlaps an existing leg at " + blocking.id().path());
        this.blocking = blocking;
    }

    public ScheduledLeg blocking() {
        return blocking;
    }
}
