package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ChangeGatheringCommand;
import dev.ted.jittertravel.domain.GatheringId;
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
    public ChangeGatheringCommand handle(ChangeGatheringRequest request) {
        Address location = request.getLocation();
        ZoneId zone = venueZone.resolve(request.getZone(), location);
        return new ChangeGatheringCommand(
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
