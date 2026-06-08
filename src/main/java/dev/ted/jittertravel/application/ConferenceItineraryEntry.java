package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

import java.time.LocalDateTime;

public record ConferenceItineraryEntry(
        String name,
        String venueName,
        Address venueAddress,
        int dayNumber,
        int totalDays,
        LocalDateTime anchorDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.CONFERENCE; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
