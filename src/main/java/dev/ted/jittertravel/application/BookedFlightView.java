package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;

import java.time.LocalDateTime;

/**
 * Row in the "Booked Flights" list, pre-formatted for display.
 * <p>
 * Holds {@code flightId} so the UI can navigate to the (future) edit screen,
 * and {@code departureDateTime} so the projector can sort entries; the
 * {@code departureDateTimeDisplay} string is what the template renders.
 */
public record BookedFlightView(
        FlightId flightId,
        String airline,
        String flightNumber,
        String route,
        LocalDateTime departureDateTime,
        String departureDateTimeDisplay
) {
}
