package dev.ted.jittertravel.application;

/**
 * A city/country location as it appears in event data (hotels, train stations, gatherings,
 * conferences) — the unit the zone audit resolves and reports on.
 */
public record CityCountry(String city, String country) {

    public CityCountry {
        city = city != null ? city : "";
        country = country != null ? country : "";
    }

    /** A human-readable label for the audit, e.g. {@code "Frankfurt, Germany"}. */
    public String label() {
        if (city.isBlank() && country.isBlank()) {
            return "(no location)";
        }
        if (country.isBlank()) {
            return city;
        }
        if (city.isBlank()) {
            return country;
        }
        return city + ", " + country;
    }
}
