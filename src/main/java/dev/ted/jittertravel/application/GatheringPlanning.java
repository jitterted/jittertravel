package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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
        commandExecutor.appendEvents(
                UUID.randomUUID(),
                Map.of("type", "clearDifferentCityConflict",
                       "gatheringId", gatheringId.id(),
                       "conferenceId", conferenceId.id()),
                Stream.of(new DifferentCityConflictCleared(gatheringId, conferenceId, reason))
        );
    }
}
