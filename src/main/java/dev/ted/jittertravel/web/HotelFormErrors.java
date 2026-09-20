package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import org.springframework.validation.BindingResult;

/**
 * Puts everything a rejected hotel form knows under the inputs that fix it, and counts them at the
 * top. The domain says which value is wrong; the names of the inputs are the form's own business,
 * and {@code book-hotel.html} and {@code change-hotel.html} share them — which is why this is one
 * place and not the ternary each controller used to carry.
 *
 * <p><strong>A stay has one location, so the field the domain names maps straight onto an input</strong>,
 * with none of the departure/arrival branching {@link TrainFormErrors} needs. What it does share is
 * the count, which lives on {@link FormErrors} so the sentence has one home.
 *
 * <p>Every problem the stay has is rejected, not the first: {@link InvalidEnteredLocation} carries
 * at most one entry per field, so a blank name and a blank city mark both inputs in one response
 * and neither {@code <span class="error">} collects two messages.
 */
class HotelFormErrors extends FormErrors {

    HotelFormErrors(BindingResult bindingResult) {
        super(bindingResult);
    }

    void reject(InvalidEnteredLocation invalid) {
        invalid.problems().forEach(this::reject);
    }

    private void reject(InvalidLocationEntry problem) {
        bindingResult.rejectValue(locationField(problem), "invalidLocation", problem.getMessage());
    }

    private String locationField(InvalidLocationEntry problem) {
        return switch (problem.field()) {
            case VENUE_NAME -> "hotelName";
            case CITY -> "city";
        };
    }
}
