package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

/**
 * One conference on the OWNER-only {@code /conferences} list.
 * <p>
 * {@code commitment} is derived by folding the conference's attendance events — see
 * {@link AttendanceCommitment}. The list shows it so the backfill pass can see at a glance which
 * conferences still need confirming; the private {@link dev.ted.jittertravel.domain.AttendanceBasis}
 * is not carried here either, because nothing on this page renders it yet.
 */
public record ConferenceView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        AttendanceCommitment commitment
) implements TemporalView {
    public String city() { return venueAddress.city(); }
    public String country() { return venueAddress.country(); }

    /** A conference is "upcoming" until its last day ends, in the venue's zone. */
    @Override
    public Instant relevantUntil() {
        return endDate.utc();
    }
}
