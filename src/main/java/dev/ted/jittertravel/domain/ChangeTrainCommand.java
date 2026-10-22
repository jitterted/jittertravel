package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Changes an existing booked train in place, keeping the same {@link TrainTripId}. Validation
 * rules (same as booking, plus existence), in two places:
 * <ul>
 *   <li>As the command is built — each station must name a building and a city that is really a
 *       city ({@link InvalidTrainLocations}, naming both ends when both are wrong; see
 *       {@link EnteredLocation}), and arrival must be after departure ({@link InvalidDateRange}).
 *       These need nothing beyond the form, so they are data entry and never reach the command
 *       log (Ted, 2026-10-22; {@code docs/archived/ValidateBeforeTheWalPlan.md}).</li>
 *   <li>In {@link #execute}, recorded — the trip must already exist ({@link TrainNotFound}), the
 *       new departure must be in the future ({@link DepartureNotInFuture}), and the new window
 *       must not collide with another booked leg ({@link OverlappingLegRefused}).</li>
 * </ul>
 * A data-entry mistake is therefore reported even for a trip that does not exist. That only
 * matters for a hand-edited URL, since the edit form is reached from a row that exists, and Ted
 * accepted it for the sake of one rule for every command.
 * Emits a single {@link TrainChanged} event carrying the full new snapshot.
 */
public record ChangeTrainCommand(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        ZonedTimestamp departureDateTime,
        TrainStationAddress arrivalStation,
        ZonedTimestamp arrivalDateTime,
        String serviceId
) implements DomainCommand<ChangeTrainContext> {

    public ChangeTrainCommand {
        new TrainStations(departureStation, arrivalStation).check();
        Objects.requireNonNull(departureDateTime, "departureDateTime");
        Objects.requireNonNull(arrivalDateTime, "arrivalDateTime");
        // Ordering is an instant comparison (zone-independent).
        if (!arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
    }

    @Override
    public Stream<TrainChanged> execute(ChangeTrainContext context) {
        if (!context.tripExists()) {
            throw new TrainNotFound("No train exists with that tripId");
        }
        if (!departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        // Excludes this very trip, or a change would always collide with the leg it is
        // replacing and no booked trip could ever be corrected.
        context.scheduledLegs()
                .overlapping(new ScheduledLegId.Train(tripId), departureDateTime, arrivalDateTime)
                .ifPresent(blocking -> { throw new OverlappingLegRefused(blocking); });
        return Stream.of(new TrainChanged(tripId, departureStation, departureDateTime,
                arrivalStation, arrivalDateTime, serviceId));
    }
}
