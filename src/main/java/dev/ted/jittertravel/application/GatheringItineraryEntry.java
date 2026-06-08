package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

public record GatheringItineraryEntry(
        String title,
        String venueName,
        String city,
        String country,
        boolean speaking,
        String infoUrl,
        LocalDateTime anchorDateTime,
        LocalDateTime endDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.GATHERING; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }

    public String venueLocation() {
        String prefix = venueName.isBlank() ? "" : venueName + " · ";
        return prefix + city + (country.isBlank() ? "" : ", " + country);
    }
}
