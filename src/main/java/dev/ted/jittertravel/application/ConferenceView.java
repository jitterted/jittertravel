package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

public record ConferenceView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate
) implements TemporalView {
    public String city() { return venueAddress.city(); }
    public String country() { return venueAddress.country(); }

    /** A conference is "upcoming" until its last day ends, in the venue's zone. */
    @Override
    public Instant relevantUntil() {
        return endDate.utc();
    }
}
