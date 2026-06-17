package dev.ted.jittertravel.domain;

import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Changes an existing booked train in place, keeping the same {@link TrainTripId}. Validation
 * rules (same as booking, plus existence):
 * <ul>
 *   <li>The trip must already exist ({@link TrainNotFound} otherwise).</li>
 *   <li>The new departure date/time must be in the future ({@link DepartureNotInFuture}).</li>
 *   <li>Arrival must be after departure ({@link InvalidDateRange}).</li>
 * </ul>
 * Emits a single {@link TrainChanged} event carrying the full new snapshot.
 */
public record ChangeTrainCommand(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        LocalDateTime departureDateTime,
        TrainStationAddress arrivalStation,
        LocalDateTime arrivalDateTime,
        String serviceId
) implements DomainCommand<ChangeTrainContext> {

    @Override
    public Stream<TrainChanged> execute(ChangeTrainContext context) {
        if (!context.tripExists()) {
            throw new TrainNotFound("No train exists with that tripId");
        }
        if (departureDateTime == null || !departureDateTime.isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (arrivalDateTime == null || !arrivalDateTime.isAfter(departureDateTime)) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
        return Stream.of(new TrainChanged(tripId, departureStation, departureDateTime,
                arrivalStation, arrivalDateTime, serviceId));
    }
}