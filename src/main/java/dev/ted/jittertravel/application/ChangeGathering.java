package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeGatheringCommand;
import dev.ted.jittertravel.domain.ChangeGatheringContext;
import dev.ted.jittertravel.web.ChangeGatheringRequest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Changes an existing planned gathering in place. Routes the append through {@link CommandExecutor}
 * (never {@link dev.ted.jittertravel.infrastructure.EventStore} directly) and reads existence from
 * the {@link GatheringDetailsViewProjector} read model rather than folding the raw event stream.
 * <p>
 * commandId and today are captured at the boundary (the controller) and passed in; the service
 * performs no clock or UUID I/O of its own. commandId is a fresh id (not the gatheringId, which is
 * the aggregate id) because a gathering may be changed many times.
 */
public class ChangeGathering {
    private final CommandExecutor commandExecutor;
    private final GatheringDetailsViewProjector detailsProjector;

    public ChangeGathering(CommandExecutor commandExecutor, GatheringDetailsViewProjector detailsProjector) {
        this.commandExecutor = commandExecutor;
        this.detailsProjector = detailsProjector;
    }

    public void changeGathering(UUID commandId, ChangeGatheringRequest request, LocalDate today) {
        ChangeGatheringCommand command = new ChangeGatheringHandler().handle(request);
        boolean gatheringExists = detailsProjector.findById(command.gatheringId()).isPresent();
        ChangeGatheringContext context = new ChangeGatheringContext(gatheringExists, today);
        commandExecutor.execute(commandId, request, context, command);
    }
}
