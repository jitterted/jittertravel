package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanningContext;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.web.ClearDifferentCityConflict;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public class GatheringPlanning {
    private final CommandExecutor commandExecutor;
    private final Clock clock;

    public GatheringPlanning(CommandExecutor commandExecutor, Clock clock) {
        this.commandExecutor = commandExecutor;
        this.clock = clock;
    }

    public void planGathering(PlanGatheringRequest request) {
        PlanGatheringCommand command = new PlanGatheringHandler().handle(request);
        GatheringPlanningContext context = new GatheringPlanningContext(LocalDate.now(clock));
        commandExecutor.execute(command.gatheringId().id(), request, context, command);
    }

    public void clearConflict(GatheringId gatheringId, ConferenceId conferenceId, String reason) {
        ClearDifferentCityConflict command = new ClearDifferentCityConflict(
                gatheringId.id(), conferenceId.id(), reason);
        commandExecutor.appendEvents(UUID.randomUUID(), command, command.events());
    }
}
