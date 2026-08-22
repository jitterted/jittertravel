package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Ted pulled his talk — a schedule clash, a better offer the same week, or simply a change of mind.
 * <strong>His decision</strong>, unlike {@link TalkRejected}.
 * <p>
 * <strong>Withdrawing a talk says nothing about attending.</strong> It moves the speaking axis
 * only: a conference Ted is committed to stays committed and simply stops being one he speaks at,
 * which is why this is reachable from an accepted talk and not only from a pending one. Deciding
 * not to go is {@link ConferenceAttendanceDeclined}, a separate act on the other axis.
 * <p>
 * No reason field. The original draft carried free text, and nothing was ever going to read it:
 * reasons are on the private list in CLAUDE.md, so it could not be rendered publicly, and the
 * dashboard shows a state rather than a story. An absent field cannot leak and cannot go stale.
 *
 * @param withdrawnOn the moment Ted recorded the withdrawal, captured at the boundary
 *                    (external-inputs rule).
 */
public record TalkWithdrawn(
        ConferenceId conferenceId,
        Instant withdrawnOn
) implements Event {
}
