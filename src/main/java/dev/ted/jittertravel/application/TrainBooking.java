package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.BookTrainContext;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.time.LocalDateTime;

public class TrainBooking {
    private final CommandExecutor commandExecutor;

    public TrainBooking(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    public void bookTrain(BookTrainRequest request, LocalDateTime now) {
        BookTrainCommand command = new BookTrainHandler().handle(request);
        BookTrainContext context = new BookTrainContext(now);
        commandExecutor.execute(command.tripId().id(), request, context, command);
    }
}
