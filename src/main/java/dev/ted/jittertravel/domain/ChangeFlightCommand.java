package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.ChangeFlightRequest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Changes an existing flight's details. Validation rules (same as booking):
 * <ul>
 *   <li>The flight must already exist ({@link FlightNotFound} otherwise).</li>
 *   <li>Departure date/time must be in the future ({@link DepartureNotInFuture}).</li>
 *   <li>Arrival must be after departure ({@link InvalidDateRange}).</li>
 *   <li>Airport codes must be valid ({@link InvalidAirportCode},
 *       enforced by the {@link AirportCode} value object).</li>
 * </ul>
 * Emits a single {@link FlightChanged} event carrying the full new snapshot.
 */
public class ChangeFlightCommand {

    public Stream<FlightChanged> execute(ChangeFlightRequest dto,
                                         boolean flightExists,
                                         LocalDateTime now) {
        FlightId flightId = FlightId.of(UUID.fromString(dto.getFlightId()));

        if (!flightExists) {
            throw new FlightNotFound("No flight exists with id " + flightId.id());
        }
        if (dto.getDepartureDateTime() == null || !dto.getDepartureDateTime().isAfter(now)) {
            throw new DepartureNotInFuture("Departure date/time must be in the future");
        }
        if (dto.getArrivalDateTime() == null || !dto.getArrivalDateTime().isAfter(dto.getDepartureDateTime())) {
            throw new InvalidDateRange("Arrival date/time must be after departure date/time");
        }

        return Stream.of(new FlightChanged(
                flightId,
                dto.getAirline(),
                dto.getFlightNumber(),
                AirportCode.of(dto.getDepartureAirport()),
                dto.getDepartureDateTime(),
                AirportCode.of(dto.getArrivalAirport()),
                dto.getArrivalDateTime(),
                normalizeReason(dto.getReason())
        ));
    }

    private static String normalizeReason(String reason) {
        if (reason == null) return null;
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
