package dev.ted.jittertravel.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

public record PlanGatheringCommand(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl
) implements DomainCommand<GatheringPlanningContext> {

    @Override
    public Stream<GatheringPlanned> execute(GatheringPlanningContext context) {
        if (date == null || !date.isAfter(context.today())) {
            throw new GatheringDateNotInFuture("Gathering date must be in the future");
        }
        if (endTime == null || !endTime.isAfter(startTime)) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
        return Stream.of(new GatheringPlanned(gatheringId, title, venueName, location, date, startTime, endTime, speaking, infoUrl));
    }
}
