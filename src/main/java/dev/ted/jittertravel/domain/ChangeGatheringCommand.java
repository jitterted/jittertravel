package dev.ted.jittertravel.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

/**
 * Changes an existing planned gathering in place, keeping the same {@link GatheringId}. Validation
 * rules (same as planning, plus existence):
 * <ul>
 *   <li>The gathering must already exist ({@link GatheringNotFound} otherwise).</li>
 *   <li>The new date must be in the future ({@link GatheringDateNotInFuture}).</li>
 *   <li>The end time must be after the start time ({@link InvalidGatheringTimeRange}).</li>
 * </ul>
 * Emits a single {@link GatheringChanged} event carrying the full new snapshot.
 */
public record ChangeGatheringCommand(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl
) implements DomainCommand<ChangeGatheringContext> {

    @Override
    public Stream<GatheringChanged> execute(ChangeGatheringContext context) {
        if (!context.gatheringExists()) {
            throw new GatheringNotFound("No gathering exists with that gatheringId");
        }
        if (date == null || !date.isAfter(context.today())) {
            throw new GatheringDateNotInFuture("Gathering date must be in the future");
        }
        if (endTime == null || !endTime.isAfter(startTime)) {
            throw new InvalidGatheringTimeRange("End time must be after start time");
        }
        return Stream.of(new GatheringChanged(gatheringId, title, venueName, location,
                date, startTime, endTime, speaking, infoUrl));
    }
}
