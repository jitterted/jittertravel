package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.domain.ChangeHotelContext;
import dev.ted.jittertravel.web.ChangeHotelRequest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Changes an existing booked hotel in place. Routes the append through {@link CommandExecutor}
 * (never {@link dev.ted.jittertravel.infrastructure.EventStore} directly) and reads existence from
 * the {@link HotelDetailsViewProjector} read model rather than folding the raw event stream.
 * <p>
 * commandId and now are captured at the boundary (the controller) and passed in; the service
 * performs no clock or UUID I/O of its own. commandId is a fresh id (not the hotelBookingId, which
 * is the aggregate id) because a booking may be changed many times.
 */
public class ChangeHotel {
    private final CommandExecutor commandExecutor;
    private final HotelDetailsViewProjector detailsProjector;

    public ChangeHotel(CommandExecutor commandExecutor, HotelDetailsViewProjector detailsProjector) {
        this.commandExecutor = commandExecutor;
        this.detailsProjector = detailsProjector;
    }

    public void changeHotel(UUID commandId, ChangeHotelRequest request, LocalDateTime now) {
        ChangeHotelCommand command = new ChangeHotelHandler().handle(request);
        boolean bookingExists = detailsProjector.findById(command.hotelBookingId()).isPresent();
        ChangeHotelContext context = new ChangeHotelContext(bookingExists, now);
        commandExecutor.execute(commandId, request, context, command);
    }
}