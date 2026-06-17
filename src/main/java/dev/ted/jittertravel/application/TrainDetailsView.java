package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;

import java.time.LocalDateTime;

/**
 * Full details for a single train trip, used to hydrate the edit form.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input controls directly.
 * The list view ({@link BookedTrainView}) is what does the pre-formatting; this view is for
 * editing. Mirrors {@link FlightDetailsView}.
 */
public record TrainDetailsView(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        LocalDateTime departureDateTime,
        TrainStationAddress arrivalStation,
        LocalDateTime arrivalDateTime,
        String serviceId
) {
}