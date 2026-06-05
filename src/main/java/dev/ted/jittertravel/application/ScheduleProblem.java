package dev.ted.jittertravel.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public sealed interface ScheduleProblem
        permits ScheduleProblem.MissingTravel, ScheduleProblem.MissingHotel, ScheduleProblem.SchedulingConflict {

    record MissingTravel(
            String fromCity,
            LocalDateTime arrivedAt,
            String toCity,
            LocalDateTime nextDepartureAt
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
}
