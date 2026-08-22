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
 * The set is deliberately what can be derived <em>before</em> submission tracking exists. Slice 4
 * adds the submission stream, and with it the states this cannot currently tell apart — above all
 * "submitted, waiting to hear", which today is indistinguishable from "have not submitted" and so
 * falls in {@link #CFP_CLOSES_SOON} either way.
 */
public enum DashboardGroup {

    /** A recorded deadline that has not passed. The only group with someone else's clock running. */
    CFP_CLOSES_SOON,

    /**
     * The conference forms its program through a CFP, and Ted has not recorded when that CFP closes.
     * The action is to go and find the date — which is why this is its own group and not merged into
     * {@link #DECIDE}: it is a different job, and it is the one that gates the backfill.
     */
    CFP_DATE_UNKNOWN,

    /**
     * The CFP closed and Ted is still only watching. Attend as a guest, or drop it — but the
     * speaking route is gone.
     */
    DECIDE,

    /** An open-space conference: there is no CFP, so the only question is whether to go. */
    NOTHING_TO_SUBMIT,

    /** Committed. Nothing is owed here; it is on the list so the page is the whole picture. */
    GOING
}
