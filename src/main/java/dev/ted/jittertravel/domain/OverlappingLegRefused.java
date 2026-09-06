package dev.ted.jittertravel.domain;

/**
 * The proposed journey collides with one already booked — Ted can only be on one of them, so it is
 * refused rather than written.
 * <p>
 * <strong>Refusal, not a warning</strong> (Ted, 2026-09-06). The report at {@code /schedule-problems}
 * catches an overlap only if Ted visits it; this catches it at the moment it is entered, which is
 * what his near miss actually needed. The objection — that refusing forces "cancel the old one
 * first" — is answered by the two things shipped alongside: cancelling is now one link from three
 * surfaces, and this refusal <em>names the blocking leg as a link</em>, so the error says what is in
 * the way and puts him one click from it.
 * <p>
 * It carries the blocking {@link ScheduledLeg} and nothing else. The boundary turns that into words
 * and a link; the domain neither knows nor formats them.
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
