package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

/**
 * A private social event on the owner/family itinerary. The itinerary is behind auth
 * (OWNER/FAMILY only), so unlike the anonymous calendar it shows full detail — the redaction
 * threat model is anonymous viewers. Mirrors {@link GatheringItineraryEntry} minus the public
 * {@code speaking}/{@code infoUrl} fields.
 */
public record PrivateEventItineraryEntry(
        String title,
        String venueName,
        String city,
        String country,
        ZonedTimestamp anchorDateTime,
        ZonedTimestamp endDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.PRIVATE_EVENT; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime.localDateTime(); }

    public String venueLocation() {
        String prefix = venueName.isBlank() ? "" : venueName + " · ";
        return prefix + city + (country.isBlank() ? "" : ", " + country);
    }
}
