package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing record for planning a gathering — a meetup, a user group, anything whose venue and
 * time are public by decision.
 * <p>
 * {@code gatheringId} stays a component: it is minted for a new gathering and carried in a hidden
 * field, so it is form data rather than something the path already says.
 */
public record PlanGatheringRequest(
        String gatheringId,
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
     * The same form under a new id, for re-rendering after a refusal: the attempt that was refused
     * retires its id (H4), so the corrected resubmit is a new command rather than a collision with
     * the row the refusal may have left.
     */
    public PlanGatheringRequest withGatheringId(String gatheringId) {
        return new PlanGatheringRequest(gatheringId, title, venueName, street, city, region,
                postalCode, country, locationForMatching, zone, date, startTime, endTime, speaking,
                infoUrl);
    }

    /**
     * {@code speaking} is boxed, and that is load-bearing rather than style. An unchecked checkbox
     * submits <em>nothing</em>, and constructor binding cannot pass {@code null} to a primitive, so
     * a {@code boolean} component turns "Ted is not speaking" into a binding error on the field —
     * which is what RequiredEntryConventionTest caught the moment this record landed. A mutable
     * bean never had the problem because an absent value simply left the field {@code false}.
     * Boxed and defaulted here, the absent case means the same thing it always did.
     */
    public PlanGatheringRequest {
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
