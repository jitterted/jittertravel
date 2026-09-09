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
 * <p>
 * <strong>{@code commitment} and {@code speaking} are both derived</strong> — folded from the
 * conference's own events through {@link ConferenceProgress}, never stored on one. They were added
 * 2026-09-09 to close a gap in which family saw <em>less</em> than an anonymous visitor: both facts
 * had been on {@code /calendar} for every viewer since {@code e4a7b67}, and the itinerary was never
 * updated. The private half stays private exactly as it does on the calendar — the
 * {@code AttendanceBasis} is answered as {@link ConferenceProgress#speaking()} and never carried
 * onto this record.
 */
public record ConferenceItineraryEntry(
        ConferenceId conferenceId,
        String name,
        String venueName,
        Address venueAddress,
        int dayNumber,
        int totalDays,
        LocalDateTime anchorDateTime,
        String infoUrl,
        boolean speaking,
        AttendanceCommitment commitment
) implements ItineraryEntry {

    @Override public EntryKind kind() { return EntryKind.CONFERENCE; }
    @Override public LocalDateTime anchorTime() { return anchorDateTime; }
}
