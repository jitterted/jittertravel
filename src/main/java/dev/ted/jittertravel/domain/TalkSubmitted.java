package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Ted submitted a talk to a conference's call for papers — the entry point onto the speaking axis.
 * <p>
 * <strong>Conference-keyed, not talk-keyed.</strong> There is no {@code SubmissionId} and no talk
 * title: for calendaring it does not matter whether Ted submitted one proposal or three, so this
 * reads as "I submitted (one or more talks) to this CFP". When per-talk state earns its keep — a
 * page that lists proposals by title — add the id and the title then, not before
 * ({@code docs/ConferenceSubmissionTrackingPlan.md}).
 * <p>
 * <strong>The talk title is deliberately absent, and that is also a redaction property.</strong>
 * Titles are on the private list in CLAUDE.md; an event that never carries one cannot leak one.
 * <p>
 * Submitting is <em>opting in</em>: it is why {@link TalkAccepted} commits attendance on its own,
 * with no separate confirmation. Ted decided when he submitted.
 *
 * @param submittedOn the moment Ted recorded the submission, captured at the boundary
 *                    (external-inputs rule). Not the CFP's own deadline — that is {@link CfpOpened}.
 */
public record TalkSubmitted(
        ConferenceId conferenceId,
        Instant submittedOn
) implements Event {
}
