package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Changes a booked flight in place. An arrival before the departure needs nothing beyond the form
 * to judge, so it is refused as the command is built and never reaches the command log (Ted,
 * 2026-10-22; {@code docs/archived/ValidateBeforeTheWalPlan.md}); existence, the departure being in the
 * future and the overlap with another leg need state, stay in {@link #execute}, and are recorded.
 */
public record ChangeFlightCommand(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime,
        String reason
) implements DomainCommand<ChangeFlightContext> {

    public ChangeFlightCommand {
        if (reason == null) reason = "";
        Objects.requireNonNull(departureDateTime, "departureDateTime");
        Objects.requireNonNull(arrivalDateTime, "arrivalDateTime");
        if (!arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
    }

    @Override
    public Stream<FlightChanged> execute(ChangeFlightContext context) {
        if (!context.flightExists()) {
            throw new FlightNotFound("No flight exists with id " + flightId.id());
        }
        if (!departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        // Excludes this very flight — see ChangeTrainCommand.
        context.scheduledLegs()
                .overlapping(new ScheduledLegId.Flight(flightId), departureDateTime, arrivalDateTime)
                .ifPresent(blocking -> { throw new OverlappingLegRefused(blocking); });
        return Stream.of(new FlightChanged(flightId, airline, flightNumber,
                departureAirport, departureDateTime, arrivalAirport, arrivalDateTime, reason));
    }
}
