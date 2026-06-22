package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 */
public record BookedFlightView(
        FlightId flightId,
        String airline,
        String flightNumber,
        String route,
        LocalDateTime departureDateTime,
        String departureDateTimeDisplay,
        String arrivalDateTimeDisplay,
        List<ChangeEntry> history
) implements TemporalView {

    /**
     * A flight is "upcoming" until it departs. STOPGAP: flight events still store
     * bare wall-clock times, so the departure is interpreted in the server zone to
     * preserve pre-migration behavior. Once FlightBooked/FlightChanged carry a
     * {@code ZonedTimestamp}, return its {@code utc()} directly (see
     * {@link TemporalView}).
     */
    @Override
    public Instant relevantUntil() {
        return departureDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** True when there is at least one change beyond the original booking. */
    public boolean hasChanges() {
        return history.size() > 1;
    }

    /** Most recent change's display text; only meaningful when {@link #hasChanges()}. */
    public String latestChangeDisplay() {
        return history.getLast().displayText();
    }
}
