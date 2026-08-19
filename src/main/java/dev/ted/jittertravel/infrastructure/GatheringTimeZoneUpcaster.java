package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.LocationZoneResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * v1→v2 for {@code GatheringPlanned}/{@code GatheringChanged}: the one migration that changes the
 * field <em>set</em> rather than a field's type. A gathering's legacy {@code date} + {@code
 * startTime} + {@code endTime} trio collapses into {@code startsAt} and {@code endsAt}. The three
 * legacy keys are removed, because the record no longer declares them and the golden contract test
 * rejects unknown properties. Both endpoints are at the one venue, so they share its address-derived
 * zone.
 */
class GatheringTimeZoneUpcaster implements EventUpcaster {

    private final LocationZoneResolver locationZoneResolver;
    private final WallClockZoning zoning;

    GatheringTimeZoneUpcaster(LocationZoneResolver locationZoneResolver, WallClockZoning zoning) {
        this.locationZoneResolver = locationZoneResolver;
        this.zoning = zoning;
    }

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        return eventVersion == 1
               && (eventLogicalType.equals("GatheringPlanned") || eventLogicalType.equals("GatheringChanged"));
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        JsonNode date = payload.get("date");
        if (!zoning.isLegacyScalar(date)) {
            return; // already collapsed into startsAt/endsAt
        }
        JsonNode location = payload.get("location");
        ZoneId zone = locationZoneResolver.resolve(
                zoning.nestedText(location, "city"),
                zoning.nestedText(location, "region"),
                zoning.nestedText(location, "country"));
        LocalDate localDate = LocalDate.parse(date.asString());
        payload.set("startsAt", zoning.toZoned(localDate.atTime(localTime(payload, "startTime")), zone));
        payload.set("endsAt", zoning.toZoned(localDate.atTime(localTime(payload, "endTime")), zone));
        payload.remove("date");
        payload.remove("startTime");
        payload.remove("endTime");
    }

    private LocalTime localTime(ObjectNode payload, String field) {
        return LocalTime.parse(payload.get(field).asString());
    }
}
