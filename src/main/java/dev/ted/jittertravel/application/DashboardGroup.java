package dev.ted.jittertravel.application;

/**
 * What a conference needs from Ted next — the question {@code /conferences} is grouped by.
 * <p>
 * <strong>Declaration order is display order, and it is urgency order.</strong> A CFP closing is the
 * only thing here with someone else's clock on it; a conference already committed to needs nothing
 * at all and sits last.
 * <p>
 * These are derived per request, never stored: the same conference moves between groups as its
 * deadline passes, with no event and no write. That is the point of deriving status rather than
 * storing it ({@code docs/ConferenceSubmissionTrackingPlan.md}).
 * <p>
 * No headings or guidance text here, deliberately — how a group is worded belongs to the renderer
 * (CLAUDE.md, "Presentation formatting stays out of the domain"; the rule is about display strings,
 * and an application-layer enum is no better a place for them than a domain one).
 * <p>
 * <strong>Commitment is asked before anything else</strong> ({@link ConferenceDashboard}): a
 * conference Ted has answered — going, or dropped — needs nothing from him whatever its CFP or its
 * submission is doing. Everything between those two ends is about a conference still merely
 * watched, and there the speaking axis is asked before the CFP clock, because a submitted talk
 * makes the deadline moot.
 */
public enum DashboardGroup {

    /** A recorded deadline that has not passed. The only group with someone else's clock running. */
    CFP_CLOSES_SOON,

    /**
     * The organizers asked Ted to speak and he has not answered. Second only to a closing CFP,
     * because someone is waiting on him — an invitation has no published deadline but it does have
     * a person at the other end.
     */
    INVITED,

    /**
     * The conference forms its program through a CFP, and Ted has not recorded when that CFP closes.
     * The action is to go and find the date — which is why this is its own group and not merged into
     * {@link #DECIDE}: it is a different job, and it is the one that gates the backfill.
     */
    CFP_DATE_UNKNOWN,

    /**
     * The speaking route is gone and Ted is still only watching: either the CFP closed without a
     * submission, or a talk was turned down at a conference that does not require acceptance.
     * Attend as a guest, or drop it.
     */
    DECIDE,

    /** An open-space conference: there is no CFP, so the only question is whether to go. */
    NOTHING_TO_SUBMIT,

    /**
     * Submitted, and the organizers have not said. Below the groups that need a decision precisely
     * because it needs none — the only move here is recording what they eventually say. This is
     * the state slice 3 could not tell apart from "have not submitted"; both fell in
     * {@link #CFP_CLOSES_SOON}, which was the whole reason for the submission stream.
     */
    WAITING_TO_HEAR,

    /** Committed. Nothing is owed here; it is on the list so the page is the whole picture. */
    GOING,

    /**
     * Ted said no. Last, and hidden altogether unless {@code ?dropped=show} asks for it: this is a
     * record to look back on, not work to do. Only reachable through {@link DroppedView#SHOW}, so
     * the group is absent from the default page rather than merely empty.
     */
    DROPPED
}
