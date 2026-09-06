package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.web.BookFlightRequest;

import java.time.Instant;

public class FlightBooking {
    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;
    private final AirportZoneResolver airportZoneResolver;

    public FlightBooking(CommandExecutor commandExecutor, AirportZoneResolver airportZoneResolver,
                         LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.airportZoneResolver = airportZoneResolver;
        this.liveScheduledLegs = liveScheduledLegs;
    }

    public void bookFlight(BookFlightRequest request, Instant now) {
        BookFlightCommand command = new BookFlightHandler(airportZoneResolver).handle(request);
        // Every live scheduled leg, folded from the event stream (R1) — the command refuses a
        // journey that collides with one. Shared with the other three write paths.
        BookFlightContext context = new BookFlightContext(now, liveScheduledLegs.fold());
        commandExecutor.execute(command.flightId().id(), request, context, command);
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
