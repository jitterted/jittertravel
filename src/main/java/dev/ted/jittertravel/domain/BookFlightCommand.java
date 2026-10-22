package dev.ted.jittertravel.domain;

import java.util.Objects;
import java.util.stream.Stream;

public record BookFlightCommand(
        FlightId flightId,
        String airline,
        String flightNumber,
        AirportCode departureAirport,
        ZonedTimestamp departureDateTime,
        AirportCode arrivalAirport,
        ZonedTimestamp arrivalDateTime
) implements DomainCommand<BookFlightContext> {

    /**
     * What the form alone can get wrong is refused here, before the command exists, so it never
     * reaches the command log: a command that was never valid is invalid data, not a domain
     * refusal (Ted, 2026-10-22; {@code docs/ValidateBeforeTheWalPlan.md}). Only what needs
     * state — the departure being in the future, the overlap with a booked leg — stays in
     * {@link #execute}, where the refusal is recorded.
     */
    public BookFlightCommand {
        Objects.requireNonNull(departureDateTime, "departureDateTime");
        Objects.requireNonNull(arrivalDateTime, "arrivalDateTime");
        if (!arrivalDateTime.utc().isAfter(departureDateTime.utc())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }
    }

    @Override
    public Stream<FlightBooked> execute(BookFlightContext context) {
        if (!departureDateTime.utc().isAfter(context.now())) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        // Last of the rules, and after the dates on purpose: an overlap is only a meaningful
        // question once the window itself is valid, and testing it against a past or inverted
        // range would report a collision that says nothing about what is wrong.
        context.scheduledLegs()
                .overlapping(null, departureDateTime, arrivalDateTime)
                .ifPresent(blocking -> { throw new OverlappingLegRefused(blocking); });
        return Stream.of(new FlightBooked(flightId, airline, flightNumber,
                departureAirport, departureDateTime, arrivalAirport, arrivalDateTime));
    }
}
