package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for declining a planned conference. A record rather than a form bean: the id comes
 * from the path and the reason from a single request parameter, so the controller builds this
 * directly (mirrors {@link CancelHotelRequest}).
 */
public record DeclineConferenceRequest(
        UUID conferenceId,
        String reason
) {

    public DeclineConferenceRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
