package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.Instant;

public class HotelBooking {
    private final CommandExecutor commandExecutor;
    private final LocationZoneResolver zoneResolver;

    public HotelBooking(CommandExecutor commandExecutor, LocationZoneResolver zoneResolver) {
        this.commandExecutor = commandExecutor;
        this.zoneResolver = zoneResolver;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    public void bookHotel(BookHotelRequest request, Instant now) {
        BookHotelCommand command = new HotelHandler(zoneResolver).bookHotel(request);
        BookHotelContext context = new BookHotelContext(now);
        commandExecutor.execute(command.hotelBookingId().id(), request, context, command);
    }
}
