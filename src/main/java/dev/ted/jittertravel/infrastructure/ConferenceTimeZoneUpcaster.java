package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.ZoneId;

/**
 * v1→v2 for {@code ConferencePlanned}: start and end are at the one venue, so they share
 * its address-derived zone. The v2→v3 {@code format} increment is a <em>separate</em> rung
 * ({@link ConferenceFormatUpcaster}) — a row written after this migration but before {@code format}
 * existed carries an object {@code startDate} yet no {@code format}, and the composite climbs it
 * through the format rung alone.
 */
class ConferenceTimeZoneUpcaster implements EventUpcaster {

    private final LocationZoneResolver locationZoneResolver;
    private final WallClockZoning zoning;

    ConferenceTimeZoneUpcaster(LocationZoneResolver locationZoneResolver, WallClockZoning zoning) {
        this.locationZoneResolver = locationZoneResolver;
        this.zoning = zoning;
    }

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 1 && eventLogicalType.equals("ConferencePlanned");
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        JsonNode startDate = payload.get("startDate");
        if (!zoning.isLegacyScalar(startDate)) {
            return; // already a {utc, zone} object
        }
        JsonNode venueAddress = payload.get("venueAddress");
        ZoneId zone = locationZoneResolver.resolve(
                zoning.nestedText(venueAddress, "city"),
                zoning.nestedText(venueAddress, "region"),
                zoning.nestedText(venueAddress, "country"));
        payload.set("startDate", zoning.toZoned(startDate.asString(), zone));
        payload.set("endDate", zoning.toZoned(payload.get("endDate").asString(), zone));
    }
}
