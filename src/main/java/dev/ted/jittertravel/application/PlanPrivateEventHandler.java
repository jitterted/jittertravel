package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.PlanPrivateEventCommand;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanPrivateEventRequest;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Maps the private-event form to its domain command, resolving the venue zone through the shared
 * {@link VenueZone} (explicit {@code CommonZone} wins, else the address must resolve). Single day,
 * one venue, so start and end share that one zone. Mirrors {@code PlanGatheringHandler}.
 */
public class PlanPrivateEventHandler {

    private final VenueZone venueZone;

    public PlanPrivateEventHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    public PlanPrivateEventCommand handle(PlanPrivateEventRequest request) {
        Address location = request.getLocation();
        ZoneId zone = venueZone.resolve(request.getZone(), location);
        return new PlanPrivateEventCommand(
                PrivateEventId.of(UUID.fromString(request.getPrivateEventId())),
                request.getTitle(),
                request.getVenueName(),
                location,
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getStartTime()), zone),
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getEndTime()), zone)
        );
    }
}
