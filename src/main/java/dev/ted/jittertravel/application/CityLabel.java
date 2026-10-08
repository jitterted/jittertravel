package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Countries;

/**
 * Writes where a place is as one short line — {@code "Denver, CO"} in the United States,
 * {@code "Vienna, Austria"} everywhere else (Ted, 2026-10-06). Presentation-layer, shared by every
 * projector and renderer that shows a city, so the rule lives in one place.
 * <p>
 * The part after the city is the <em>qualifier</em>: a US state's postal code, or the country's name.
 * Outside the US the region is never shown — stored regions there are as often a borough
 * ({@code "Westminster Borough"}) as a province. A US place with no state reads {@code "USA"}.
 * <p>
 * Since the location-codes migration (2026-10-08) a country is stored as its ISO code and a US
 * region as a state's postal code or nothing: the command refuses anything else, and the read path
 * converts or fails loud on older rows. So the region is shown as stored, to the owner and on the
 * public calendar alike (Ted, 2026-10-08) — the public-only fallback for a US region that was not a
 * state went with the spellings that made one possible.
 */
public class CityLabel {

    private static final String UNITED_STATES = "US";
    private static final String USA = "USA";

    private final Countries countries = new Countries();

    /** "City, Qualifier", or the city alone when there is no qualifier. */
    public String label(Address address) {
        return joined(address.city(), qualifier(address));
    }

    /** The state code or country that follows the city, or {@code ""} when none was recorded. */
    public String qualifier(Address address) {
        if (!UNITED_STATES.equals(address.country())) {
            return countryName(address.country());
        }
        return address.region().isBlank() ? USA : address.region();
    }

    /**
     * A country as a reader sees it: the name for an ISO code ({@code "DE"} → {@code "Germany"}), and
     * anything else exactly as it was stored.
     */
    public String countryName(String country) {
        return countries.name(country).orElse(country);
    }

    private String joined(String city, String qualifier) {
        if (qualifier.isBlank()) {
            return city;
        }
        if (city.isBlank()) {
            return qualifier;
        }
        return city + ", " + qualifier;
    }
}
