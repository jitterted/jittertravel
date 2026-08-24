package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a planned private social event. A record rather than a form bean:
 * the id comes from the path and the reason from a single request parameter, so the controller
 * builds this directly (mirrors {@link CancelHotelRequest}).
 */
public record CancelPrivateEventRequest(
        UUID privateEventId,
        String reason
) {

    public CancelPrivateEventRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
