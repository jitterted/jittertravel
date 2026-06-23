package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.BookTrainContext;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.time.Instant;

public class TrainBooking {
    private final CommandExecutor commandExecutor;
    private final LocationZoneResolver zoneResolver;

    public TrainBooking(CommandExecutor commandExecutor, LocationZoneResolver zoneResolver) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    public void bookTrain(BookTrainRequest request, Instant now) {
        BookTrainCommand command = new BookTrainHandler(zoneResolver).handle(request);
        BookTrainContext context = new BookTrainContext(now);
        commandExecutor.execute(command.tripId().id(), request, context, command);
    }
}
