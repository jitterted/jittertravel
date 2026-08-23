package dev.ted.jittertravel.domain;

/**
 * Where Ted's talk stands with one conference — <em>derived</em> by folding that conference's
 * submission events, never stored on any of them. The speaking axis of the two-dimensional model
 * in {@code docs/archived/ConferenceSubmissionTrackingPlan.md}; the other axis is
 * {@code AttendanceCommitment}, and the two are independent except at two documented points
 * ({@link TalkAccepted} commits attendance, {@link InvitedToSpeak} deliberately does not).
 *
 * <p><strong>The last submission event wins.</strong> Not "best outcome wins", which the plan
 * originally specified to cope with three proposals to one conference: with waitlisting dropped
 * (Ted, 2026-08-22 — an outcome is accepted or rejected, nothing between), best-outcome ordering
 * breaks the ordinary case, since {@code Submitted → Rejected} would fold back to
 * {@link #SUBMITTED} and the rejection would never surface. Last-wins is correct for every
 * sequence the dashboard's state machine can produce, and it matches how the rest of the codebase
 * folds.
 *
 * <p>The cost is the one the conference-keyed design already accepted: two proposals with
 * different outcomes can only be recorded as one. The dashboard never offers an action that would
 * produce such a sequence, and per-talk state is the change to make if that ever bites — not a
 * cleverer fold.
 *
 * <p><strong>OWNER-only.</strong> Every value here is submission status, which CLAUDE.md keeps
 * private: it must never reach a {@code CalendarEntry}. What may be published is the collapsed
 * boolean "Ted is speaking", and only for a conference he is committed to — see
 * {@link InvitedToSpeak}. This enum lives in {@code domain} rather than beside
 * {@code AttendanceCommitment} in {@code application} for the reason that enum gives for its own
 * placement, read the other way: commands <em>do</em> branch on this one, because it is what makes
 * a transition legal.
 */
public enum SpeakingStatus {

    /** No talk submitted and no invitation received. Every conference starts here. */
    NOT_SPEAKING,

    /** Submitted, waiting to hear. Nothing for Ted to do; the organizers hold this one. */
    SUBMITTED,

    /** Accepted — Ted is speaking, and attendance is committed by that fact alone. */
    ACCEPTED,

    /**
     * Turned down. Whether the conference survives depends on its {@link ConferenceFormat}:
     * {@code CALL_FOR_PAPERS} leaves it needing a decision, {@code ACCEPTANCE_REQUIRED} drops it.
     */
    REJECTED,

    /** Ted pulled the talk. Says nothing about whether he still attends. */
    WITHDRAWN,

    /** The organizers asked, with no CFP. An open offer until Ted confirms or declines. */
    INVITED
}
