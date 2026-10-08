package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Countries;
import dev.ted.jittertravel.domain.Subdivision;
import dev.ted.jittertravel.domain.Subdivisions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The location-codes rung for every type that carries an address or a station address: a country
 * stored as a name becomes its ISO 3166-1 alpha-2 code, and a US, Canadian or Australian region
 * stored as a name becomes its postal code ({@code docs/LocationDataCleanupPlan.md} §5.1). Every other
 * region is left exactly as typed (D3), and a blank country stays blank — the airport endpoints that
 * have one are filled in by the eager migration, which may consult the airport table where a rung may
 * not (R7a).
 *
 * <p><b>A country it cannot name fails loud</b> (Ted, 2026-10-07): a payload that would bind with a
 * name where a code belongs is a row no form can save again, so replay and restore stop on it and
 * name the event. The boot-replay preflight run against the backup taken just before the push is what
 * meets it first.
 *
 * <p>The types sit at different versions, so the rung claims each at its own: the {@code
 * ZonedTimestamp} families advance 2→3, {@code ConferencePlanned} 3→4 (after its {@code format} rung),
 * and the two types born zoned 1→2.
 */
class LocationCodesUpcaster implements EventUpcaster {

    /**
     * Spellings stored in the log that are not a country's name in {@link Countries}. {@code Brussels}
     * is one hotel's country (event 19, the Prize by Radisson in Antwerp): a city in the wrong box,
     * but it has to bind, so its correction lives here rather than with the eager migration's fixes.
     */
    private static final Map<String, String> ALIASES = Map.of(
            "usa", "US",
            "uk", "GB",
            "brussels", "BE");

    private static final Map<String, AddressBearing> TYPES = Map.of(
            "HotelBooked", new AddressBearing(2, "address"),
            "HotelChanged", new AddressBearing(2, "address"),
            "TrainBooked", new AddressBearing(2, "departureStation", "arrivalStation"),
            "TrainChanged", new AddressBearing(2, "departureStation", "arrivalStation"),
            "GatheringPlanned", new AddressBearing(2, "location"),
            "GatheringChanged", new AddressBearing(2, "location"),
            "ConferencePlanned", new AddressBearing(3, "venueAddress"),
            "GroundTransferPlanned", new AddressBearing(1, "origin", "destination"),
            "PrivateEventPlanned", new AddressBearing(1, "location"));

    private final Countries countries = new Countries();
    private final Subdivisions subdivisions = new Subdivisions();

    @Override
    public boolean canHandle(String eventLogicalType, int eventVersion) {
        AddressBearing type = TYPES.get(eventLogicalType);
        return type != null && type.fromVersion() == eventVersion;
    }

    @Override
    public void upcast(ObjectNode payload, String eventLogicalType) {
        for (String field : TYPES.get(eventLogicalType).fields()) {
            if (payload.get(field) instanceof ObjectNode place) {
                toCodes(place);
            }
        }
    }

    private void toCodes(ObjectNode place) {
        String country = countryCode(text(place, "country"));
        place.put("country", country);
        // A station has no region, and only these three countries' regions are a fixed list.
        if (place.has("region") && subdivisions.required(country)) {
            place.put("region", regionCode(country, text(place, "region")));
        }
    }

    private String countryCode(String stored) {
        if (stored.isEmpty() || countries.isKnown(stored)) {
            return stored.toUpperCase(Locale.ROOT);
        }
        return countries.codeFor(stored)
                        .or(() -> Optional.ofNullable(ALIASES.get(stored.toLowerCase(Locale.ROOT))))
                        .orElseThrow(() -> new IllegalStateException(
                                "No country code for '%s'".formatted(stored)));
    }

    private String regionCode(String country, String stored) {
        if (stored.isEmpty()) {
            return stored;
        }
        return subdivisions.find(country, stored)
                           .or(() -> subdivisions.findByName(country, stored))
                           .map(Subdivision::code)
                           .orElseThrow(() -> new IllegalStateException(
                                   "No region of %s named '%s'".formatted(country, stored)));
    }

    private static String text(ObjectNode place, String field) {
        JsonNode value = place.get(field);
        return value == null || value.isNull() ? "" : value.asString().trim();
    }

    /** The version a type leaves through this rung, and the fields of it holding a place. */
    private record AddressBearing(int fromVersion, List<String> fields) {
        AddressBearing(int fromVersion, String... fields) {
            this(fromVersion, List.of(fields));
        }
    }
}
