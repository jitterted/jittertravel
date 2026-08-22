package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Ted is going to a conference he had been watching — the commitment half of the pair whose other
 * half is {@link ConferenceAttendanceDeclined}. Both are <em>his</em> decisions, and both stay
 * distinct from {@link ConferenceCancelled} (the organizers pulled the event).
 * <p>
 * Attendance commitment is <em>derived</em>, never stored on {@link ConferencePlanned}: a planned
 * conference starts out merely watched, and folding this event over it is what turns it into "going".
 * The last decision wins, so a later decline overrides a confirmation and a later confirmation
 * overrides a decline-shaped correction of basis.
 * <p>
 * The {@code basis} is the private half of this event: the collapsed commitment level is public
 * (it renders as the absence of a "Maybe" chip on {@code /calendar}), but <em>why</em> Ted is going
 * is submission status wearing a different hat, so it must never enter a {@code CalendarEntry}.
 * See {@code docs/ConferenceSubmissionTrackingPlan.md} and CLAUDE.md.
 *
 * @param basis       why he is going. Never null: it is the whole point of the event, and unlike a
 *                    free-text reason there is no sensible empty value, so an absent one fails loud
 *                    rather than reaching a projector as a null.
 * @param confirmedOn the moment Ted confirmed, captured at the boundary (external-inputs rule).
 */
public record ConferenceAttendanceConfirmed(
        ConferenceId conferenceId,
        AttendanceBasis basis,
        Instant confirmedOn
) implements Event {

    public ConferenceAttendanceConfirmed {
        if (basis == null) {
            throw new IllegalArgumentException(
                    "basis must not be null — every confirmed attendance records why Ted is going");
        }
    }
}
