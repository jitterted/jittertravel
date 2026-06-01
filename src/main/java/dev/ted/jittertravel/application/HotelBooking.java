package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class HotelBooking {
    private final EventStore eventStore;
    private final PostgresPersister persister;

    public HotelBooking(EventStore eventStore, PostgresPersister persister) {
        this.eventStore = eventStore;
        this.persister = persister;
    }

    public void bookHotel(BookHotelRequest request) {
        UUID commandId = UUID.fromString(request.getHotelBookingId());
        persister.saveCommand(commandId, request);
        var events = new BookHotelCommand().execute(request, LocalDateTime.now());
        eventStore.append(events, commandId);
    }
}
