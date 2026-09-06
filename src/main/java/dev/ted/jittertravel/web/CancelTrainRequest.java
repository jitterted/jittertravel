package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a booked train trip. A record rather than a form bean: the id comes
 * from the path and the reason from a single request parameter, so the controller builds this
 * directly (mirrors {@link CancelPrivateEventRequest} and {@link CancelHotelRequest}).
 */
public record CancelTrainRequest(
        UUID tripId,
        String reason
) {

    public CancelTrainRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
