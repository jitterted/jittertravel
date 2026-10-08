package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Countries;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AddressRenderer {

    private static final Countries COUNTRIES = new Countries();

    public static String mapsUrl(Address address) {
        String query = address.street() + " " + address.city() + " " + country(address);
        return buildUrl(query.strip());
    }

    public static String mapsUrl(String placeName, Address address) {
        String query = placeName + " " + address.street() + " " + address.city() + " " + country(address);
        return buildUrl(query.strip());
    }

    /** The name for a picked code, so the search reads "Germany" rather than "DE". */
    private static String country(Address address) {
        return COUNTRIES.name(address.country()).orElse(address.country());
    }

    private static String buildUrl(String query) {
        return "https://www.google.com/maps/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
