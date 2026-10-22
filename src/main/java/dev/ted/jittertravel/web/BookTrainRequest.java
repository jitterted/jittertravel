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

    /**
     * The same form under a new id, for re-rendering after a refusal: the attempt that was refused
     * retires its id (H4), so the corrected resubmit is a new command rather than a collision with
     * the row the refusal may have left.
     */
    public BookTrainRequest withTrainTripId(String trainTripId) {
        return new BookTrainRequest(trainTripId, serviceId,
                departureStationName, departureCityName, departureCountry, departureMapsUrl,
                departureZone, departureDateTime,
                arrivalStationName, arrivalCityName, arrivalCountry, arrivalMapsUrl,
                arrivalZone, arrivalDateTime);
    }
}
