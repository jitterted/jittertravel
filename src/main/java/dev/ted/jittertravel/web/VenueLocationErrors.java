package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import org.springframework.validation.BindingResult;

/**
 * Puts a refused venue country or state under the input that fixes it, for the four forms that
 * check only those two ({@code EnteredCountry}): plan and change gathering, plan conference and
 * plan private event. The domain names the value; the form names its input — {@code country} on
 * three of them, {@code venueCountry} on the conference form, whose venue fields are prefixed.
 */
class VenueLocationErrors {

    private final BindingResult bindingResult;
    private final String countryInput;
    private final String regionInput;

    VenueLocationErrors(BindingResult bindingResult, String countryInput, String regionInput) {
        this.bindingResult = bindingResult;
        this.countryInput = countryInput;
        this.regionInput = regionInput;
    }

    void reject(InvalidEnteredLocation invalid) {
        invalid.problems().forEach(this::reject);
    }

    private void reject(InvalidLocationEntry problem) {
        bindingResult.rejectValue(input(problem), "invalidLocation", problem.getMessage());
    }

    private String input(InvalidLocationEntry problem) {
        return switch (problem.field()) {
            case COUNTRY -> countryInput;
            case REGION -> regionInput;
            // A venue's name and city are not checked on these forms (CLAUDE.md, "A city that is
            // really a station"), so neither can arrive here.
            case VENUE_NAME, CITY -> throw new IllegalStateException(
                    "A venue's name and city are not checked: " + problem.getMessage());
        };
    }
}
