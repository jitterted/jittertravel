package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.BookTrainContext;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.web.BookTrainRequest;

import java.time.Instant;

public class TrainBooking {
    private final CommandExecutor commandExecutor;
    private final LiveScheduledLegs liveScheduledLegs;
    private final LocationZoneResolver zoneResolver;

    public TrainBooking(CommandExecutor commandExecutor, LocationZoneResolver zoneResolver,
                        LiveScheduledLegs liveScheduledLegs) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
        this.liveScheduledLegs = liveScheduledLegs;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    public void bookTrain(BookTrainRequest request, Instant now) {
        BookTrainCommand command = new BookTrainHandler(zoneResolver).handle(request);
        // Every live scheduled leg, folded from the event stream (R1) — the command refuses a
        // journey that collides with one. Shared with the other three write paths.
        BookTrainContext context = new BookTrainContext(now, liveScheduledLegs.fold());
        commandExecutor.execute(command.tripId().id(), request, context, command);
    }
}
