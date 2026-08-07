package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final AirportZoneResolver airportZoneResolver;
    private final JsonMapper jsonMapper;

    public EventPayloadUpcaster(LocationZoneResolver locationZoneResolver,
                                AirportZoneResolver airportZoneResolver,
                                JsonMapper jsonMapper) {
        this.locationZoneResolver = locationZoneResolver;
        this.airportZoneResolver = airportZoneResolver;
        this.jsonMapper = jsonMapper;
    }

    public JsonNode upcast(String logicalType, JsonNode payload) {
        if (!(payload instanceof ObjectNode object)) {
            return payload;
        }
        switch (logicalType) {
            case "HotelBooked", "HotelChanged" -> upcastHotelStay(object);
            case "TrainBooked", "TrainChanged" -> upcastTrainTrip(object);
            case "FlightBooked", "FlightChanged" -> upcastFlightTrip(object);
            case "GatheringPlanned", "GatheringChanged" -> upcastGathering(object);
            case "ConferenceTentativelyPlanned" -> upcastConference(object);
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
                addressField(object, "city"), addressField(object, "region"),
                addressField(object, "country"));
        object.set("checkIn", toZoned(checkIn.asText(), zone));
        object.set("checkOut", toZoned(object.get("checkOut").asText(), zone));
    }

    /**
     * The only <em>shape</em> change so far, rather than a field-type change: a gathering's legacy
     * {@code date} + {@code startTime} + {@code endTime} trio collapses into {@code startsAt} and
     * {@code endsAt}. The three legacy keys are removed, because the record no longer declares them
     * and the golden contract test rejects unknown properties. Both endpoints are at the one venue,
     * so they share its address-derived zone.
     */
    private void upcastGathering(ObjectNode object) {
        JsonNode date = object.get("date");
        if (!isLegacyScalar(date)) {
            return; // already collapsed into startsAt/endsAt
        }
        ZoneId zone = locationZoneResolver.resolve(
                nestedText(object.get("location"), "city"),
                nestedText(object.get("location"), "region"),
                nestedText(object.get("location"), "country"));
        LocalDate localDate = LocalDate.parse(date.asText());
        object.set("startsAt", toZoned(localDate.atTime(localTime(object, "startTime")), zone));
        object.set("endsAt", toZoned(localDate.atTime(localTime(object, "endTime")), zone));
        object.remove("date");
        object.remove("startTime");
        object.remove("endTime");
    }

    private static LocalTime localTime(ObjectNode object, String field) {
        return LocalTime.parse(object.get(field).asText());
    }

    /** Start and end are at the one venue, so they share its address-derived zone. */
    private void upcastConference(ObjectNode object) {
        JsonNode startDate = object.get("startDate");
        if (!isLegacyScalar(startDate)) {
            return; // already a {utc, zone} object
        }
        ZoneId zone = locationZoneResolver.resolve(
                nestedText(object.get("venueAddress"), "city"),
                nestedText(object.get("venueAddress"), "region"),
                nestedText(object.get("venueAddress"), "country"));
        object.set("startDate", toZoned(startDate.asText(), zone));
        object.set("endDate", toZoned(object.get("endDate").asText(), zone));
    }

    /**
     * Departure and arrival resolve <em>independently</em> from their own station's city/country —
     * a trip may span two zones (e.g. Frankfurt→Paris).
     */
    private void upcastTrainTrip(ObjectNode object) {
        JsonNode departure = object.get("departureDateTime");
        if (!isLegacyScalar(departure)) {
            return; // already a {utc, zone} object
        }
        ZoneId departureZone = stationZone(object, "departureStation");
        ZoneId arrivalZone = stationZone(object, "arrivalStation");
        object.set("departureDateTime", toZoned(departure.asText(), departureZone));
        object.set("arrivalDateTime", toZoned(object.get("arrivalDateTime").asText(), arrivalZone));
    }

    /**
     * Departure and arrival resolve independently from their airport codes.
     */
    private void upcastFlightTrip(ObjectNode object) {
        JsonNode departure = object.get("departureDateTime");
        if (!isLegacyScalar(departure)) {
            return; // already a {utc, zone} object
        }
        ZoneId departureZone = airportZone(object, "departureAirport");
        ZoneId arrivalZone = airportZone(object, "arrivalAirport");
        object.set("departureDateTime", toZoned(departure.asText(), departureZone));
        object.set("arrivalDateTime", toZoned(object.get("arrivalDateTime").asText(), arrivalZone));
    }

    private ZoneId airportZone(ObjectNode payload, String airportField) {
        JsonNode airport = payload.get(airportField);
        String code = nestedText(airport, "code");
        return airportZoneResolver.resolve(AirportCode.of(code));
    }

    private ZoneId stationZone(ObjectNode payload, String stationField) {
        JsonNode station = payload.get(stationField);
        String city = nestedText(station, "city");
        String country = nestedText(station, "country");
        return locationZoneResolver.resolve(city, country);
    }

    private static String nestedText(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText();
    }

    private JsonNode toZoned(String wallClock, ZoneId zone) {
        return toZoned(LocalDateTime.parse(wallClock), zone);
    }

    private JsonNode toZoned(LocalDateTime wallClock, ZoneId zone) {
        return jsonMapper.valueToTree(ZonedTimestamp.fromLocal(wallClock, zone));
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
