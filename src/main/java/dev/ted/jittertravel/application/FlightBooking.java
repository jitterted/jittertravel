package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.web.BookFlightRequest;

import java.time.Instant;

public class FlightBooking {
    private final CommandExecutor commandExecutor;
    private final AirportZoneResolver airportZoneResolver;

    public FlightBooking(CommandExecutor commandExecutor, AirportZoneResolver airportZoneResolver) {
        this.commandExecutor = commandExecutor;
        this.airportZoneResolver = airportZoneResolver;
    }

    public void bookFlight(BookFlightRequest request, Instant now) {
        BookFlightCommand command = new BookFlightHandler(airportZoneResolver).handle(request);
        BookFlightContext context = new BookFlightContext(now);
        commandExecutor.execute(command.flightId().id(), request, context, command);
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
