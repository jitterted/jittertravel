package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;

public record ConferenceTentativelyPlanned(
        ConferenceId conferenceId,
        String name,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String venueName,
        Address venueAddress
) implements Event {
}
