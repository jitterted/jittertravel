package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
import dev.ted.jittertravel.domain.ZoneResolutionException;

import java.time.ZoneId;

/**
 * Resolves the zone for a single train-trip endpoint at the boundary, with the per-endpoint
 * contract shared by {@code BookTrainHandler} and {@code ChangeTrainHandler}: an explicitly chosen
 * {@link CommonZone} wins; otherwise the station's city/country must resolve via
 * {@link LocationZoneResolver} or the command is rejected. Departure and arrival each go through
 * this independently, so a trip may span two zones — {@link TrainZones} is what asks both.
 *
 * <p><strong>The failure is re-tagged on the way out</strong>, from a bare
 * {@link ZoneResolutionException} to an {@link UnresolvedStationZone} carrying which end failed and
 * why. Both facts are known here and nowhere further out: the caller sees only that something did
 * not resolve, which is exactly the message the form used to print. Whether the country box was
 * empty is the whole difference between "type the country" and "pick a zone", so it is read here
 * rather than guessed at the page.
 */
public class StationZone {

    private final LocationZoneResolver zoneResolver;

    public StationZone(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    /**
     * @throws UnresolvedStationZone when no zone was picked and none follows from the station's
     *         city and country.
     */
    public ZoneId resolve(LocationRole role, String explicitZone, TrainStationAddress station) {
        CommonZone picked = CommonZone.fromParam(explicitZone);
        if (picked != null) {
            return picked.zoneId();
        }
        try {
            return zoneResolver.resolve(station.city(), station.country());
        } catch (ZoneResolutionException unresolved) {
            throw new UnresolvedStationZone(role, station.country().isBlank()
                    ? UnresolvedStationZone.Cause.COUNTRY_MISSING
                    : UnresolvedStationZone.Cause.COUNTRY_UNRECOGNISED);
        }
    }
}
