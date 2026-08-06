package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.time.LocalDateTime;

public record FlightItineraryEntry(
        FlightId flightId,
        FlightDayRole role,
        String airline,
        String flightNumber,
        String departureAirportCode,
        ZonedTimestamp departureDateTime,
        String arrivalAirportCode,
        ZonedTimestamp arrivalDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.FLIGHT; }
    @Override public LocalDateTime anchorTime() {
        return anchor().localDateTime();
    }

    /** The endpoint this entry is filed under; each end keeps its own airport zone. */
    public ZonedTimestamp anchor() {
        return role == FlightDayRole.ARRIVAL ? arrivalDateTime : departureDateTime;
    }
}
