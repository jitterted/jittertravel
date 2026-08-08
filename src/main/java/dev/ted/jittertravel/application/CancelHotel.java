package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.CancelHotelCommand;
import dev.ted.jittertravel.domain.CancelHotelContext;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelHotelRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Cancels a booked hotel stay.
 * <p>
 * Unlike {@link ChangeHotel} and {@link ChangeFlight}, this folds its decision facts from the
 * authoritative event stream rather than reading a projector. R1 in
 * {@code EventSourcingRulesHeuristics.md} requires it, and the stakes are higher here than for an
 * existence check: cancelling is gated on check-in, so deciding from a read model that might be
 * stale or mid-replay could let a stay be cancelled after arrival. (Bringing the two Change
 * services into line is tracked separately in {@code docs/Backlog.md}.)
 * <p>
 * commandId and now are captured at the boundary and passed in; this service does no clock or UUID
 * I/O of its own.
 */
public class CancelHotel {
    private final CommandExecutor commandExecutor;

    public CancelHotel(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public void cancelHotel(UUID commandId, CancelHotelRequest request, Instant now) {
        HotelBookingId hotelBookingId = HotelBookingId.of(request.hotelBookingId());
        CancelHotelCommand command = new CancelHotelCommand(hotelBookingId, request.reason());
        commandExecutor.execute(commandId, request, contextFor(hotelBookingId, now), command);
    }

    /**
     * Folds the booking's current state from the event stream. A cancellation clears both facts, so
     * a second cancel of the same booking is refused as not-found rather than silently emitting a
     * duplicate event.
     */
    private CancelHotelContext contextFor(HotelBookingId hotelBookingId, Instant now) {
        BookingState state = commandExecutor.eventsForDecision()
                .map(StoredEvent::payload)
                .reduce(BookingState.NONE,
                        (current, event) -> current.apply(hotelBookingId, event),
                        (first, second) -> second);
        return new CancelHotelContext(state.exists(), state.checkIn(), now);
    }

    /**
     * The slice of hotel state a cancellation decision needs. {@code checkIn} tracks
     * {@link HotelChanged} because an edit can move it, which changes whether the stay is still
     * cancellable.
     */
    private record BookingState(boolean exists, ZonedTimestamp checkIn) {
        static final BookingState NONE = new BookingState(false, null);

        BookingState apply(HotelBookingId wanted, Object event) {
            return switch (event) {
                case HotelBooked e when e.hotelBookingId().equals(wanted) ->
                        new BookingState(true, e.checkIn());
                case HotelChanged e when e.hotelBookingId().equals(wanted) ->
                        new BookingState(true, e.checkIn());
                case HotelBookingCancelled e when e.hotelBookingId().equals(wanted) -> NONE;
                default -> this;
            };
        }
    }
}
