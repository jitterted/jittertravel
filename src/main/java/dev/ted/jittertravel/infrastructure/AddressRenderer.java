package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AddressRenderer {

    public static String mapsUrl(Address address) {
        String query = address.street() + " " + address.city() + " " + address.country();
        return buildUrl(query.strip());
    }

    public static String mapsUrl(String placeName, Address address) {
        String query = placeName + " " + address.street() + " " + address.city() + " " + address.country();
        return buildUrl(query.strip());
    }

    private static String buildUrl(String query) {
        return "https://www.google.com/maps/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
