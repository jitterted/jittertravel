package dev.ted.jittertravel.domain;

/**
 * A gathering — a few hours on a single day, often an evening — has been planned.
 * <p>
 * {@code startsAt}/{@code endsAt} replace the former {@code date} + {@code startTime} +
 * {@code endTime} trio: both endpoints are at the same venue, so they share one zone, and storing
 * instants makes "is it over?" answerable without knowing where the server runs. Legacy payloads
 * carrying the three wall-clock fields are merged by {@code EventPayloadUpcaster} at read time.
 */
public record GatheringPlanned(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) implements Event {
    public GatheringPlanned {
        if (venueName == null) {
            venueName = "";
        }
        if (infoUrl == null) {
            infoUrl = "";
        }
    }
}
