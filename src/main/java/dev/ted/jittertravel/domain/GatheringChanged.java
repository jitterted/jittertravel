package dev.ted.jittertravel.domain;

/**
 * Full new snapshot of a gathering after an in-place edit. Shares {@link GatheringPlanned}'s
 * {@code startsAt}/{@code endsAt} shape (and its upcasting of legacy wall-clock payloads).
 */
public record GatheringChanged(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) implements Event {
    public GatheringChanged {
        if (venueName == null) {
            venueName = "";
        }
        if (infoUrl == null) {
            infoUrl = "";
        }
    }
}
