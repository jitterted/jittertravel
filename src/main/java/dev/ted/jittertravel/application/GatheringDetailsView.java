package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * Full details for a single gathering, used to hydrate the edit form.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input controls directly. The
 * list view ({@link PlannedGatheringView}) is what does the pre-formatting; this view is for
 * editing. Mirrors {@link TrainDetailsView}.
 * <p>
 * The form's date/start/end inputs are filled from the venue-zone wall-clock
 * ({@link ZonedTimestamp#localDateTime()}), so re-opening an edit form shows exactly what was
 * entered rather than a time shifted into the server's zone.
 */
public record GatheringDetailsView(
        GatheringId gatheringId,
        String title,
        String venueName,
        Address location,
        ZonedTimestamp startsAt,
        ZonedTimestamp endsAt,
        boolean speaking,
        String infoUrl
) {
}
