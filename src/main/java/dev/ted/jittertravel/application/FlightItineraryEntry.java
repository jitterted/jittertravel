package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

public record FlightItineraryEntry(
        FlightDayRole role,
        String airline,
        String flightNumber,
        String departureAirportCode,
        LocalDateTime departureDateTime,
        String arrivalAirportCode,
        LocalDateTime arrivalDateTime
) implements ItineraryEntry {
    @Override public EntryKind kind() { return EntryKind.FLIGHT; }
    @Override public LocalDateTime anchorTime() {
        return role == FlightDayRole.ARRIVAL ? arrivalDateTime : departureDateTime;
    }
}
