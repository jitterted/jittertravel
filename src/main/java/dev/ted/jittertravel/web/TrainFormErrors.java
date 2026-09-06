package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
import org.springframework.validation.BindingResult;

/**
 * Puts everything a rejected train form knows under the inputs that fix it. The domain says which
 * end of the trip and which value is wrong; the names of the inputs and the words shown to the
 * reader are the form's own business, and {@code book-train.html} and {@code change-train.html}
 * share both — which is why this is one place and not a copy in each controller.
 *
 * <p><strong>Nothing here is a global error except the count.</strong> The form has two identical
 * fieldsets side by side, so a banner saying "a station" could not be resolved names neither of
 * them and points the reader at four inputs, two of which are fine. Position answers "which end?"
 * for free, so the message goes under the input; the banner is left saying only how many there are,
 * which is the one thing position cannot say when a problem is below the fold.
 *
 * <p><strong>Where each message lands is chosen by what fixes it</strong>, not by what failed. A
 * blank country is repaired by typing a country, so it lands on the country input. A country no
 * zone follows from cannot be repaired by retyping it — the curated table is what it is — so that
 * one lands on the time-zone select, which is the way through.
 */
class TrainFormErrors {

    private final BindingResult bindingResult;

    TrainFormErrors(BindingResult bindingResult) {
        this.bindingResult = bindingResult;
    }

    void reject(InvalidTrainEntry invalid) {
        invalid.locations().forEach(this::reject);
        invalid.zones().forEach(this::reject);
    }

    private void reject(InvalidLocationEntry invalid) {
        bindingResult.rejectValue(locationField(invalid), "invalidLocation", invalid.getMessage());
    }

    private void reject(UnresolvedStationZone unresolved) {
        bindingResult.rejectValue(zoneField(unresolved), "zoneUnresolved", message(unresolved));
    }

    /**
     * The one thing said at the top of the form: how many inputs are marked below. It is a count
     * and not a description on purpose — a reader who can see the marked field does not need it,
     * and a reader who cannot needs to know the submit failed and that there is more than one.
     *
     * <p>Counts field errors only. A whole-form failure (the trip vanished between GET and POST)
     * is already its own global message and is not "something to fix below".
     */
    void summarize() {
        int count = bindingResult.getFieldErrorCount();
        if (count == 0) {
            return;
        }
        bindingResult.reject("problemCount",
                count == 1 ? "1 thing to fix below." : count + " things to fix below.");
    }

    private String locationField(InvalidLocationEntry invalid) {
        boolean departure = invalid.role() == LocationRole.DEPARTURE;
        return switch (invalid.field()) {
            case VENUE_NAME -> departure ? "departureStationName" : "arrivalStationName";
            case CITY -> departure ? "departureCityName" : "arrivalCityName";
        };
    }

    private String zoneField(UnresolvedStationZone unresolved) {
        boolean departure = unresolved.role() == LocationRole.DEPARTURE;
        return switch (unresolved.cause()) {
            case COUNTRY_MISSING -> departure ? "departureCountry" : "arrivalCountry";
            case COUNTRY_UNRECOGNISED -> departure ? "departureZone" : "arrivalZone";
        };
    }

    /**
     * Terse, like every field message on this form: it sits in a narrow column under the input it
     * is about, which has already said which field and which end of the trip. Neither carries an
     * apostrophe — these render through Thymeleaf's escaping, where {@code Can't} arrives as
     * {@code Can&#39;t} and every assertion about the markup then has to know it.
     */
    private String message(UnresolvedStationZone unresolved) {
        return switch (unresolved.cause()) {
            // Not "Country is required": leaving it blank and picking a zone is a legitimate way
            // through, and a message that says otherwise is wrong about its own form.
            case COUNTRY_MISSING -> "Country or time zone required";
            case COUNTRY_UNRECOGNISED -> "Unknown country — pick a zone";
        };
    }
}
