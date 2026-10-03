package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a whole flight itinerary. The id comes from the path and the reason
 * from a single request parameter, so the controller builds this directly (mirrors
 * {@link CancelFlightRequest}).
 */
public record CancelFlightItineraryRequest(
        UUID itineraryId,
        String reason
) {

    public CancelFlightItineraryRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
