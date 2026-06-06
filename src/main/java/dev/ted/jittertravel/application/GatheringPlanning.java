package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

public class GatheringPlanning {
    private final CommandExecutor commandExecutor;
    private final EventStore eventStore;
    private final Clock clock;

    public GatheringPlanning(CommandExecutor commandExecutor, EventStore eventStore, Clock clock) {
        this.commandExecutor = commandExecutor;
        this.eventStore = eventStore;
        this.clock = clock;
    }

    public void planGathering(PlanGatheringRequest request) {
        PlanGatheringCommand command = new PlanGatheringHandler().handle(request);
        GatheringPlanningContext context = new GatheringPlanningContext(LocalDate.now(clock));
        commandExecutor.execute(command.gatheringId().id(), request, context, command);
    }

    public void clearConflict(GatheringId gatheringId, ConferenceId conferenceId, String reason) {
        eventStore.append(
                Stream.of(new DifferentCityConflictCleared(gatheringId, conferenceId, reason)),
                UUID.randomUUID());
    }
}
