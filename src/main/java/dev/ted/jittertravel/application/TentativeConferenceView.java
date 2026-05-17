package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;

import java.time.LocalDateTime;

public record TentativeConferenceView(
        ConferenceId conferenceId,
        String name,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String city,
        String country
) {
}
