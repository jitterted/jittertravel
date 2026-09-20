package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for changing a booked train trip.
 * <p>
 * <strong>The id is not here.</strong> Which trip is being changed is path data — nothing on the
 * page submits it, and a hidden field holding it would let a submit re-target another trip. The
 * controller reads it from the path and hands it to the application service alongside this request.
 * Compare {@link BookTrainRequest}, whose id is minted for a new trip and so genuinely is form data.
 */
public record ChangeTrainRequest(
        String serviceId,
        String departureStationName,
        String departureCityName,
        String departureCountry,
        String departureMapsUrl,
        String departureZone,
        // @DateTimeFormat required to match browser's <input type="datetime-local" /> format
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime departureDateTime,
        String arrivalStationName,
        String arrivalCityName,
        String arrivalCountry,
        String arrivalMapsUrl,
        String arrivalZone,
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime arrivalDateTime
) {
}
