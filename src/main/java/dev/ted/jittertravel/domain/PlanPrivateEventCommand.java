package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public record PlanPrivateEventCommand(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt
) implements DomainCommand<PlanPrivateEventContext> {

    @Override
    public Stream<PrivateEventPlanned> execute(PlanPrivateEventContext context) {
        // Same rule as a gathering: a private event must be planned for a later *date*, not merely
        // a later moment — judged in the event's own zone rather than the server's.
        if (startsAt == null || !startsAt.isOnDayAfter(context.now())) {
            throw new PrivateEventDateNotInFuture("Private event date must be in the future");
        }
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (endsAt == null || !endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidPrivateEventTimeRange("End time must be after start time");
        }
        return Stream.of(new PrivateEventPlanned(privateEventId, title, venueName, location, startsAt, endsAt));
    }
}
