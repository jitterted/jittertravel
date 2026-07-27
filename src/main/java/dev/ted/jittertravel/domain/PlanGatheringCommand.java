package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record PlanGatheringCommand(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) implements DomainCommand<GatheringPlanningContext> {

    @Override
    public Stream<GatheringPlanned> execute(GatheringPlanningContext context) {
        // Unchanged rule: a gathering must be planned for a later *date*, not merely a later
        // moment — now judged in the gathering's own zone rather than the server's.
        if (startsAt == null || !startsAt.isOnDayAfter(context.now())) {
            throw new GatheringDateNotInFuture("Gathering date must be in the future");
        }
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (endsAt == null || !endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
        return Stream.of(new GatheringPlanned(gatheringId, title, venueName, location, startsAt, endsAt, speaking, infoUrl));
    }
}
