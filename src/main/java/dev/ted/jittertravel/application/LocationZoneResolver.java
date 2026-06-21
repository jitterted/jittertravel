package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Derives the IANA time zone of a location from its {@link Address}. Resolution precedence:
 * <ol>
 *   <li>a curated city table (covers notable cities in multi-zone countries, where country alone is
 *       ambiguous);</li>
 *   <li>a curated country table (exact for single-zone countries).</li>
 * </ol>
 *
 * <p><strong>Strict, with no default zone:</strong> this is a travel app, so most entries are away
 * from any "home" zone and a silent default would be wrong more often than right. When the location
 * does not resolve, {@link #resolve(Address)} throws {@link ZoneResolutionException} rather than
 * guessing. The boundary's contract: an explicitly chosen {@code CommonZone} wins; otherwise
 * location-based resolution must succeed or the command is rejected and the form re-prompts for a
 * {@code CommonZone}. The completeness of the tables for existing data is verified by an audit over
 * every distinct stored location.
 *
 * <p>Plain Java (no Spring) so it stays usable from tests. The tables are intentionally a curated
 * subset, not an exhaustive geo database — a wrong guess is correctable by re-editing the entry (or
 * picking a {@code CommonZone} in the form).
 */
public class LocationZoneResolver {

    private final Map<String, ZoneId> cityToZone;
    private final Map<String, ZoneId> countryToZone;

    public LocationZoneResolver() {
        this.cityToZone = defaultCityTable();
        this.countryToZone = defaultCountryTable();
    }

    /**
     * The zone for {@code address}.
     *
     * @throws ZoneResolutionException when the location cannot be resolved (the caller must instead
     *         use an explicitly chosen {@code CommonZone}).
     */
    public ZoneId resolve(Address address) {
        if (address == null) {
            throw new ZoneResolutionException(address);
        }
        return resolve(address.city(), address.country());
    }

    /**
     * The zone for a {@code city}/{@code country} pair (e.g. a train station, which is not an
     * {@link Address}). Same precedence as {@link #resolve(Address)}: city table, then country table.
     *
     * @throws ZoneResolutionException when neither resolves.
     */
    public ZoneId resolve(String city, String country) {
        ZoneId byCity = cityToZone.get(normalize(city));
        if (byCity != null) {
            return byCity;
        }
        ZoneId byCountry = countryToZone.get(normalize(country));
        if (byCountry != null) {
            return byCountry;
        }
        throw new ZoneResolutionException(city, country);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, ZoneId> defaultCityTable() {
        Map<String, ZoneId> table = new HashMap<>();
        // United States (multi-zone: country alone is ambiguous)
        put(table, "America/New_York", "new york", "boston", "washington", "atlanta", "miami");
        put(table, "America/Chicago", "chicago", "dallas", "houston", "austin", "minneapolis");
        put(table, "America/Denver", "denver", "salt lake city", "albuquerque");
        put(table, "America/Phoenix", "phoenix");
        put(table, "America/Los_Angeles", "los angeles", "san francisco", "seattle", "portland",
                "san diego", "las vegas");
        // Canada (multi-zone)
        put(table, "America/Toronto", "toronto", "ottawa", "montreal");
        put(table, "America/Vancouver", "vancouver");
        put(table, "America/Edmonton", "calgary", "edmonton");
        // Australia (multi-zone)
        put(table, "Australia/Sydney", "sydney", "melbourne", "canberra", "brisbane");
        put(table, "Australia/Perth", "perth");
        return table;
    }

    private static Map<String, ZoneId> defaultCountryTable() {
        Map<String, ZoneId> table = new HashMap<>();
        put(table, "Europe/London", "united kingdom", "uk", "england", "scotland", "wales");
        put(table, "Europe/Dublin", "ireland");
        put(table, "Europe/Paris", "france");
        put(table, "Europe/Berlin", "germany");
        put(table, "Europe/Amsterdam", "netherlands");
        put(table, "Europe/Brussels", "belgium");
        put(table, "Europe/Madrid", "spain");
        put(table, "Europe/Rome", "italy");
        put(table, "Europe/Zurich", "switzerland");
        put(table, "Europe/Vienna", "austria");
        put(table, "Europe/Copenhagen", "denmark");
        put(table, "Europe/Oslo", "norway");
        put(table, "Europe/Stockholm", "sweden");
        put(table, "Europe/Helsinki", "finland");
        put(table, "Europe/Warsaw", "poland");
        put(table, "Europe/Prague", "czech republic", "czechia");
        put(table, "Europe/Lisbon", "portugal");
        put(table, "Asia/Tokyo", "japan");
        put(table, "Asia/Singapore", "singapore");
        put(table, "Asia/Kolkata", "india");
        put(table, "Atlantic/Reykjavik", "iceland");
        put(table, "Pacific/Auckland", "new zealand");
        return table;
    }

    private static void put(Map<String, ZoneId> table, String zoneId, String... keys) {
        ZoneId zone = ZoneId.of(zoneId);
        for (String key : keys) {
            table.put(key, zone);
        }
    }
}
