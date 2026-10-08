package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlacesUsedProjector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the {@link PlacePicker} on every form that collects a country, so
 * {@code fragments/place-picker.html} has its lists without each controller fetching them.
 *
 * <p>The projector is optional for the same reason it is in {@link ProblemContextAdvice}: a
 * {@code @WebMvcTest} slice has no projector bean, and a form there still renders, offering every
 * country behind "Another country…" and nothing first.
 */
@ControllerAdvice(assignableTypes = {
        BookHotelController.class,
        ChangeHotelController.class,
        BookTrainController.class,
        ChangeTrainController.class,
        PlanGatheringController.class,
        ChangeGatheringController.class,
        PlanConferenceController.class,
        PlanPrivateEventController.class})
public class PlacePickerAdvice {

    private final PlacesUsedProjector placesUsed;

    public PlacePickerAdvice(ObjectProvider<PlacesUsedProjector> placesUsed) {
        this.placesUsed = placesUsed.getIfAvailable();
    }

    @ModelAttribute("placePicker")
    public PlacePicker placePicker() {
        if (placesUsed == null) {
            return PlacePicker.withNothingUsed();
        }
        return new PlacePicker(placesUsed.countries(), placesUsed::subdivisions);
    }
}
