package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Corrects the place the schedule reasons about a private event in, leaving everything about the
 * event itself alone.
 * <p>
 * Two refusals, reported in this order:
 * <ol>
 *   <li>the private event exists — a stale link, or a second tab that cancelled it, must not write
 *       an override for an evening no read model holds;</li>
 *   <li>a location was typed. Blank is <em>refused</em> rather than read as "clear the override":
 *       {@code ScheduleGapProjector} holds a resolved occupancy carrying one city string, not the
 *       original {@link Address}, so there is nothing to revert to without re-reading the plan
 *       event. Undo is typing the original city back in, which costs nothing. See
 *       {@code docs/PrivateEventMatchingLocationPlan.md} D4.</li>
 * </ol>
 * <p>
 * The check is here and not in {@link PrivateEventMatchingLocationChanged}'s compact constructor:
 * normalize in the record, reject in the command (CLAUDE.md, "A city that is really a station is
 * rejected on the write path").
 * <p>
 * There is no time gate, deliberately, and no new event is emitted when the value is unchanged —
 * re-submitting the same location writes a second event, which is an honest record of Ted having
 * said it twice and costs one row.
 */
public record ChangePrivateEventMatchingLocationCommand(
        PrivateEventId privateEventId,
        String locationForMatching
) implements DomainCommand<ChangePrivateEventMatchingLocationContext> {

    @Override
    public Stream<PrivateEventMatchingLocationChanged> execute(
            ChangePrivateEventMatchingLocationContext context) {
        if (!context.privateEventExists()) {
            throw new PrivateEventNotFound(
                    "No private event found to re-match: " + privateEventId);
        }
        if (locationForMatching == null || locationForMatching.isBlank()) {
            throw new InvalidMatchingLocation("Location is required");
        }
        return Stream.of(
                new PrivateEventMatchingLocationChanged(privateEventId, locationForMatching));
    }
}
