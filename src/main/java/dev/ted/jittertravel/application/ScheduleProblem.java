package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;

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

    record SchedulingConflict(
            String gathering1Name,
            LocalTime gathering1Start,
            LocalTime gathering1End,
            String gathering2Name,
            LocalTime gathering2Start,
            LocalTime gathering2End,
            LocalDate date
    ) implements ScheduleProblem {}

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
