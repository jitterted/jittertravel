package dev.ted.jittertravel.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record GatheringPlanned(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
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
