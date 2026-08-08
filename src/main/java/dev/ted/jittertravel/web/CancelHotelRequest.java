package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.CancelHotelCommand;
import dev.ted.jittertravel.domain.CancelHotelContext;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBookingId;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * Command record for cancelling a booked hotel stay. A record rather than a form bean: the id comes
 * from the path and the reason from a single request parameter, so the controller builds this
 * directly (mirrors {@link ClearDifferentCityConflict}).
 */
public record CancelHotelRequest(
        UUID hotelBookingId,
        String reason
) implements ImportableCommand {

    public CancelHotelRequest {
        if (reason == null) {
            reason = "";
        }
    }

    @Override
    public UUID commandId() {
        // Fresh id, not the hotelBookingId: the booking id is the aggregate id, and command ids are
        // per-command. Matches ChangeHotelRequest and ClearDifferentCityConflict.
        return UUID.randomUUID();
    }

    @Override
    public Stream<? extends Event> events() {
        // On import the booking is assumed to exist (its booking imported earlier), and the
        // check-in gate is passed a null check-in — there is no event stream to fold here, and
        // IMPORT_BYPASS_INSTANT could not trip the gate anyway. See CancelHotelContext.
        return new CancelHotelCommand(HotelBookingId.of(hotelBookingId), reason)
                .execute(new CancelHotelContext(true, null, IMPORT_BYPASS_INSTANT));
    }
}
