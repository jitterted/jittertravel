package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;

public record PlannedGatheringView(
        GatheringId gatheringId,
        String title,
        String venueName,
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) implements TemporalView {

    /**
     * A gathering is "upcoming" until it finishes — the instant it ends at its venue, which is the
     * same moment no matter where the server or the viewer is.
     */
    @Override
    public Instant relevantUntil() {
        return endsAt.utc();
    }
}
