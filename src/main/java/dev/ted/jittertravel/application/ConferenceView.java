package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
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
 * <strong>The submission stream is authoritative for it, and the basis is only the fallback.</strong>
 * Where {@code speakingStatus} has anything to say — accepted, submitted, rejected, withdrawn — it
 * decides, because those are history; the basis decides only where the stream is silent, which is
 * exactly the conferences recorded before those events existed. An invitation is the one case
 * needing both: it is speaking only once Ted said yes, which is a confirmation carrying
 * {@code SPEAKING_INVITED}.
 * <p>
 * {@code speakingStatus} is where the talk stands, and it is what the dashboard groups and its
 * per-row actions read. OWNER-only, like the whole axis — see
 * {@link dev.ted.jittertravel.domain.SpeakingStatus}. It is <em>not</em> a second opinion about
 * {@code speaking}: that boolean is derived from this together with the commitment.
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
        SpeakingStatus speakingStatus,
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
