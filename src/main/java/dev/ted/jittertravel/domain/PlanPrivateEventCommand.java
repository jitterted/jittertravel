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
        // Same rule as a gathering: today or later, judged by *date* in the event's own zone
        // rather than the server's. Today is allowed because plans can be last-minute.
        if (startsAt == null || !startsAt.isOnOrAfterDayOf(context.now())) {
            throw new PrivateEventDateNotInFuture("Private event date must be today or later");
        }
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (endsAt == null || !endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidPrivateEventTimeRange("End time must be after start time");
        }
        return Stream.of(new PrivateEventPlanned(privateEventId, title, venueName, location, startsAt, endsAt));
    }
}
