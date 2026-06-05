package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringPlanningContext;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.Clock;
import java.time.LocalDate;

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
}
