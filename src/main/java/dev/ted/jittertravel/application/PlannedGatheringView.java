package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public record PlannedGatheringView(
        GatheringId gatheringId,
        String title,
        String venueName,
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl
) implements TemporalView {

    /**
     * A gathering is "upcoming" until it finishes on its day. STOPGAP: gathering
     * events still store bare wall-clock date/times, so the end is interpreted in
     * the server zone to preserve pre-migration behavior. Once GatheringPlanned
     * carries {@code ZonedTimestamp}s, return the end's {@code utc()} directly
     * (see {@link TemporalView}).
     */
    @Override
    public Instant relevantUntil() {
        return date.atTime(endTime).atZone(ZoneId.systemDefault()).toInstant();
    }
}
