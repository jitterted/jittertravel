package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PlanGroundTransferCommand;
import dev.ted.jittertravel.domain.PlanGroundTransferContext;
import dev.ted.jittertravel.web.PlanGroundTransferRequest;

/**
 * Application service for planning a ground transfer. The command goes through
 * {@link CommandExecutor} (never {@code EventStore} directly).
 * <p>
 * Unlike every other planning service here, it takes no {@code now}: a ground transfer has no
 * future-date rule (D6), so its decision context is empty and there is nothing for the boundary to
 * pass in. Adding an unused {@code now} "for later" would be a speculative parameter.
 */
public class GroundTransferPlanning {
    private final CommandExecutor commandExecutor;
    private final GroundTransferEndpointResolver endpoints;

    public GroundTransferPlanning(CommandExecutor commandExecutor,
                                  GroundTransferEndpointResolver endpoints) {
        this.commandExecutor = commandExecutor;
        this.endpoints = endpoints;
    }

    public void planGroundTransfer(PlanGroundTransferRequest request) {
        PlanGroundTransferCommand command = new PlanGroundTransferHandler(endpoints).handle(request);
        commandExecutor.execute(command.groundTransferId().id(), request,
                new PlanGroundTransferContext(), command);
    }
}
