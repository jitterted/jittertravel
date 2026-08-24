package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;

/**
 * v1→v2 for {@code TrainBooked}/{@code TrainChanged}: departure and arrival resolve
 * <em>independently</em> from their own station's city/country — a trip may span two zones (e.g.
 * Frankfurt→Paris).
 */
class TrainTimeZoneUpcaster implements EventUpcaster {

    private final LocationZoneResolver locationZoneResolver;
    private final WallClockZoning zoning;

    TrainTimeZoneUpcaster(LocationZoneResolver locationZoneResolver, WallClockZoning zoning) {
        this.locationZoneResolver = locationZoneResolver;
        this.zoning = zoning;
    }

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 1
               && (eventLogicalType.equals("TrainBooked") || eventLogicalType.equals("TrainChanged"));
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        JsonNode departure = payload.get("departureDateTime");
        if (!zoning.isLegacyScalar(departure)) {
            return; // already a {utc, zone} object
        }
        ZoneId departureZone = stationZone(payload.get("departureStation"));
        ZoneId arrivalZone = stationZone(payload.get("arrivalStation"));
        payload.set("departureDateTime", zoning.toZoned(departure.asString(), departureZone));
        payload.set("arrivalDateTime", zoning.toZoned(payload.get("arrivalDateTime").asString(), arrivalZone));
    }

    private ZoneId stationZone(JsonNode station) {
        return locationZoneResolver.resolve(
                zoning.nestedText(station, "city"),
                zoning.nestedText(station, "country"));
    }
}
