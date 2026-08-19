package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PlanConferenceCommand;
import dev.ted.jittertravel.domain.PlanConferenceContext;
import dev.ted.jittertravel.web.PlanConferenceRequest;

import java.time.Instant;

public class ConferencePlanning {
    private final CommandExecutor commandExecutor;
    private final LocationZoneResolver zoneResolver;

    public ConferencePlanning(CommandExecutor commandExecutor, LocationZoneResolver zoneResolver) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    // The read-only guard lives in CommandExecutor, which refuses to write a command row at all.
    public void planConference(PlanConferenceRequest request, Instant now) {
        PlanConferenceCommand command = new PlanConferenceHandler(zoneResolver).handle(request);
        PlanConferenceContext context = new PlanConferenceContext(now);
        commandExecutor.execute(command.conferenceId().id(), request, context, command);
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
