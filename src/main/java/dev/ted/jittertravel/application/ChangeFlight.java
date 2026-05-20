package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeFlightCommand;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.ChangeFlightRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public class ChangeFlight {
    private final EventStore eventStore;
    private final PostgresPersister persister;

    public ChangeFlight(EventStore eventStore, PostgresPersister persister) {
        this.eventStore = eventStore;
        this.persister = persister;
    }

    public void changeFlight(ChangeFlightRequest request) {
        if (eventStore.isReadOnly()) {
            throw new ReadOnlyModeException("Attempting to execute request while in read-only mode:" + request);
        }

        // Each change attempt is its own command. We use a fresh id (not the
        // flightId, which is the aggregate id, not the command id).
        UUID commandId = UUID.randomUUID();
        persister.saveCommand(commandId, request);

        FlightId flightId = FlightId.of(UUID.fromString(request.getFlightId()));
        boolean flightExists = flightExists(flightId);

        ChangeFlightCommand domainCommand = new ChangeFlightCommand();
        var events = domainCommand.execute(request, flightExists, LocalDateTime.now());

        eventStore.append(events, commandId);
    }

    /**
     * Fold from the authoritative event stream — inline filter today.
     * Will be replaced by an indexed tag query (see TaggedEventStoreQueryingDesign.md)
     * when a second caller demands it.
     */
    private boolean flightExists(FlightId flightId) {
        return eventStore.findAll()
                .map(StoredEvent::payload)
                .anyMatch(payload -> switch (payload) {
                    case FlightBooked b -> b.flightId().equals(flightId);
                    case FlightChanged c -> c.flightId().equals(flightId);
                    default -> false;
                });
    }

    public boolean isReadOnly() {
        return eventStore.isReadOnly();
    }
}
