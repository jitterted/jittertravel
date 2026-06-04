package dev.ted.jittertravel.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public sealed interface ScheduleProblem
        permits ScheduleProblem.MissingTravel, ScheduleProblem.MissingHotel {

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
}
