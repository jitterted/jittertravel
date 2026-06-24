package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.CommonZone;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Resolves the zone for a single flight endpoint at the boundary, with the per-endpoint contract
 * shared by {@link BookFlightHandler} and {@link ChangeFlightHandler}: an explicitly chosen
 * {@link CommonZone} name or raw IANA zone ID (from AeroDataBox) wins; otherwise the airport code
 * must resolve via {@link AirportZoneResolver} or a {@link ZoneResolutionException} rejects the
 * command (the form then re-prompts for a {@code CommonZone}). Departure and arrival each go
 * through this independently, so a flight may span two zones.
 */
public class FlightEndpointZone {

    private final AirportZoneResolver airportZoneResolver;

    public FlightEndpointZone(AirportZoneResolver airportZoneResolver) {
        this.airportZoneResolver = airportZoneResolver;
    }

    public ZoneId resolve(String explicitZone, AirportCode airportCode) {
        if (explicitZone != null && !explicitZone.isBlank()) {
            CommonZone picked = CommonZone.fromParam(explicitZone);
            if (picked != null) {
                return picked.zoneId();
            }
            try {
                return ZoneId.of(explicitZone); // raw IANA zone ID from AeroDataBox
            } catch (DateTimeException ignored) {
            }
        }
        return airportZoneResolver.resolve(airportCode);
    }
}
