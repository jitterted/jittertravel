package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;

/**
 * v1→v2 for {@code FlightBooked}/{@code FlightChanged}: departure and arrival resolve independently
 * from their airport codes. This is the one timezone rung wired to the {@link AirportZoneResolver}
 * rather than the {@link dev.ted.jittertravel.domain.LocationZoneResolver} — the split that made
 * the single all-events upcaster incohesive.
 */
class FlightTimeZoneUpcaster implements EventUpcaster {

    private final AirportZoneResolver airportZoneResolver;
    private final WallClockZoning zoning;

    FlightTimeZoneUpcaster(AirportZoneResolver airportZoneResolver, WallClockZoning zoning) {
        this.airportZoneResolver = airportZoneResolver;
        this.zoning = zoning;
    }

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 1
               && (eventLogicalType.equals("FlightBooked") || eventLogicalType.equals("FlightChanged"));
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        JsonNode departure = payload.get("departureDateTime");
        if (!zoning.isLegacyScalar(departure)) {
            return; // already a {utc, zone} object
        }
        ZoneId departureZone = airportZone(payload.get("departureAirport"));
        ZoneId arrivalZone = airportZone(payload.get("arrivalAirport"));
        payload.set("departureDateTime", zoning.toZoned(departure.asString(), departureZone));
        payload.set("arrivalDateTime", zoning.toZoned(payload.get("arrivalDateTime").asString(), arrivalZone));
    }

    private ZoneId airportZone(JsonNode airport) {
        return airportZoneResolver.resolve(AirportCode.of(zoning.nestedText(airport, "code")));
    }
}
