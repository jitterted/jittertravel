package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.ZoneResolutionException;

import java.time.ZoneId;

/**
 * Resolves the zone of a venue at the boundary, with the contract shared by
 * {@link PlanGatheringHandler} and {@link ChangeGatheringHandler}: an explicitly chosen
 * {@link CommonZone} wins; otherwise the venue's address must resolve via
 * {@link LocationZoneResolver} or a {@link ZoneResolutionException} rejects the command (the form
 * then re-prompts for a {@code CommonZone}).
 * <p>
 * A gathering happens at one place, so its start and end share this single zone — unlike a train or
 * flight, whose endpoints resolve independently ({@link StationZone}, {@link FlightEndpointZone}).
 */
public class VenueZone {

    private final LocationZoneResolver zoneResolver;

    public VenueZone(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    public ZoneId resolve(String explicitZone, Address location) {
        CommonZone picked = CommonZone.fromParam(explicitZone);
        if (picked != null) {
            return picked.zoneId();
        }
        return zoneResolver.resolve(location);
    }
}
