package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanGatheringRequest;

import java.time.ZoneId;
import java.util.UUID;

public class PlanGatheringHandler {

    private final VenueZone venueZone;

    public PlanGatheringHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    /**
     * The form still collects a date plus two times (a gathering is an evening, not a span of
     * days); the boundary is where those become instants, by reading them as wall-clock in the
     * venue's zone.
     */
    public PlanGatheringCommand handle(PlanGatheringRequest request) {
        Address location = request.getLocation();
        ZoneId zone = venueZone.resolve(request.getZone(), location);
        return new PlanGatheringCommand(
                GatheringId.of(UUID.fromString(request.getGatheringId())),
                request.getTitle(),
                request.getVenueName(),
                location,
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getStartTime()), zone),
                ZonedTimestamp.fromLocal(request.getDate().atTime(request.getEndTime()), zone),
                request.isSpeaking(),
                request.getInfoUrl()
        );
    }
}
