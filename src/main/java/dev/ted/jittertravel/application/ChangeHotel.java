package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.domain.ChangeHotelContext;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.web.ChangeHotelRequest;

import java.time.Instant;
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
    private final LocationZoneResolver zoneResolver;

    public ChangeHotel(CommandExecutor commandExecutor, HotelDetailsViewProjector detailsProjector,
                       LocationZoneResolver zoneResolver) {
        this.commandExecutor = commandExecutor;
        this.detailsProjector = detailsProjector;
        this.zoneResolver = zoneResolver;
    }

    /**
     * {@code hotelBookingId} arrives from the path rather than on the request: which booking is
     * being changed is not something the form submits, so there is nothing on the page for a
     * crafted POST to re-target.
     */
    public void changeHotel(UUID commandId, String hotelBookingId, ChangeHotelRequest request,
                            Instant now) {
        ChangeHotelCommand command =
                new HotelHandler(zoneResolver).changeHotel(hotelBookingId, request);
        boolean bookingExists = detailsProjector.findById(command.hotelBookingId()).isPresent();
        ChangeHotelContext context = new ChangeHotelContext(bookingExists, now);
        commandExecutor.execute(commandId, request, context, command);
    }
}