package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * The organizers accepted a talk Ted submitted. Named for what happened in the world, not for who
 * typed it — the same reading as {@link CfpOpened} and {@link ConferenceCancelled}.
 * <p>
 * <strong>This commits attendance on its own.</strong> The folds derive
 * {@code AttendanceCommitment.GOING} from an acceptance with no accompanying
 * {@link ConferenceAttendanceConfirmed}, because submitting a talk <em>was</em> the opt-in: an
 * acceptance completes a decision Ted already made rather than presenting him with a new one
 * (Ted, 2026-08-12). "Accepted, then couldn't go" stays representable, because the last decision
 * wins — a later {@link ConferenceAttendanceDeclined} overrides this.
 * <p>
 * Contrast {@link InvitedToSpeak}, which deliberately does <em>not</em> commit: an unsolicited
 * offer is not the completion of anything Ted started.
 * <p>
 * <strong>OWNER-only, like every event on this axis.</strong> Submission outcomes are on the
 * private list in CLAUDE.md. What reaches the public calendar is the collapsed fact that Ted is
 * speaking at a conference he is committed to — never the acceptance that produced it, and never
 * its date. {@code PublicCalendarProjector} reads this event for the speaking flag alone.
 *
 * @param decidedOn the moment Ted recorded the outcome, captured at the boundary
 *                  (external-inputs rule) — not necessarily when the organizers decided.
 */
public record TalkAccepted(
        ConferenceId conferenceId,
        Instant decidedOn
) implements Event {
}
