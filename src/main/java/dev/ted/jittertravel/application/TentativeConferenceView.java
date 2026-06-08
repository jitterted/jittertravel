package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;

import java.time.LocalDateTime;

public record TentativeConferenceView(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public String city() { return venueAddress.city(); }
    public String country() { return venueAddress.country(); }
}
