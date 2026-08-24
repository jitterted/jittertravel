package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;

/**
 * v1→v2 for {@code HotelBooked}/{@code HotelChanged}: check-in and check-out share the hotel's
 * single, address-derived zone.
 */
class HotelTimeZoneUpcaster implements EventUpcaster {

    private final LocationZoneResolver locationZoneResolver;
    private final WallClockZoning zoning;

    HotelTimeZoneUpcaster(LocationZoneResolver locationZoneResolver, WallClockZoning zoning) {
        this.locationZoneResolver = locationZoneResolver;
        this.zoning = zoning;
    }

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 1
               && (eventLogicalType.equals("HotelBooked") || eventLogicalType.equals("HotelChanged"));
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        JsonNode checkIn = payload.get("checkIn");
        if (!zoning.isLegacyScalar(checkIn)) {
            return; // already a {utc, zone} object
        }
        JsonNode address = payload.get("address");
        ZoneId zone = locationZoneResolver.resolve(
                zoning.nestedText(address, "city"),
                zoning.nestedText(address, "region"),
                zoning.nestedText(address, "country"));
        payload.set("checkIn", zoning.toZoned(checkIn.asString(), zone));
        payload.set("checkOut", zoning.toZoned(payload.get("checkOut").asString(), zone));
    }
}
