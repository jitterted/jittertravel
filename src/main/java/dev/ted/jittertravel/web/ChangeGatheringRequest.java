package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing record for changing a planned gathering.
 * <p>
 * <strong>The id is not here.</strong> Which gathering is being changed is path data — nothing on
 * the page submits it, and a hidden field holding it would let a submit re-target another
 * gathering. The controller reads it from the path and hands it to the application service
 * alongside this request.
 */
public record ChangeGatheringRequest(
        String title,
        String venueName,
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        String locationForMatching,
        String zone,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
        @DateTimeFormat(pattern = "HH:mm") LocalTime endTime,
        Boolean speaking,
        String infoUrl
) {

    /**
     * {@code speaking} is boxed for the same reason {@link PlanGatheringRequest}'s is: an unchecked
     * checkbox submits nothing, and constructor binding cannot pass {@code null} to a primitive, so
     * a {@code boolean} component would turn "Ted is not speaking" into a binding error.
     */
    public ChangeGatheringRequest {
        speaking = speaking != null && speaking;
    }

    /**
     * The address the six location fields describe. Derived rather than stored, so the form's
     * fields and the value the write path uses cannot disagree.
     */
    public Address location() {
        return new Address(street, city, region, postalCode, country, locationForMatching);
    }
}
