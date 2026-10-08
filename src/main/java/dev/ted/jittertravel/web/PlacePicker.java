package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Countries;
import dev.ted.jittertravel.domain.Country;
import dev.ted.jittertravel.domain.Subdivision;
import dev.ted.jittertravel.domain.Subdivisions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * What the address forms' Country and State pickers offer (docs/LocationDataCleanupPlan.md D1, D3):
 * the countries Ted has used first, every country behind "Another country…", and for the US,
 * Canada and Australia a list of states — the ones he has used first — that stores the code.
 * Read by {@code fragments/place-picker.html}; built per request by {@link PlacePickerAdvice}.
 */
public class PlacePicker {

    private static final Countries COUNTRIES = new Countries();
    private static final Subdivisions SUBDIVISIONS = new Subdivisions();
    private static final List<String> STATE_LIST_COUNTRIES = List.of("US", "CA", "AU");

    private final List<Country> usedCountries;
    private final Function<String, List<Subdivision>> usedStates;

    public PlacePicker(List<Country> usedCountries, Function<String, List<Subdivision>> usedStates) {
        this.usedCountries = List.copyOf(usedCountries);
        this.usedStates = usedStates;
    }

    /** No history to draw on: every country is still offered, behind "Another country…". */
    public static PlacePicker withNothingUsed() {
        return new PlacePicker(List.of(), country -> List.of());
    }

    public List<Country> usedCountries() {
        return usedCountries;
    }

    public List<Country> allCountries() {
        return COUNTRIES.all();
    }

    public boolean isUsed(String code) {
        return usedCountries.stream().anyMatch(country -> country.code().equalsIgnoreCase(trimmed(code)));
    }

    public boolean isKnown(String code) {
        return COUNTRIES.isKnown(code);
    }

    /** The country's name, or the stored value itself when it is not a code. */
    public String countryName(String code) {
        return COUNTRIES.name(code).orElse(trimmed(code));
    }

    public boolean hasStateList(String countryCode) {
        return SUBDIVISIONS.required(countryCode);
    }

    public String stateLabel(String countryCode) {
        return "CA".equalsIgnoreCase(trimmed(countryCode)) ? "Province" : "State";
    }

    public List<Subdivision> states(String countryCode) {
        return SUBDIVISIONS.of(countryCode);
    }

    public List<Subdivision> usedStates(String countryCode) {
        return usedStates.apply(trimmed(countryCode).toUpperCase(Locale.ROOT));
    }

    public boolean isStateOf(String countryCode, String region) {
        return SUBDIVISIONS.find(countryCode, region).isPresent();
    }

    public boolean isUsedState(String countryCode, String region) {
        return usedStates(countryCode).stream()
                                      .anyMatch(state -> state.code().equalsIgnoreCase(trimmed(region)));
    }

    /**
     * The state lists for the page script, which switches Region between a list and a text box
     * when Country changes: {@code {"US": {"label": "State", "name": "United States",
     * "used": [["CO", "Colorado"]], "all": [["AL", "Alabama"], …]}, …}}. Plain maps and lists of
     * strings, so Thymeleaf's JavaScript inlining writes them without a serialization library.
     */
    public Map<String, Map<String, Object>> stateListsForScript() {
        Map<String, Map<String, Object>> lists = new LinkedHashMap<>();
        for (String country : STATE_LIST_COUNTRIES) {
            Map<String, Object> list = new LinkedHashMap<>();
            list.put("label", stateLabel(country));
            list.put("name", countryName(country));
            list.put("used", pairs(usedStates(country)));
            list.put("all", pairs(states(country)));
            lists.put(country, list);
        }
        return lists;
    }

    private static List<List<String>> pairs(List<Subdivision> subdivisions) {
        return subdivisions.stream()
                           .map(subdivision -> List.of(subdivision.code(), subdivision.name()))
                           .toList();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
