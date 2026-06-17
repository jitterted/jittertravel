package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeTrainCommand;
import dev.ted.jittertravel.domain.ChangeTrainContext;
import dev.ted.jittertravel.web.ChangeTrainRequest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Changes an existing booked train in place. Routes the append through {@link CommandExecutor}
 * (never {@link dev.ted.jittertravel.infrastructure.EventStore} directly) and reads existence from
 * the {@link TrainDetailsViewProjector} read model rather than folding the raw event stream.
 * <p>
 * commandId and now are captured at the boundary (the controller) and passed in; the service
 * performs no clock or UUID I/O of its own. commandId is a fresh id (not the tripId, which is the
 * aggregate id) because a trip may be changed many times.
 */
public class ChangeTrain {
    private final CommandExecutor commandExecutor;
    private final TrainDetailsViewProjector detailsProjector;

    public ChangeTrain(CommandExecutor commandExecutor, TrainDetailsViewProjector detailsProjector) {
        this.commandExecutor = commandExecutor;
        this.detailsProjector = detailsProjector;
    }

    public void changeTrain(UUID commandId, ChangeTrainRequest request, LocalDateTime now) {
        ChangeTrainCommand command = new ChangeTrainHandler().handle(request);
        boolean tripExists = detailsProjector.findById(command.tripId()).isPresent();
        ChangeTrainContext context = new ChangeTrainContext(tripExists, now);
        commandExecutor.execute(commandId, request, context, command);
    }
}