package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

/**
 * One conference on the OWNER-only {@code /conferences} list.
 * <p>
 * {@code commitment} is derived by folding the conference's attendance events — see
 * {@link AttendanceCommitment}.
 * <p>
 * {@code speaking} is derived too, and deliberately a <strong>boolean rather than the
 * {@link dev.ted.jittertravel.domain.AttendanceBasis} it comes from</strong>: which of the two
 * speaking bases applies — accepted, or invited — is submission status, and a field that never
 * enters a view cannot leak from it. Same reasoning as {@code CalendarEntry} carrying only a
 * collapsed commitment.
 * <p>
 * The basis is only a <em>stand-in</em> source. From slice 4 the submission fold
 * ({@code TalkAccepted}, {@code InvitedToSpeak}) is authoritative, and the basis stays as the
 * evidence for conferences recorded before those events existed; if the two ever disagree the
 * stream wins, because the basis is a manual annotation and the events are history. See
 * {@code docs/ConferenceSubmissionTrackingPlan.md}.
 * <p>
 * {@code cfpClosesOn} is the CFP deadline if one has been recorded, and {@code null} if not. The
 * two absences are different questions and the dashboard asks both: a conference with no CFP recorded
 * needs Ted to go and find the date, while one whose deadline has passed needs him to decide. Null
 * means only "not recorded" — never "no CFP exists", which is what {@code ConferenceFormat}
 * says.
 */
public record ConferenceView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        AttendanceCommitment commitment,
        boolean speaking,
        ZonedTimestamp cfpClosesOn,
        ConferenceFormat format
) implements TemporalView {
    public String city() { return venueAddress.city(); }
    public String country() { return venueAddress.country(); }

    /** A conference is "upcoming" until its last day ends, in the venue's zone. */
    @Override
    public Instant relevantUntil() {
        return endDate.utc();
    }
}
