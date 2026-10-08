package dev.ted.jittertravel.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where each airport is: the city it serves, that city's state where the country picks one from a
 * list ({@link Subdivisions}), and the country's ISO code. The whole place lives in one entry, so a
 * transfer endpoint frozen from an airport is a complete address rather than a city with no country
 * (docs/LocationDataCleanupPlan.md §4.3) — and there is no second table to disagree with this one.
 *
 * <p>The region follows the city served, not the airport's own postal address: Newark is
 * {@code New York, NY}, Dulles is {@code Washington DC, DC}, because the city is what the schedule
 * matches on.
 */
public class StaticAirportCityResolver implements AirportCityResolver {

    private record Location(String city, String region, String country) {
    }

    private static final Map<String, Location> AIRPORT_TO_LOCATION = Map.ofEntries(
            // North America
            Map.entry("SFO", new Location("San Francisco", "CA", "US")),
            Map.entry("SJC", new Location("San Jose", "CA", "US")),
            Map.entry("OAK", new Location("Oakland", "CA", "US")),
            Map.entry("LAX", new Location("Los Angeles", "CA", "US")),
            Map.entry("SAN", new Location("San Diego", "CA", "US")),
            Map.entry("SEA", new Location("Seattle", "WA", "US")),
            Map.entry("PDX", new Location("Portland", "OR", "US")),
            Map.entry("DEN", new Location("Denver", "CO", "US")),
            Map.entry("JFK", new Location("New York", "NY", "US")),
            Map.entry("EWR", new Location("New York", "NY", "US")),
            Map.entry("LGA", new Location("New York", "NY", "US")),
            Map.entry("BOS", new Location("Boston", "MA", "US")),
            Map.entry("ORD", new Location("Chicago", "IL", "US")),
            Map.entry("MDW", new Location("Chicago", "IL", "US")),
            Map.entry("ATL", new Location("Atlanta", "GA", "US")),
            Map.entry("MIA", new Location("Miami", "FL", "US")),
            Map.entry("DFW", new Location("Dallas", "TX", "US")),
            Map.entry("IAH", new Location("Houston", "TX", "US")),
            Map.entry("IAD", new Location("Washington DC", "DC", "US")),
            Map.entry("DCA", new Location("Washington DC", "DC", "US")),
            Map.entry("MSP", new Location("Minneapolis", "MN", "US")),
            Map.entry("YYZ", new Location("Toronto", "ON", "CA")),
            Map.entry("YOW", new Location("Ottawa", "ON", "CA")),
            Map.entry("YVR", new Location("Vancouver", "BC", "CA")),
            // Europe
            Map.entry("LHR", new Location("London", "", "GB")),
            Map.entry("LGW", new Location("London", "", "GB")),
            Map.entry("STN", new Location("London", "", "GB")),
            Map.entry("LCY", new Location("London", "", "GB")),
            Map.entry("CDG", new Location("Paris", "", "FR")),
            Map.entry("ORY", new Location("Paris", "", "FR")),
            Map.entry("FRA", new Location("Frankfurt", "", "DE")),
            Map.entry("MUC", new Location("Munich", "", "DE")),
            Map.entry("BER", new Location("Berlin", "", "DE")),
            Map.entry("HAM", new Location("Hamburg", "", "DE")),
            Map.entry("AMS", new Location("Amsterdam", "", "NL")),
            Map.entry("BRU", new Location("Brussels", "", "BE")),
            Map.entry("ZRH", new Location("Zurich", "", "CH")),
            Map.entry("GVA", new Location("Geneva", "", "CH")),
            Map.entry("VIE", new Location("Vienna", "", "AT")),
            Map.entry("FCO", new Location("Rome", "", "IT")),
            Map.entry("MXP", new Location("Milan", "", "IT")),
            Map.entry("BCN", new Location("Barcelona", "", "ES")),
            Map.entry("MAD", new Location("Madrid", "", "ES")),
            Map.entry("ARN", new Location("Stockholm", "", "SE")),
            Map.entry("CPH", new Location("Copenhagen", "", "DK")),
            Map.entry("HEL", new Location("Helsinki", "", "FI")),
            Map.entry("OSL", new Location("Oslo", "", "NO")),
            Map.entry("WAW", new Location("Warsaw", "", "PL")),
            Map.entry("PRG", new Location("Prague", "", "CZ")),
            Map.entry("BUD", new Location("Budapest", "", "HU")),
            Map.entry("DUB", new Location("Dublin", "", "IE")),
            Map.entry("LIS", new Location("Lisbon", "", "PT")),
            // Asia-Pacific
            Map.entry("NRT", new Location("Tokyo", "", "JP")),
            Map.entry("HND", new Location("Tokyo", "", "JP")),
            Map.entry("SIN", new Location("Singapore", "", "SG")),
            Map.entry("HKG", new Location("Hong Kong", "", "HK")),
            Map.entry("SYD", new Location("Sydney", "NSW", "AU")),
            Map.entry("ICN", new Location("Seoul", "", "KR"))
    );

    /**
     * The reverse of {@link #AIRPORT_TO_LOCATION}'s cities, with every ambiguous city dropped rather
     * than resolved to an arbitrary one of its airports — see {@link AirportCityResolver#soleAirportFor}.
     * Built once: the table is a compile-time constant, so the index is too.
     */
    private static final Map<String, String> SOLE_AIRPORT_BY_CITY = soleAirportIndex();

    @Override
    public String cityFor(String airportCode) {
        Location location = AIRPORT_TO_LOCATION.get(airportCode.toUpperCase(Locale.ROOT));
        return location == null ? airportCode : location.city();
    }

    @Override
    public Optional<Address> addressFor(String airportCode) {
        return Optional.ofNullable(AIRPORT_TO_LOCATION.get(airportCode.toUpperCase(Locale.ROOT)))
                       .map(location -> new Address("", location.city(), location.region(), "",
                                                    location.country(), location.city()));
    }

    @Override
    public Optional<String> soleAirportFor(String city) {
        if (city == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SOLE_AIRPORT_BY_CITY.get(normalize(city)));
    }

    /** Every code in the table, so a test can hold each entry to the address rules. */
    Set<String> knownCodes() {
        return AIRPORT_TO_LOCATION.keySet();
    }

    private static Map<String, String> soleAirportIndex() {
        Map<String, String> soleAirport = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, Location> entry : AIRPORT_TO_LOCATION.entrySet()) {
            String city = normalize(entry.getValue().city());
            if (soleAirport.put(city, entry.getKey()) != null) {
                ambiguous.add(city);
            }
        }
        ambiguous.forEach(soleAirport::remove);
        return Map.copyOf(soleAirport);
    }

    private static String normalize(String city) {
        return city.trim().toLowerCase(Locale.ROOT);
    }
}
