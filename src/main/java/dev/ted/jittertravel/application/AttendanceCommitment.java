package dev.ted.jittertravel.application;

/**
 * How committed Ted is to a conference, <em>derived</em> by folding the conference's own events —
 * never stored on any of them. A conference goes on the list as {@link #WATCHING} and becomes
 * {@link #GOING} when attendance is confirmed.
 * <p>
 * This is the one attendance fact that is <strong>public</strong>: every speculative state (CFP not
 * open yet, submitted and waiting, rejected but undecided, not submitting) collapses to
 * {@code WATCHING}, and that collapse is what makes commitment publishable without leaking
 * submission status. The collapse happens in the projector, so the private detail — the
 * {@link dev.ted.jittertravel.domain.AttendanceBasis} — never enters a view at all.
 * <p>
 * There is deliberately no {@code NOT_GOING} value. A declined or organizer-cancelled conference
 * leaves every read model entirely, for every viewer, so "not going" is represented by absence
 * rather than by a value that no renderer could ever be handed. It earns a value here the day
 * something has to render a dropped conference (the plan's eventual "dropped" toggle), not before.
 * <p>
 * Lives in {@code application} rather than {@code domain} because no command branches on it: it is
 * a read-model label. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
 */
public enum AttendanceCommitment {
    /** Being watched, not committed — renders as a public "Maybe" chip. */
    WATCHING,
    /** Going. Renders as a plain entry: "Ted is going" is the default reading of a calendar entry. */
    GOING
}
