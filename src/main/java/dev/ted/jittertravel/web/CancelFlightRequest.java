package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a booked flight. The id comes from the path and the reason from a
 * single request parameter, so the controller builds this directly (mirrors
 * {@link CancelTrainRequest}).
 */
public record CancelFlightRequest(
        UUID flightId,
        String reason
) {

    public CancelFlightRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
