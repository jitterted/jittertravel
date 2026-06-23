package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.TrainStationAddress;

import java.time.ZoneId;

/**
 * Resolves the zone for a single train-trip endpoint at the boundary, with the per-endpoint
 * contract shared by {@link BookTrainHandler} and {@link ChangeTrainHandler}: an explicitly chosen
 * {@link CommonZone} wins; otherwise the station's city/country must resolve via
 * {@link LocationZoneResolver} or a {@link ZoneResolutionException} rejects the command (the form
 * then re-prompts for a {@code CommonZone}). Departure and arrival each go through this
 * independently, so a trip may span two zones.
 */
public class StationZone {

    private final LocationZoneResolver zoneResolver;

    public StationZone(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    public ZoneId resolve(String explicitZone, TrainStationAddress station) {
        CommonZone picked = CommonZone.fromParam(explicitZone);
        if (picked != null) {
            return picked.zoneId();
        }
        return zoneResolver.resolve(station.city(), station.country());
    }
}
