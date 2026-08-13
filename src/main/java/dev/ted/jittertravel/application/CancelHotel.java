package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelHotelCommand;
import dev.ted.jittertravel.domain.CancelHotelContext;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelHotelRequest;

import java.util.UUID;

/**
 * Cancels a booked hotel stay.
 * <p>
 * Unlike {@link ChangeHotel} and {@link ChangeFlight}, this folds its one decision fact from the
 * authoritative event stream rather than reading a projector, as R1 in
 * {@code EventSourcingRulesHeuristics.md} requires. (Bringing the two Change services into line is
 * tracked separately in {@code docs/Backlog.md}.)
 * <p>
 * commandId is captured at the boundary and passed in; this service does no clock or UUID I/O of
 * its own. There is no {@code now} because cancelling is not time-gated.
 */
public class CancelHotel {
    private final CommandExecutor commandExecutor;

    public CancelHotel(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelHotel(UUID commandId, CancelHotelRequest request) {
        HotelBookingId hotelBookingId = HotelBookingId.of(request.hotelBookingId());
        CancelHotelCommand command = new CancelHotelCommand(hotelBookingId, request.reason());
        commandExecutor.execute(commandId, request, contextFor(hotelBookingId), command);
    }

    /**
     * Folds whether the booking is live from the event stream. A cancellation clears the fact, so a
     * second cancel of the same booking is refused as not-found rather than silently emitting a
     * duplicate event.
     */
    private CancelHotelContext contextFor(HotelBookingId hotelBookingId) {
        boolean exists = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(false,
                        (current, event) -> stillBooked(current, hotelBookingId, event),
                        (first, second) -> second);
        return new CancelHotelContext(exists);
    }

    private boolean stillBooked(boolean current, HotelBookingId wanted, Object event) {
        return switch (event) {
            case HotelBooked e when e.hotelBookingId().equals(wanted) -> true;
            case HotelChanged e when e.hotelBookingId().equals(wanted) -> true;
            case HotelBookingCancelled e when e.hotelBookingId().equals(wanted) -> false;
            default -> current;
        };
    }
}
