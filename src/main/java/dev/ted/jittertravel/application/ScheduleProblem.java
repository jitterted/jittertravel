package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;

public sealed interface ScheduleProblem
        permits ScheduleProblem.MissingTravel, ScheduleProblem.MissingHotel,
                ScheduleProblem.SchedulingConflict, ScheduleProblem.DifferentCityConflict {

    /**
     * The two endpoints are in different cities and therefore usually different zones, so they are
     * {@link ZonedTimestamp}s: the gap between them is only meaningful as a comparison of instants.
     * Renderers show {@code localDateTime()} — the wall-clock at each end.
     */
    record MissingTravel(
            String fromCity,
            ZonedTimestamp arrivedAt,
            String toCity,
            ZonedTimestamp nextDepartureAt
    ) implements ScheduleProblem {}

    record MissingHotel(
            String city,
            LocalDate checkIn,
            LocalDate checkOut,
            String conferenceName
    ) implements ScheduleProblem {}

    /**
     * Two gatherings whose instants overlap. Each side carries its <em>own</em>
     * {@link ZonedTimestamp}s and city: overlapping gatherings in different zones can fall on
     * different local dates (a San Francisco evening and a Tokyo morning), so there is no single
     * date to report — showing one gathering's date beside the other's times reads as wrong
     * exactly when the instant-based detection has done its job.
     */
    record SchedulingConflict(
            ConflictingGathering first,
            ConflictingGathering second
    ) implements ScheduleProblem {}

    record ConflictingGathering(
            String name,
            String city,
            ZonedTimestamp startsAt,
            ZonedTimestamp endsAt
    ) {}

    record DifferentCityConflict(
            String gatheringName,
            String gatheringCity,
            String conferenceName,
            String conferenceCity,
            LocalDate date,
            GatheringId gatheringId,
            ConferenceId conferenceId
    ) implements ScheduleProblem {}
}
