package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps an {@link AirportCode} to the IANA time zone of that airport. Like {@link LocationZoneResolver}
 * this is <strong>strict, with no default</strong>: an unknown code throws {@link ZoneResolutionException}
 * rather than guessing. The curated set mirrors {@link StaticAirportCityResolver} — the airports this
 * app already knows about — so any future code not yet covered is caught by the zone audit before it
 * can corrupt a stored datetime.
 *
 * <p>Plain Java (no Spring) so it stays usable from tests and the read-time upcaster.
 */
public class AirportZoneResolver {

    private final Map<String, ZoneId> airportToZone = defaultAirportTable();

    /**
     * The zone for {@code airport}.
     *
     * @throws ZoneResolutionException when the airport code is not in the curated table.
     */
    public ZoneId resolve(AirportCode airport) {
        ZoneId zone = airportToZone.get(airport.code());
        if (zone == null) {
            throw new ZoneResolutionException(airport.code());
        }
        return zone;
    }

    private static Map<String, ZoneId> defaultAirportTable() {
        Map<String, ZoneId> table = new HashMap<>();
        // North America
        put(table, "America/Los_Angeles", "SFO", "SJC", "OAK", "LAX", "SAN", "SEA", "PDX");
        put(table, "America/Denver", "DEN");
        put(table, "America/New_York", "JFK", "EWR", "LGA", "BOS", "ATL", "MIA", "IAD", "DCA");
        put(table, "America/Chicago", "ORD", "MDW", "DFW", "IAH", "MSP");
        put(table, "America/Toronto", "YYZ");
        put(table, "America/Vancouver", "YVR");
        // Europe
        put(table, "Europe/London", "LHR", "LGW", "STN", "LCY");
        put(table, "Europe/Paris", "CDG", "ORY");
        put(table, "Europe/Berlin", "FRA", "MUC", "BER", "HAM");
        put(table, "Europe/Amsterdam", "AMS");
        put(table, "Europe/Brussels", "BRU");
        put(table, "Europe/Zurich", "ZRH", "GVA");
        put(table, "Europe/Vienna", "VIE");
        put(table, "Europe/Rome", "FCO", "MXP");
        put(table, "Europe/Madrid", "BCN", "MAD");
        put(table, "Europe/Stockholm", "ARN");
        put(table, "Europe/Copenhagen", "CPH");
        put(table, "Europe/Helsinki", "HEL");
        put(table, "Europe/Oslo", "OSL");
        put(table, "Europe/Warsaw", "WAW");
        put(table, "Europe/Prague", "PRG");
        put(table, "Europe/Budapest", "BUD");
        put(table, "Europe/Dublin", "DUB");
        put(table, "Europe/Lisbon", "LIS");
        // Asia-Pacific
        put(table, "Asia/Tokyo", "NRT", "HND");
        put(table, "Asia/Singapore", "SIN");
        put(table, "Asia/Hong_Kong", "HKG");
        put(table, "Australia/Sydney", "SYD");
        put(table, "Asia/Seoul", "ICN");
        return table;
    }

    private static void put(Map<String, ZoneId> table, String zoneId, String... codes) {
        ZoneId zone = ZoneId.of(zoneId);
        for (String code : codes) {
            table.put(code, zone);
        }
    }
}
