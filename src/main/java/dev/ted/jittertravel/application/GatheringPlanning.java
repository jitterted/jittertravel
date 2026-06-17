package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanningContext;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.web.ClearDifferentCityConflict;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.LocalDate;
import java.util.UUID;

public class GatheringPlanning {
    private final CommandExecutor commandExecutor;

    public GatheringPlanning(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    // today and commandId are captured at the boundary (controller) and passed in;
    // the service generates no clock or UUID I/O of its own.
    public void planGathering(PlanGatheringRequest request, LocalDate today) {
        PlanGatheringCommand command = new PlanGatheringHandler().handle(request);
        GatheringPlanningContext context = new GatheringPlanningContext(today);
        commandExecutor.execute(command.gatheringId().id(), request, context, command);
    }

    public void clearConflict(GatheringId gatheringId, ConferenceId conferenceId, String reason, UUID commandId) {
        ClearDifferentCityConflict command = new ClearDifferentCityConflict(
                gatheringId.id(), conferenceId.id(), reason);
        commandExecutor.appendEvents(commandId, command, command.events());
    }
}
