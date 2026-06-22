package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record TentativeConferenceView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        LocalDateTime startDate,
        LocalDateTime endDate
) implements TemporalView {
    public String city() { return venueAddress.city(); }
    public String country() { return venueAddress.country(); }

    /**
     * A conference is "upcoming" until its last day ends. STOPGAP: conference
     * events still store bare wall-clock times, so the end is interpreted in the
     * server zone to preserve pre-migration behavior. Once
     * ConferenceTentativelyPlanned carries {@code ZonedTimestamp}s, return the
     * end's {@code utc()} directly (see {@link TemporalView}).
     */
    @Override
    public Instant relevantUntil() {
        return endDate.atZone(ZoneId.systemDefault()).toInstant();
    }
}
