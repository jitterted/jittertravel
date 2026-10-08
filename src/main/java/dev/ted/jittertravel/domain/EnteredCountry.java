package dev.ted.jittertravel.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The country of a place, and its state where the country picks one from a list — checked on the
 * way <em>in</em>, like {@link EnteredLocation} and for the same reason: reject in the command,
 * never in {@link Address}, whose constructor binds every stored event (docs/LocationDataCleanupPlan.md
 * §4.2). An old booking stored as {@code "United States"} is met only when Ted next edits it.
 *
 * <p><strong>The rules</strong>, each reported under the input that fixes it:
 * <ol>
 *   <li>a country, when there is one, is an ISO code from {@link Countries} — the form offers
 *       nothing else, so this catches a page out of step with the table, or a hand-made request.
 *       A blank country is left to the zone rules, which already say what to do about it;</li>
 *   <li>in the United States, Canada and Australia the region is required, and is one of that
 *       country's own codes ({@link Subdivisions}). Everywhere else it is free text and has no rule.
 *       A train station has no region, so for one this rule does not apply.</li>
 * </ol>
 *
 * <p>An unknown country has no state list, so the two rules never both fire for one place.
 */
public record EnteredCountry(String country, String region, boolean hasRegion) {

    /** A place with nothing to check — a location entered before countries were picked. */
    public static final EnteredCountry NONE = new EnteredCountry("", "", false);

    private static final Countries COUNTRIES = new Countries();
    private static final Subdivisions SUBDIVISIONS = new Subdivisions();

    public EnteredCountry {
        country = country == null ? "" : country.trim();
        region = region == null ? "" : region.trim();
    }

    public static EnteredCountry of(Address address) {
        return address == null ? NONE : new EnteredCountry(address.country(), address.region(), true);
    }

    public static EnteredCountry of(TrainStationAddress station) {
        return new EnteredCountry(station.country(), "", false);
    }

    /** @throws InvalidEnteredLocation carrying every problem found, when there is at least one. */
    public void check(LocationRole role) {
        List<InvalidLocationEntry> problems = problems(role);
        if (!problems.isEmpty()) {
            throw new InvalidEnteredLocation(problems);
        }
    }

    public List<InvalidLocationEntry> problems(LocationRole role) {
        List<InvalidLocationEntry> problems = new ArrayList<>();
        if (!country.isEmpty() && !COUNTRIES.isKnown(country)) {
            problems.add(new InvalidLocationEntry(role, LocationField.COUNTRY, "Unknown country"));
        }
        if (hasRegion && SUBDIVISIONS.required(country)) {
            String countryName = COUNTRIES.name(country).orElse(country);
            String kind = "CA".equalsIgnoreCase(country) ? "province" : "state";
            if (region.isEmpty()) {
                problems.add(new InvalidLocationEntry(role, LocationField.REGION,
                        capitalized(kind) + " required for " + countryName));
            } else if (SUBDIVISIONS.find(country, region).isEmpty()) {
                problems.add(new InvalidLocationEntry(role, LocationField.REGION,
                        "Not a " + kind + " of " + countryName));
            }
        }
        return List.copyOf(problems);
    }

    private static String capitalized(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
