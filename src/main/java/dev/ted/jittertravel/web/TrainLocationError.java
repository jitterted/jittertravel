package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationRole;
import org.springframework.validation.BindingResult;

/**
 * Puts an {@link InvalidLocationEntry} under the input that caused it. The domain says which end of
 * the trip and which half of the location is wrong; the names of the four inputs are the form's own
 * business, and {@code book-train.html} and {@code change-train.html} share them — which is why this
 * is one place and not a copy in each controller.
 */
class TrainLocationError {

    private final BindingResult bindingResult;

    TrainLocationError(BindingResult bindingResult) {
        this.bindingResult = bindingResult;
    }

    void reject(InvalidLocationEntry invalid) {
        bindingResult.rejectValue(fieldName(invalid), "invalidLocation", invalid.getMessage());
    }

    private String fieldName(InvalidLocationEntry invalid) {
        boolean departure = invalid.role() == LocationRole.DEPARTURE;
        return switch (invalid.field()) {
            case VENUE_NAME -> departure ? "departureStationName" : "arrivalStationName";
            case CITY -> departure ? "departureCityName" : "arrivalCityName";
        };
    }
}
