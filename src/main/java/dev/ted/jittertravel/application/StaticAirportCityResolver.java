package dev.ted.jittertravel.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class StaticAirportCityResolver implements AirportCityResolver {

    private static final Map<String, String> AIRPORT_TO_CITY = Map.ofEntries(
            // North America
            Map.entry("SFO", "San Francisco"),
            Map.entry("SJC", "San Jose"),
            Map.entry("OAK", "Oakland"),
            Map.entry("LAX", "Los Angeles"),
            Map.entry("SAN", "San Diego"),
            Map.entry("SEA", "Seattle"),
            Map.entry("PDX", "Portland"),
            Map.entry("DEN", "Denver"),
            Map.entry("JFK", "New York"),
            Map.entry("EWR", "New York"),
            Map.entry("LGA", "New York"),
            Map.entry("BOS", "Boston"),
            Map.entry("ORD", "Chicago"),
            Map.entry("MDW", "Chicago"),
            Map.entry("ATL", "Atlanta"),
            Map.entry("MIA", "Miami"),
            Map.entry("DFW", "Dallas"),
            Map.entry("IAH", "Houston"),
            Map.entry("IAD", "Washington DC"),
            Map.entry("DCA", "Washington DC"),
            Map.entry("MSP", "Minneapolis"),
            Map.entry("YYZ", "Toronto"),
            Map.entry("YVR", "Vancouver"),
            // Europe
            Map.entry("LHR", "London"),
            Map.entry("LGW", "London"),
            Map.entry("STN", "London"),
            Map.entry("LCY", "London"),
            Map.entry("CDG", "Paris"),
            Map.entry("ORY", "Paris"),
            Map.entry("FRA", "Frankfurt"),
            Map.entry("MUC", "Munich"),
            Map.entry("BER", "Berlin"),
            Map.entry("HAM", "Hamburg"),
            Map.entry("AMS", "Amsterdam"),
            Map.entry("BRU", "Brussels"),
            Map.entry("ZRH", "Zurich"),
            Map.entry("GVA", "Geneva"),
            Map.entry("VIE", "Vienna"),
            Map.entry("FCO", "Rome"),
            Map.entry("MXP", "Milan"),
            Map.entry("BCN", "Barcelona"),
            Map.entry("MAD", "Madrid"),
            Map.entry("ARN", "Stockholm"),
            Map.entry("CPH", "Copenhagen"),
            Map.entry("HEL", "Helsinki"),
            Map.entry("OSL", "Oslo"),
            Map.entry("WAW", "Warsaw"),
            Map.entry("PRG", "Prague"),
            Map.entry("BUD", "Budapest"),
            Map.entry("DUB", "Dublin"),
            Map.entry("LIS", "Lisbon"),
            // Asia-Pacific
            Map.entry("NRT", "Tokyo"),
            Map.entry("HND", "Tokyo"),
            Map.entry("SIN", "Singapore"),
            Map.entry("HKG", "Hong Kong"),
            Map.entry("SYD", "Sydney"),
            Map.entry("ICN", "Seoul")
    );

    /**
     * The reverse of {@link #AIRPORT_TO_CITY}, with every ambiguous city dropped rather than
     * resolved to an arbitrary one of its airports — see {@link AirportCityResolver#soleAirportFor}.
     * Built once: the table is a compile-time constant, so the index is too.
     */
    private static final Map<String, String> SOLE_AIRPORT_BY_CITY = soleAirportIndex();

    @Override
    public String cityFor(String airportCode) {
        return AIRPORT_TO_CITY.getOrDefault(airportCode.toUpperCase(), airportCode);
    }

    @Override
    public Optional<String> soleAirportFor(String city) {
        if (city == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SOLE_AIRPORT_BY_CITY.get(normalize(city)));
    }

    private static Map<String, String> soleAirportIndex() {
        Map<String, String> soleAirport = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, String> entry : AIRPORT_TO_CITY.entrySet()) {
            String city = normalize(entry.getValue());
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
