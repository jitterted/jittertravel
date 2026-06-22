package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Read-time upcaster: rewrites a legacy event payload — where a datetime was stored as a bare
 * wall-clock scalar (e.g. {@code "checkIn": "2026-06-17T15:00:00"}) — into the current shape, a
 * {@link ZonedTimestamp} object ({@code {"utc": ..., "zone": ...}}). The zone is derived from the
 * event's own location, exactly as live entry does, so a replayed legacy event resolves to the same
 * moment a freshly-booked one would.
 *
 * <p>Idempotent: a payload already in the new shape (the datetime field is an object, not a scalar)
 * is returned untouched, so new rows pass straight through. Keyed by the stable logical event type
 * (see {@link EventTypes}); add a case here when a new event type migrates a datetime field to
 * {@link ZonedTimestamp}. The pre-migration zone audit ({@code /admin/zone-audit}) guarantees every
 * legacy location resolves, so this never throws for stored data.
 */
public class EventPayloadUpcaster {

    private final LocationZoneResolver locationZoneResolver;
    private final JsonMapper jsonMapper;

    public EventPayloadUpcaster(LocationZoneResolver locationZoneResolver, JsonMapper jsonMapper) {
        this.locationZoneResolver = locationZoneResolver;
        this.jsonMapper = jsonMapper;
    }

    public JsonNode upcast(String logicalType, JsonNode payload) {
        if (!(payload instanceof ObjectNode object)) {
            return payload;
        }
        switch (logicalType) {
            case "HotelBooked", "HotelChanged" -> upcastHotelStay(object);
            default -> { /* type not migrated, or it has no datetime fields */ }
        }
        return object;
    }

    /** check-in/check-out share the hotel's single, address-derived zone. */
    private void upcastHotelStay(ObjectNode object) {
        JsonNode checkIn = object.get("checkIn");
        if (!isLegacyScalar(checkIn)) {
            return; // already a {utc, zone} object
        }
        ZoneId zone = locationZoneResolver.resolve(
                addressField(object, "city"), addressField(object, "country"));
        object.set("checkIn", toZoned(checkIn.asText(), zone));
        object.set("checkOut", toZoned(object.get("checkOut").asText(), zone));
    }

    private JsonNode toZoned(String wallClock, ZoneId zone) {
        return jsonMapper.valueToTree(ZonedTimestamp.fromLocal(LocalDateTime.parse(wallClock), zone));
    }

    private static boolean isLegacyScalar(JsonNode node) {
        return node != null && node.isTextual();
    }

    private static String addressField(ObjectNode payload, String field) {
        JsonNode address = payload.get("address");
        if (address == null) {
            return "";
        }
        JsonNode value = address.get(field);
        return value == null ? "" : value.asText();
    }
}
