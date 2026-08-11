package dev.ted.jittertravel.web;

import java.util.UUID;

/**
 * Command record for cancelling a booked hotel stay. A record rather than a form bean: the id comes
 * from the path and the reason from a single request parameter, so the controller builds this
 * directly (mirrors {@link ClearDifferentCityConflict}).
 */
public record CancelHotelRequest(
        UUID hotelBookingId,
        String reason
) {

    public CancelHotelRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
