package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * Full details for a single train trip, used to hydrate the edit form and to resolve a
 * {@code train:} ground-transfer token.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input controls directly.
 * The list view ({@link BookedTrainView}) is what does the pre-formatting; this view is for
 * editing. Mirrors {@link FlightDetailsView}.
 * <p>
 * <strong>The moments keep their zone.</strong> This view used to drop it, calling
 * {@code localDateTime()} in the projector — harmless for a {@code datetime-local} input, which
 * reads the wall clock and nothing else, but it is the same loss that makes the hotel path
 * re-derive a zone it already had. {@link GroundTransferEndpointResolver} reads the zone from here,
 * so a station endpoint is stamped with the zone its own booking resolved (D5); the edit form calls
 * {@code localDateTime()} at the point it binds, which is where that narrowing belongs.
 */
public record TrainDetailsView(
        TrainTripId tripId,
        TrainStationAddress departureStation,
        ZonedTimestamp departureDateTime,
        TrainStationAddress arrivalStation,
        ZonedTimestamp arrivalDateTime,
        String serviceId
) {
}