package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.Clock;
import java.time.LocalDateTime;

public class HotelBooking {
    private final CommandExecutor commandExecutor;
    private final Clock clock;

    public HotelBooking(CommandExecutor commandExecutor, Clock clock) {
        this.commandExecutor = commandExecutor;
        this.clock = clock;
    }

    public void bookHotel(BookHotelRequest request) {
        BookHotelCommand command = new BookHotelHandler().handle(request);
        BookHotelContext context = new BookHotelContext(LocalDateTime.now(clock));
        commandExecutor.execute(command.hotelBookingId().id(), request, context, command);
    }
}
