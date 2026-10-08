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
        EnteredCountry.of(location).check(LocationRole.VENUE);
        // Today or later, judged by *date* in the gathering's own zone rather than the server's.
        // Today is allowed because gatherings can be last-minute (Ted, 2026-09-22).
        if (startsAt == null || !startsAt.isOnOrAfterDayOf(context.now())) {
            throw new GatheringDateNotInFuture("Gathering date must be today or later");
        }
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (endsAt == null || !endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
        return Stream.of(new GatheringPlanned(gatheringId, title, venueName, location, startsAt, endsAt, speaking, infoUrl));
    }
}
