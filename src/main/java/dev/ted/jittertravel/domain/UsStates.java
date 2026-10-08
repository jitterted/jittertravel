package dev.ted.jittertravel.domain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which country fields name the United States, and the postal code of each state. A curated table
 * of facts about the world, in the same spirit as {@link LocationZoneResolver}: stored data spells
 * the country both {@code "USA"} and {@code "United States"}, and the state both {@code "CO"} and
 * {@code "Colorado"}, so both spellings are keys and matching ignores case and surrounding space.
 */
public class UsStates {

    private static final Map<String, String> CODE_BY_KEY = codeByKey();

    private static final Set<String> COUNTRY_KEYS = Set.of(
            "usa", "us", "u.s.", "u.s.a.", "united states", "united states of america");

    public boolean isUnitedStates(String country) {
        return COUNTRY_KEYS.contains(normalized(country));
    }

    /** The two-letter postal code for a state given by name or code; empty when it is neither. */
    public Optional<String> stateCode(String region) {
        return Optional.ofNullable(CODE_BY_KEY.get(normalized(region)));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> codeByKey() {
        String[][] states = {
                {"AL", "Alabama"}, {"AK", "Alaska"}, {"AZ", "Arizona"}, {"AR", "Arkansas"},
                {"CA", "California"}, {"CO", "Colorado"}, {"CT", "Connecticut"},
                {"DE", "Delaware"}, {"DC", "District of Columbia"}, {"FL", "Florida"},
                {"GA", "Georgia"}, {"HI", "Hawaii"}, {"ID", "Idaho"}, {"IL", "Illinois"},
                {"IN", "Indiana"}, {"IA", "Iowa"}, {"KS", "Kansas"}, {"KY", "Kentucky"},
                {"LA", "Louisiana"}, {"ME", "Maine"}, {"MD", "Maryland"},
                {"MA", "Massachusetts"}, {"MI", "Michigan"}, {"MN", "Minnesota"},
                {"MS", "Mississippi"}, {"MO", "Missouri"}, {"MT", "Montana"},
                {"NE", "Nebraska"}, {"NV", "Nevada"}, {"NH", "New Hampshire"},
                {"NJ", "New Jersey"}, {"NM", "New Mexico"}, {"NY", "New York"},
                {"NC", "North Carolina"}, {"ND", "North Dakota"}, {"OH", "Ohio"},
                {"OK", "Oklahoma"}, {"OR", "Oregon"}, {"PA", "Pennsylvania"},
                {"RI", "Rhode Island"}, {"SC", "South Carolina"}, {"SD", "South Dakota"},
                {"TN", "Tennessee"}, {"TX", "Texas"}, {"UT", "Utah"}, {"VT", "Vermont"},
                {"VA", "Virginia"}, {"WA", "Washington"}, {"WV", "West Virginia"},
                {"WI", "Wisconsin"}, {"WY", "Wyoming"}
        };
        Map<String, String> table = new HashMap<>();
        for (String[] state : states) {
            table.put(normalized(state[0]), state[0]);
            table.put(normalized(state[1]), state[0]);
        }
        return Map.copyOf(table);
    }
}
