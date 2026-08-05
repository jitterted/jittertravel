package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import dev.ted.jittertravel.domain.PlanTentativeConferenceContext;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;

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
    public void planConference(PlanTentativeConferenceRequest request, Instant now) {
        PlanTentativeConferenceCommand command = new PlanTentativeConferenceHandler(zoneResolver).handle(request);
        PlanTentativeConferenceContext context = new PlanTentativeConferenceContext(now);
        commandExecutor.execute(command.conferenceId().id(), request, context, command);
    }

    public boolean isReadOnly() {
        return commandExecutor.isReadOnly();
    }
}
