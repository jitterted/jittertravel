package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * The organizers turned down a talk Ted submitted. <strong>Theirs, not his</strong> — kept distinct
 * from {@link ConferenceAttendanceDeclined}, which is Ted deciding not to go. Conflating the two
 * was the exact merge {@code docs/ConferenceSubmissionTrackingPlan.md} forbids.
 * <p>
 * <strong>What it means depends on the conference's {@link ConferenceFormat}, and that branch is
 * in the read models, not here:</strong>
 * <ul>
 *   <li>{@code CALL_FOR_PAPERS} — the speaking route is gone but attending is not. The conference
 *       stays on the list needing a decision: go as an attendee, or drop it. This is the
 *       rejected-but-undecided state the whole two-axis model exists to represent.</li>
 *   <li>{@code ACCEPTANCE_REQUIRED} — acceptance <em>gated</em> attendance (PLoP), so the
 *       rejection drops the conference: it leaves the calendar and moves to the dashboard's
 *       dropped group. No "go anyway" is offered, because there is no anyway.</li>
 * </ul>
 * The event is the same fact either way; only the fold differs. Recording it never removes
 * anything by itself — a read model that drops the conference does so by folding this together
 * with the format the conference was planned with.
 * <p>
 * <strong>OWNER-only.</strong> A rejection is the most private thing on this axis: no field of it
 * reaches a calendar entry, and the anonymous calendar cannot distinguish a rejected conference
 * from any other one Ted has not committed to.
 *
 * @param decidedOn the moment Ted recorded the outcome, captured at the boundary
 *                  (external-inputs rule) — not necessarily when the organizers decided.
 */
public record TalkRejected(
        ConferenceId conferenceId,
        Instant decidedOn
) implements Event {
}
