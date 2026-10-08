package dev.ted.jittertravel.domain;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Derives the IANA time zone of a location from its {@link Address}. Resolution precedence:
 * <ol>
 *   <li>a curated city table (notable cities in multi-zone countries, and the exceptions that sit in
 *       a different zone from the rest of their state/province);</li>
 *   <li>a curated state/province table for the multi-zone countries the owner travels in (USA,
 *       Canada, Australia), scoped by country so {@code WA} cannot mean both Washington and Western
 *       Australia;</li>
 *   <li>a curated country table (exact for single-zone countries).</li>
 * </ol>
 *
 * <p>The state/province step exists because the city table cannot keep up: a conference venue is as
 * likely to be in Lone Tree, Colorado or North Gower, Ontario as in Denver or Ottawa, and the
 * country alone is ambiguous for exactly those countries. States cover the whole country in ~60
 * entries where cities would need thousands, so an unknown small town now resolves from the region
 * its address already carries.
 *
 * <p><strong>Strict, with no default zone:</strong> this is a travel app, so most entries are away
 * from any "home" zone and a silent default would be wrong more often than right. When the location
 * does not resolve, {@link #resolve(Address)} throws {@link ZoneResolutionException} rather than
 * guessing. The boundary's contract: an explicitly chosen {@code CommonZone} wins; otherwise
 * location-based resolution must succeed or the command is rejected and the form re-prompts for a
 * {@code CommonZone}. The completeness of the tables for existing data is verified by an audit over
 * every distinct stored location.
 *
 * <p><strong>Zone-split states are keyed to their predominant zone</strong> (e.g. Florida to
 * Eastern, Texas to Central, Idaho to Mountain), because the city step runs first and is where an
 * exception like Pensacola or El Paso belongs. This is curation, not a default: a region we do not
 * know still fails loudly, and a wrong guess is correctable by re-editing the entry or picking a
 * {@code CommonZone}.
 *
 * <p>Plain Java (no Spring) so it stays usable from tests. The tables are intentionally a curated
 * subset, not an exhaustive geo database.
 */
public class LocationZoneResolver {

    private final Map<String, ZoneId> cityToZone;
    private final Map<String, ZoneId> regionToZone;
    private final Map<String, ZoneId> countryToZone;
    private final Map<String, String> countryToRegionScope;

    public LocationZoneResolver() {
        this.cityToZone = defaultCityTable();
        this.regionToZone = defaultRegionTable();
        this.countryToZone = defaultCountryTable();
        this.countryToRegionScope = defaultRegionScopeTable();
    }

    /**
     * The zone for {@code address}, using its city, region (state/province) and country.
     *
     * @throws ZoneResolutionException when the location cannot be resolved (the caller must instead
     *         use an explicitly chosen {@code CommonZone}).
     */
    public ZoneId resolve(Address address) {
        if (address == null) {
            throw new ZoneResolutionException(address);
        }
        return resolve(address.city(), address.region(), address.country());
    }

    /**
     * The zone for a {@code city}/{@code country} pair that carries no region — a train station,
     * which is not an {@link Address}.
     *
     * @throws ZoneResolutionException when neither resolves.
     */
    public ZoneId resolve(String city, String country) {
        return resolve(city, "", country);
    }

    /**
     * The zone for a city / state-or-province / country triple. Precedence is city, then region
     * (scoped to the country, and only for the multi-zone countries in the table), then country.
     *
     * @throws ZoneResolutionException when none of the three resolves.
     */
    public ZoneId resolve(String city, String region, String country) {
        ZoneId byCity = cityToZone.get(normalize(city));
        if (byCity != null) {
            return byCity;
        }
        ZoneId byRegion = regionToZone.get(regionKey(region, country));
        if (byRegion != null) {
            return byRegion;
        }
        ZoneId byCountry = countryToZone.get(normalize(country));
        if (byCountry != null) {
            return byCountry;
        }
        throw new ZoneResolutionException(city, region, country);
    }

    /**
     * The lookup key for the region table: {@code "<country scope>|<region>"}, e.g. {@code "us|co"}.
     * Returns a key that cannot match when the country is not one of the multi-zone countries, so a
     * region name never resolves against the wrong country's states.
     */
    private String regionKey(String region, String country) {
        String scope = countryToRegionScope.get(normalize(country));
        if (scope == null) {
            return "";
        }
        return scope + "|" + normalize(region);
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

    /**
     * States and provinces of the multi-zone countries, keyed {@code "<scope>|<postal code>"}. A
     * region is stored as its code since the location-codes migration (2026-10-08), and the command
     * refuses anything else, so the spelled-out names that used to sit beside the codes are gone.
     */
    private static Map<String, ZoneId> defaultRegionTable() {
        Map<String, ZoneId> table = new HashMap<>();

        // --- United States. Split states (FL, IN, KY, MI, TN, KS, NE, ND, SD, TX, ID, OR) are
        // keyed to their predominant zone; put an exception city in the city table above.
        putRegion(table, "America/New_York", "us",
                "ct", "de", "dc", "fl", "ga", "in", "ky", "me", "md", "ma", "mi", "nh", "nj", "ny",
                "nc", "oh", "pa", "ri", "sc", "vt", "va", "wv");
        putRegion(table, "America/Chicago", "us",
                "al", "ar", "il", "ia", "ks", "la", "mn", "ms", "mo", "ne", "nd", "ok", "sd", "tn",
                "tx", "wi");
        putRegion(table, "America/Denver", "us", "co", "id", "mt", "nm", "ut", "wy");
        putRegion(table, "America/Phoenix", "us", "az");
        putRegion(table, "America/Los_Angeles", "us", "ca", "nv", "or", "wa");
        putRegion(table, "America/Anchorage", "us", "ak");
        putRegion(table, "Pacific/Honolulu", "us", "hi");

        // --- Canada
        putRegion(table, "America/St_Johns", "ca", "nl");
        putRegion(table, "America/Halifax", "ca", "ns", "nb", "pe");
        putRegion(table, "America/Toronto", "ca", "on", "qc");
        putRegion(table, "America/Winnipeg", "ca", "mb");
        putRegion(table, "America/Regina", "ca", "sk");
        putRegion(table, "America/Edmonton", "ca", "ab");
        putRegion(table, "America/Vancouver", "ca", "bc");
        putRegion(table, "America/Whitehorse", "ca", "yt");
        putRegion(table, "America/Yellowknife", "ca", "nt");
        putRegion(table, "America/Iqaluit", "ca", "nu");

        // --- Australia
        putRegion(table, "Australia/Sydney", "au", "nsw", "act");
        putRegion(table, "Australia/Melbourne", "au", "vic");
        putRegion(table, "Australia/Hobart", "au", "tas");
        putRegion(table, "Australia/Brisbane", "au", "qld");
        putRegion(table, "Australia/Adelaide", "au", "sa");
        putRegion(table, "Australia/Perth", "au", "wa");
        putRegion(table, "Australia/Darwin", "au", "nt");

        return table;
    }

    /**
     * The countries whose regions the table above covers, by ISO code, mapped to their scope prefix.
     * A country absent here never consults the region table — a single-zone country does not need
     * it, and an unlisted multi-zone country must keep failing loudly rather than matching a US state
     * code.
     */
    private static Map<String, String> defaultRegionScopeTable() {
        return Map.of("us", "us", "ca", "ca", "au", "au");
    }

    /**
     * Single-zone countries, keyed by the ISO code a picked country is stored as ({@link Countries}).
     * The names stored before the picker existed went with the location-codes migration
     * (docs/LocationDataCleanupPlan.md §6).
     */
    private static Map<String, ZoneId> defaultCountryTable() {
        Map<String, ZoneId> table = new HashMap<>();
        put(table, "Europe/London", "gb");
        put(table, "Europe/Dublin", "ie");
        put(table, "Europe/Paris", "fr");
        put(table, "Europe/Berlin", "de");
        put(table, "Europe/Amsterdam", "nl");
        put(table, "Europe/Brussels", "be");
        put(table, "Europe/Madrid", "es");
        put(table, "Europe/Rome", "it");
        put(table, "Europe/Zurich", "ch");
        put(table, "Europe/Vienna", "at");
        put(table, "Europe/Copenhagen", "dk");
        put(table, "Europe/Oslo", "no");
        put(table, "Europe/Stockholm", "se");
        put(table, "Europe/Helsinki", "fi");
        put(table, "Europe/Warsaw", "pl");
        put(table, "Europe/Prague", "cz");
        put(table, "Europe/Lisbon", "pt");
        put(table, "Asia/Tokyo", "jp");
        put(table, "Asia/Singapore", "sg");
        put(table, "Asia/Kolkata", "in");
        put(table, "Atlantic/Reykjavik", "is");
        put(table, "Africa/Casablanca", "ma");
        put(table, "Pacific/Auckland", "nz");
        return table;
    }

    private static void put(Map<String, ZoneId> table, String zoneId, String... keys) {
        ZoneId zone = ZoneId.of(zoneId);
        for (String key : keys) {
            table.put(key, zone);
        }
    }

    private static void putRegion(Map<String, ZoneId> table, String zoneId, String scope,
                                  String... regions) {
        ZoneId zone = ZoneId.of(zoneId);
        for (String region : regions) {
            table.put(scope + "|" + region, zone);
        }
    }
}
