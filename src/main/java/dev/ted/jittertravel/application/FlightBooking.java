package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.BookFlightRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class FlightBooking {
    private final EventStore eventStore;
    private final PostgresPersister persister;

    public FlightBooking(EventStore eventStore, PostgresPersister persister) {
        this.eventStore = eventStore;
        this.persister = persister;
    }

    public void bookFlight(BookFlightRequest request) {
        if (eventStore.isReadOnly()) {
            throw new ReadOnlyModeException("Attempting to execute request while in read-only mode:" + request);
        }

        UUID commandId = UUID.fromString(request.getFlightId());
        persister.saveCommand(commandId, request);

        BookFlightCommand domainCommand = new BookFlightCommand();
        var events = domainCommand.execute(request, LocalDateTime.now());

        eventStore.append(events, commandId);
    }

    public boolean isReadOnly() {
        return eventStore.isReadOnly();
    }
}
