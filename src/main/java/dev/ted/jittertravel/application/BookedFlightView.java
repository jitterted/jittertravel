package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.Instant;
import java.util.List;

/**
 * Row in the "Booked Flights" list, pre-formatted for display.
 * <p>
 * Holds {@code flightId} so the UI can navigate to the edit screen,
 * {@code departureDateTime} so the projector can sort entries, and the
 * full {@link ChangeEntry} {@code history} for inline expansion. The
 * history always contains at least the initial booking entry; if no
 * {@code FlightChanged} events have occurred, {@link #hasChanges()}
 * returns {@code false}.
 * <p>
 * {@code cancelled} marks a flight kept as a record behind {@code ?cancelled=show}; it carries the
 * cancellation's {@code cancellationReason} ({@code ""} when none) and {@code cancelledOn}
 * ({@code null} while the flight is live). Unlike every other flight read model, this one keeps a
 * cancelled flight — see {@code FlightCancelled}.
 */
public record BookedFlightView(
        FlightId flightId,
        String airline,
        String flightNumber,
        String route,
        ZonedTimestamp departureDateTime,
        ZonedTimestamp arrivalDateTime,
        List<ChangeEntry> history,
        boolean cancelled,
        String cancellationReason,
        Instant cancelledOn
) implements TemporalView {

    public BookedFlightView {
        if (cancellationReason == null) {
            cancellationReason = "";
        }
    }

    /** A live flight: not cancelled. */
    public BookedFlightView(FlightId flightId, String airline, String flightNumber, String route,
                            ZonedTimestamp departureDateTime, ZonedTimestamp arrivalDateTime,
                            List<ChangeEntry> history) {
        this(flightId, airline, flightNumber, route, departureDateTime, arrivalDateTime, history,
             false, "", null);
    }

    @Override
    public Instant relevantUntil() {
        return departureDateTime.utc();
    }

    /** True when there is at least one change beyond the original booking. */
    public boolean hasChanges() {
        return history.size() > 1;
    }

    /** Most recent change's display text; only meaningful when {@link #hasChanges()}. */
    public String latestChangeDisplay() {
        return history.getLast().displayText();
    }

    /** A copy marked cancelled, carrying what the cancellation recorded. */
    BookedFlightView cancelledWith(String reason, Instant on) {
        return new BookedFlightView(flightId, airline, flightNumber, route, departureDateTime,
                arrivalDateTime, history, true, reason, on);
    }
}
