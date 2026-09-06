package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;

import java.time.LocalDateTime;

/**
 * One day of a conference on the itinerary.
 * <p>
 * {@code infoUrl} is the conference's own public page — {@code ""} when none was recorded, the same
 * shape a {@link GatheringItineraryEntry} carries, so the renderer treats both titles alike.
 * <p>
 * {@code conferenceId} is here for the same reason a {@link GatheringItineraryEntry} carries its
 * own: it is what lets the card link to the conference's page in the app. The itinerary is
 * OWNER/FAMILY only, and the renderer builds that link for the owner alone
 * ({@code docs/ConferenceDetailAndChangePlan.md}).
 */
public record ConferenceItineraryEntry(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        int dayNumber,
        int totalDays,
        LocalDateTime anchorDateTime,
        String infoUrl
) implements ItineraryEntry {

    /** Convenience overload for call sites that predate the conference's own web page. */
    public ConferenceItineraryEntry(ConferenceId conferenceId, String name, String venueName,
                                    Address venueAddress, int dayNumber, int totalDays,
                                    LocalDateTime anchorDateTime) {
        this(conferenceId, name, venueName, venueAddress, dayNumber, totalDays, anchorDateTime, "");
    }

    @Override public EntryKind kind() { return EntryKind.CONFERENCE; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
