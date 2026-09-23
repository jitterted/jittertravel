package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Changes an existing planned gathering in place, keeping the same {@link GatheringId}. Validation
 * rules:
 * <ul>
 *   <li>The gathering must already exist ({@link GatheringNotFound} otherwise).</li>
 *   <li>The new date must be today or later, judged in the gathering's own zone
 *       ({@link GatheringDateNotInFuture}). Unlike planning, today is allowed: a gathering
 *       happening today can still have its details corrected.</li>
 *   <li>The end must be after the start ({@link InvalidGatheringTimeRange}).</li>
 * </ul>
 * Emits a single {@link GatheringChanged} event carrying the full new snapshot.
 */
public record ChangeGatheringCommand(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) implements DomainCommand<ChangeGatheringContext> {

    @Override
    public Stream<GatheringChanged> execute(ChangeGatheringContext context) {
        if (!context.gatheringExists()) {
            throw new GatheringNotFound("No gathering exists with that gatheringId");
        }
        if (startsAt == null || !startsAt.isOnOrAfterDayOf(context.now())) {
            throw new GatheringDateNotInFuture("Gathering date must be today or later");
        }
        if (endsAt == null || !endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
        return Stream.of(new GatheringChanged(gatheringId, title, venueName, location,
                startsAt, endsAt, speaking, infoUrl));
    }
}
