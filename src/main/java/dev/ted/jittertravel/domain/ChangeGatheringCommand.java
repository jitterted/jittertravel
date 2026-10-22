package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Changes an existing planned gathering in place, keeping the same {@link GatheringId}. Validation
 * rules, in two places:
 * <ul>
 *   <li>As the command is built — the country is a real code, and a US, Canadian or Australian
 *       venue names its state ({@link EnteredCountry}); the end is after the start
 *       ({@link InvalidGatheringTimeRange}). These need nothing beyond the form, so they are data
 *       entry and never reach the command log (Ted, 2026-10-22;
 *       {@code docs/archived/ValidateBeforeTheWalPlan.md}).</li>
 *   <li>In {@link #execute}, recorded — the gathering must already exist
 *       ({@link GatheringNotFound}), and the new date must be today or later, judged in the
 *       gathering's own zone ({@link GatheringDateNotInFuture}). Unlike planning, today is
 *       allowed: a gathering happening today can still have its details corrected.</li>
 * </ul>
 * A data-entry mistake is therefore reported even for a gathering that does not exist. That only
 * matters for a hand-edited URL, since the edit form is reached from a row that exists, and Ted
 * accepted it for the sake of one rule for every command.
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

    public ChangeGatheringCommand {
        EnteredCountry.of(location).check(LocationRole.VENUE);
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.utc().isAfter(startsAt.utc())) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
    }

    @Override
    public Stream<GatheringChanged> execute(ChangeGatheringContext context) {
        if (!context.gatheringExists()) {
            throw new GatheringNotFound("No gathering exists with that gatheringId");
        }
        if (!startsAt.isOnOrAfterDayOf(context.now())) {
            throw new GatheringDateNotInFuture("Gathering date must be today or later");
        }
        return Stream.of(new GatheringChanged(gatheringId, title, venueName, location,
                startsAt, endsAt, speaking, infoUrl));
    }
}
