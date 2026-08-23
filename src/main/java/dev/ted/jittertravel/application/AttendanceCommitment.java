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
 * <strong>{@link #NOT_GOING} exists only because one surface renders it.</strong> It was left out
 * until 2026-08-22, when the OWNER-only {@code /conferences} dashboard gained its dropped group:
 * a conference Ted declined stays on that list, behind {@code ?dropped=show}, so "looked at it,
 * said no" is a record next year's entry can benefit from. Everywhere else — the calendar, the
 * itinerary — a declined conference still leaves entirely, for every viewer, so those read models
 * never construct this value and no {@code CalendarEntry} can carry it.
 * <p>
 * Lives in {@code application} rather than {@code domain} because no command branches on it: it is
 * a read-model label. See {@code docs/archived/ConferenceSubmissionTrackingPlan.md}.
 */
public enum AttendanceCommitment {
    /** Being watched, not committed — renders as a public "Maybe" chip. */
    WATCHING,
    /** Going. Renders as a plain entry: "Ted is going" is the default reading of a calendar entry. */
    GOING,

    /**
     * Ted declined. <strong>Dashboard-only</strong>, and never public: a dropped conference is
     * absent from the calendar rather than marked on it, so an anonymous viewer cannot tell a
     * declined conference from one that was never planned.
     */
    NOT_GOING
}
