package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringId;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Full details for a single gathering, used to hydrate the edit form.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input controls directly. The
 * list view ({@link PlannedGatheringView}) is what does the pre-formatting; this view is for
 * editing. Mirrors {@link TrainDetailsView}.
 */
public record GatheringDetailsView(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean speaking,
        String infoUrl
) {
}
