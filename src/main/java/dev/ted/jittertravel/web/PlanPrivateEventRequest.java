package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing record for planning a private event — a dinner with friends, the kind of evening
 * that is {@code Busy} and nothing more to an anonymous viewer.
 * <p>
 * {@code privateEventId} stays a component, unlike the ids on the {@code Change*} forms: it is
 * minted for a new evening and carried in a hidden field, so it is genuinely form data rather than
 * something the path already says.
 */
public record PlanPrivateEventRequest(
        String privateEventId,
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
        @DateTimeFormat(pattern = "HH:mm") LocalTime endTime
) {

    /**
     * The same form under a new id, for re-rendering after a refusal: the attempt that was refused
     * retires its id (H4), so the corrected resubmit is a new command rather than a collision with
     * the row the refusal may have left.
     */
    public PlanPrivateEventRequest withPrivateEventId(String privateEventId) {
        return new PlanPrivateEventRequest(privateEventId, title, venueName, street, city, region,
                postalCode, country, locationForMatching, zone, date, startTime, endTime);
    }

    /**
     * The address the six location fields describe. Derived rather than stored, so the form's
     * fields and the value the write path uses cannot disagree.
     */
    public Address location() {
        return new Address(street, city, region, postalCode, country, locationForMatching);
    }
}
