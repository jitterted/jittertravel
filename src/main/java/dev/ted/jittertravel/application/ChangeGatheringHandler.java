package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ChangeGatheringCommand;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.ChangeGatheringRequest;

import java.time.ZoneId;
import java.util.UUID;

public class ChangeGatheringHandler {

    private final VenueZone venueZone;

    public ChangeGatheringHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    /**
     * Re-resolves the zone from the submitted form every time, so moving a gathering to a venue in
     * another zone re-derives its instants instead of keeping the old zone. See
     * {@link PlanGatheringHandler} for the date+times to instants conversion.
     */
    public ChangeGatheringCommand handle(String gatheringId, ChangeGatheringRequest request) {
        Address location = request.location();
        ZoneId zone = venueZone.resolve(request.zone(), location);
        return new ChangeGatheringCommand(
                GatheringId.of(UUID.fromString(gatheringId)),
                request.title(),
                request.venueName(),
                location,
                ZonedTimestamp.fromLocal(request.date().atTime(request.startTime()), zone),
                ZonedTimestamp.fromLocal(request.date().atTime(request.endTime()), zone),
                request.speaking(),
                request.infoUrl()
        );
    }
}
