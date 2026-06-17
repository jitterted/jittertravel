package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.LocalDateTime;

public class HotelBooking {
    private final CommandExecutor commandExecutor;

    public HotelBooking(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    // now is captured at the boundary (controller) and passed in; the service reads no clock.
    public void bookHotel(BookHotelRequest request, LocalDateTime now) {
        BookHotelCommand command = new BookHotelHandler().handle(request);
        BookHotelContext context = new BookHotelContext(now);
        commandExecutor.execute(command.hotelBookingId().id(), request, context, command);
    }
}
