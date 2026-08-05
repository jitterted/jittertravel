package dev.ted.jittertravel.domain;

/**
 * A conference has been tentatively planned — a multi-day (or single-full-day) event, as opposed to
 * a {@link GatheringPlanned}, which is a few hours on one day.
 * <p>
 * {@code startDate}/{@code endDate} are {@link ZonedTimestamp}s in the venue's single zone, so "is
 * it over?" is answerable without knowing where the server runs. Legacy payloads carrying bare
 * wall-clock scalars are rewritten by {@code EventPayloadUpcaster} at read time.
 */
public record ConferenceTentativelyPlanned(
        ConferenceId conferenceId,
        String name,
        ZonedTimestamp startDate,
        ZonedTimestamp endDate,
        String venueName,
        Address venueAddress
) implements Event {
}
