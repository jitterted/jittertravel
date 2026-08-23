package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

import java.time.LocalDateTime;

/**
 * One day of a conference on the itinerary.
 * <p>
 * {@code infoUrl} is the conference's own public page — {@code ""} when none was recorded, the same
 * shape a {@link GatheringItineraryEntry} carries, so the renderer treats both titles alike.
 */
public record ConferenceItineraryEntry(
        String name,
        String venueName,
        Address venueAddress,
        int dayNumber,
        int totalDays,
        LocalDateTime anchorDateTime,
        String infoUrl
) implements ItineraryEntry {

    /** Convenience overload for call sites that predate the conference's own web page. */
    public ConferenceItineraryEntry(String name, String venueName, Address venueAddress,
                                    int dayNumber, int totalDays, LocalDateTime anchorDateTime) {
        this(name, venueName, venueAddress, dayNumber, totalDays, anchorDateTime, "");
    }

    @Override public EntryKind kind() { return EntryKind.CONFERENCE; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
