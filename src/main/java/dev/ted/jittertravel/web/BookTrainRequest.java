package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for booking a train trip.
 * <p>
 * {@code trainTripId} stays a component: it is minted for a new trip and carried in a hidden field,
 * so it is form data rather than something the path already says. Compare
 * {@link ChangeTrainRequest}, whose id is path data and is therefore not on the request at all.
 */
public record BookTrainRequest(
        String trainTripId,
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
