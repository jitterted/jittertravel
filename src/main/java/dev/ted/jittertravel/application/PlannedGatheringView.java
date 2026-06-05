package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlannedGatheringView(
        GatheringId gatheringId,
        String title,
        String venueName,
        String city,
        String country,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl
) {}
