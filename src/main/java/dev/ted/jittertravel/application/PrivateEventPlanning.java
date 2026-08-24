package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.PlanPrivateEventCommand;
import dev.ted.jittertravel.domain.PlanPrivateEventContext;
import dev.ted.jittertravel.web.PlanPrivateEventRequest;

import java.time.Instant;

/**
 * Application service for planning a private social event. Like {@code GatheringPlanning}:
 * {@code now} is captured at the boundary and passed in; the venue zone is resolved inward; the
 * command goes through {@link CommandExecutor} (never {@code EventStore} directly).
 */
public class PrivateEventPlanning {
    private final CommandExecutor commandExecutor;
    private final LocationZoneResolver zoneResolver;

    public PrivateEventPlanning(CommandExecutor commandExecutor, LocationZoneResolver zoneResolver) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
    }

    public void planPrivateEvent(PlanPrivateEventRequest request, Instant now) {
        PlanPrivateEventCommand command = new PlanPrivateEventHandler(zoneResolver).handle(request);
        PlanPrivateEventContext context = new PlanPrivateEventContext(now);
        commandExecutor.execute(command.privateEventId().id(), request, context, command);
    }
}
