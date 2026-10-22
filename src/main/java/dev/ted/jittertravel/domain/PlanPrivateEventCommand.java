package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

public record PlanPrivateEventCommand(
        PrivateEventId privateEventId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt
) implements DomainCommand<PlanPrivateEventContext> {

    /**
     * What the form alone can get wrong is refused here, before the command exists, so it never
     * reaches the command log: a command that was never valid is invalid data, not a domain
     * refusal (Ted, 2026-10-22; {@code docs/ValidateBeforeTheWalPlan.md}). Only what needs
     * state — the date being today or later — stays in {@link #execute}, where the refusal is
     * recorded.
     */
    public PlanPrivateEventCommand {
        EnteredCountry.of(location).check(LocationRole.VENUE);
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (!endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidPrivateEventTimeRange("End time must be after start time");
        }
    }

    @Override
    public Stream<PrivateEventPlanned> execute(PlanPrivateEventContext context) {
        // Same rule as a gathering: today or later, judged by *date* in the event's own zone
        // rather than the server's. Today is allowed because plans can be last-minute.
        if (!startsAt.isOnOrAfterDayOf(context.now())) {
            throw new PrivateEventDateNotInFuture("Private event date must be today or later");
        }
        return Stream.of(new PrivateEventPlanned(privateEventId, title, venueName, location, startsAt, endsAt));
    }
}
