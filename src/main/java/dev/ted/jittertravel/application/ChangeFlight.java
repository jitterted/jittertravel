package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.ChangeFlightCommand;
import dev.ted.jittertravel.domain.ChangeFlightContext;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.web.ChangeFlightRequest;

import java.time.Instant;
import java.util.UUID;

public class ChangeFlight {
    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;
    private final FlightDetailsViewProjector detailsProjector;
    private final AirportZoneResolver airportZoneResolver;

    public ChangeFlight(CommandExecutor commandExecutor,
                        FlightDetailsViewProjector detailsProjector,
                        AirportZoneResolver airportZoneResolver,
                        LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.detailsProjector = detailsProjector;
        this.airportZoneResolver = airportZoneResolver;
        this.liveScheduledLegs = liveScheduledLegs;
    }

    public void changeFlight(UUID commandId, ChangeFlightRequest request, Instant now) {
        ChangeFlightCommand command = new ChangeFlightHandler(airportZoneResolver).handle(request);
        FlightId flightId = command.flightId();
        boolean flightExists = detailsProjector.findById(flightId).isPresent();
        // Every live scheduled leg, folded from the event stream (R1) — the command refuses a
        // journey that collides with one. Shared with the other three write paths.
        ChangeFlightContext context = new ChangeFlightContext(flightExists, now,
                liveScheduledLegs.fold());
        commandExecutor.execute(commandId, request, context, command);
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
