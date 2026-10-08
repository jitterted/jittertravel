package dev.ted.jittertravel.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The states, provinces and territories of the three countries whose region is picked from a list
 * rather than typed: the United States, Canada and Australia (Ted, 2026-10-06; see
 * docs/LocationDataCleanupPlan.md D3). In those three the region is required and stored as its
 * postal code — the US because a page shows it ("Denver, CO"), Canada and Australia because their
 * time zone depends on it. Everywhere else the region stays free text and this table has no say.
 *
 * <p>A curated in-memory table. Codes repeat across countries (Washington and Western Australia are
 * both {@code WA}), so a code means nothing without its country.
 */
public class Subdivisions {

    private static final Map<String, List<Subdivision>> BY_COUNTRY = Map.of(
            "US", list(
                    "AL", "Alabama", "AK", "Alaska", "AZ", "Arizona", "AR", "Arkansas",
                    "CA", "California", "CO", "Colorado", "CT", "Connecticut", "DE", "Delaware",
                    "DC", "District of Columbia", "FL", "Florida", "GA", "Georgia", "HI", "Hawaii",
                    "ID", "Idaho", "IL", "Illinois", "IN", "Indiana", "IA", "Iowa", "KS", "Kansas",
                    "KY", "Kentucky", "LA", "Louisiana", "ME", "Maine", "MD", "Maryland",
                    "MA", "Massachusetts", "MI", "Michigan", "MN", "Minnesota",
                    "MS", "Mississippi", "MO", "Missouri", "MT", "Montana", "NE", "Nebraska",
                    "NV", "Nevada", "NH", "New Hampshire", "NJ", "New Jersey", "NM", "New Mexico",
                    "NY", "New York", "NC", "North Carolina", "ND", "North Dakota", "OH", "Ohio",
                    "OK", "Oklahoma", "OR", "Oregon", "PA", "Pennsylvania", "RI", "Rhode Island",
                    "SC", "South Carolina", "SD", "South Dakota", "TN", "Tennessee", "TX", "Texas",
                    "UT", "Utah", "VT", "Vermont", "VA", "Virginia", "WA", "Washington",
                    "WV", "West Virginia", "WI", "Wisconsin", "WY", "Wyoming"),
            "CA", list(
                    "AB", "Alberta", "BC", "British Columbia", "MB", "Manitoba",
                    "NB", "New Brunswick", "NL", "Newfoundland and Labrador", "NS", "Nova Scotia",
                    "NT", "Northwest Territories", "NU", "Nunavut", "ON", "Ontario",
                    "PE", "Prince Edward Island", "QC", "Quebec", "SK", "Saskatchewan",
                    "YT", "Yukon"),
            "AU", list(
                    "ACT", "Australian Capital Territory", "NSW", "New South Wales",
                    "NT", "Northern Territory", "QLD", "Queensland", "SA", "South Australia",
                    "TAS", "Tasmania", "VIC", "Victoria", "WA", "Western Australia"));

    /** Whether a place in this country must name one of its subdivisions as its region. */
    public boolean required(String countryCode) {
        return BY_COUNTRY.containsKey(normalized(countryCode));
    }

    /** The country's subdivisions, A to Z by name; empty for a country whose region is free text. */
    public List<Subdivision> of(String countryCode) {
        return BY_COUNTRY.getOrDefault(normalized(countryCode), List.of());
    }

    /** The subdivision a code names in this country; empty when it names none there. */
    public Optional<Subdivision> find(String countryCode, String code) {
        String wanted = normalized(code);
        return of(countryCode).stream()
                              .filter(subdivision -> subdivision.code().equals(wanted))
                              .findFirst();
    }

    /** The subdivision this country spells with exactly this name, ignoring case. */
    public Optional<Subdivision> findByName(String countryCode, String name) {
        String wanted = name == null ? "" : name.trim();
        return of(countryCode).stream()
                              .filter(subdivision -> subdivision.name().equalsIgnoreCase(wanted))
                              .findFirst();
    }

    private static String normalized(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static List<Subdivision> list(String... codesAndNames) {
        List<Subdivision> subdivisions = new ArrayList<>();
        for (int i = 0; i < codesAndNames.length; i += 2) {
            subdivisions.add(new Subdivision(codesAndNames[i], codesAndNames[i + 1]));
        }
        return List.copyOf(subdivisions);
    }
}
