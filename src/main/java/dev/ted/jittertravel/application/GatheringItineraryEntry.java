package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

public record GatheringItineraryEntry(
        GatheringId gatheringId,
        String title,
        String venueName,
        String city,
        String country,
        boolean speaking,
        String infoUrl,
        ZonedTimestamp anchorDateTime,
        ZonedTimestamp endDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.GATHERING; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime.localDateTime(); }

    public String venueLocation() {
        String prefix = venueName.isBlank() ? "" : venueName + " · ";
        return prefix + city + (country.isBlank() ? "" : ", " + country);
    }
}
