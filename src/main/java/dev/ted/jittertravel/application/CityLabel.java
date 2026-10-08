package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Countries;
import dev.ted.jittertravel.domain.UsStates;

/**
 * Writes where a place is as one short line — {@code "Denver, CO"} in the United States,
 * {@code "Vienna, Austria"} everywhere else (Ted, 2026-10-06). Presentation-layer, shared by every
 * projector and renderer that shows a city, so the rule lives in one place.
 * <p>
 * The part after the city is the <em>qualifier</em>: a US state's postal code, or the country.
 * Outside the US the region is never shown — stored regions there are as often a borough
 * ({@code "Westminster Borough"}) as a province. In the US:
 * <ul>
 *   <li>a recognised state, by name or code, becomes its code: {@code "Colorado"} → {@code "CO"};</li>
 *   <li>no state at all falls back to {@code "USA"}, however the country was spelled;</li>
 *   <li>an unrecognised one is shown as typed — for the owner. The {@linkplain #publicLabel public}
 *       form falls back to {@code "USA"} instead: a US region that is not a state is something
 *       else Ted typed, like a neighbourhood, and the redaction rules publish only what is named.</li>
 * </ul>
 */
public class CityLabel {

    private static final String USA = "USA";

    private final UsStates usStates = new UsStates();
    private final Countries countries = new Countries();

    /** "City, Qualifier", or the city alone when there is no qualifier. */
    public String label(Address address) {
        return joined(address.city(), qualifier(address));
    }

    /** The state code or country that follows the city, or {@code ""} when none was recorded. */
    public String qualifier(Address address) {
        if (!usStates.isUnitedStates(address.country())) {
            return countryName(address.country());
        }
        if (address.region().isBlank()) {
            return USA;
        }
        return usStates.stateCode(address.region()).orElse(address.region());
    }

    /** As {@link #label}, but a US region that is not a state is never published. */
    public String publicLabel(Address address) {
        if (!usStates.isUnitedStates(address.country())) {
            return label(address);
        }
        return joined(address.city(), usStates.stateCode(address.region()).orElse(USA));
    }

    /**
     * A country as a reader sees it: the name for a picked ISO code ({@code "DE"} → {@code "Germany"}),
     * and anything else — a name stored before countries were picked — exactly as it was stored.
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
