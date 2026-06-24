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
 */
public record BookedFlightView(
        FlightId flightId,
        String airline,
        String flightNumber,
        String route,
        ZonedTimestamp departureDateTime,
        ZonedTimestamp arrivalDateTime,
        List<ChangeEntry> history
) implements TemporalView {

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
}
