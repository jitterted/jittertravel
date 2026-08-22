package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * The organizers asked Ted to speak, with no CFP involved. Conference-keyed, since there is no
 * submission to key on.
 * <p>
 * <strong>An invitation is an offer, so it does not commit attendance</strong> — and that is the
 * one place this event deliberately differs from {@link TalkAccepted}. An acceptance completes a
 * decision Ted made when he submitted; an invitation is unsolicited, so it is a question awaiting
 * his yes. Saying yes is an explicit
 * {@link ConferenceAttendanceConfirmed} with {@link AttendanceBasis#SPEAKING_INVITED}; saying no is
 * {@link ConferenceAttendanceDeclined}. Until one of those, the conference sits in the dashboard's
 * invited group with the offer still open.
 * <p>
 * Legal for every {@link ConferenceFormat}, including {@code OPEN_SPACE}: an open-space conference
 * has no CFP to submit to, but its organizers can still ask Ted to give a keynote.
 * <p>
 * <strong>OWNER-only, and it is the reason the public speaking badge is gated on commitment.</strong>
 * An unanswered invitation must not reach the anonymous calendar — a stranger seeing "Maybe" plus a
 * speaking badge would learn Ted had been asked somewhere he has not decided about. The public
 * projector reads this event only in combination with a confirmation (Ted, 2026-08-22).
 *
 * @param invitedOn the moment Ted recorded the invitation, captured at the boundary
 *                  (external-inputs rule) — not necessarily when the organizers sent it.
 */
public record InvitedToSpeak(
        ConferenceId conferenceId,
        Instant invitedOn
) implements Event {
}
