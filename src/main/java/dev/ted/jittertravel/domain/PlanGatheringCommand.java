package dev.ted.jittertravel.domain;

import java.util.Objects;
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

    /**
     * What the form alone can get wrong is refused here, before the command exists, so it never
     * reaches the command log: a command that was never valid is invalid data, not a domain
     * refusal (Ted, 2026-10-22; {@code docs/archived/ValidateBeforeTheWalPlan.md}). Only what needs
     * state — the date being today or later — stays in {@link #execute}, where the refusal is
     * recorded.
     */
    public PlanGatheringCommand {
        EnteredCountry.of(location).check(LocationRole.VENUE);
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        // Both endpoints share the venue's zone, so comparing instants is the same as comparing
        // wall-clock — and stays right if that ever stops being true.
        if (!endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
    }

    @Override
    public Stream<GatheringPlanned> execute(GatheringPlanningContext context) {
        // Today or later, judged by *date* in the gathering's own zone rather than the server's.
        // Today is allowed because gatherings can be last-minute (Ted, 2026-09-22).
        if (!startsAt.isOnOrAfterDayOf(context.now())) {
            throw new GatheringDateNotInFuture("Gathering date must be today or later");
        }
        return Stream.of(new GatheringPlanned(gatheringId, title, venueName, location, startsAt, endsAt, speaking, infoUrl));
    }
}
