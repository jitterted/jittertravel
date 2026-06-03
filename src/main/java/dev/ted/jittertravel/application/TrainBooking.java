package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.BookTrainContext;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.time.Clock;
import java.time.LocalDateTime;

public class TrainBooking {
    private final CommandExecutor commandExecutor;
    private final Clock clock;

    public TrainBooking(CommandExecutor commandExecutor, Clock clock) {
        this.commandExecutor = commandExecutor;
        this.clock = clock;
    }

    public void bookTrain(BookTrainRequest request) {
        BookTrainCommand command = new BookTrainHandler().handle(request);
        BookTrainContext context = new BookTrainContext(LocalDateTime.now(clock));
        commandExecutor.execute(command.tripId().id(), request, context, command);
    }
}
