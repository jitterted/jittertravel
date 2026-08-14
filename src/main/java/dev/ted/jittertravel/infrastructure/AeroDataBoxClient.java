package dev.ted.jittertravel.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client for AeroDataBox (RapidAPI) flight-number lookup endpoint.
 * <p>
 * Example:
 *   GET /flights/number/UA195/2026-06-28
 *       ?withAircraftImage=false&withLocation=false&withFlightPlan=false&dateLocalRole=Departure
 * <p>
 * Response is a JSON array of flight segments. Every segment is returned as its own
 * {@link FlightLookupResult}; see {@link FlightLookupCandidates} for why they are not collapsed
 * into one.
 */
@Component
public class AeroDataBoxClient {

    private static final Logger log = LoggerFactory.getLogger(AeroDataBoxClient.class);
    private static final DateTimeFormatter LOCAL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mmXXX");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String apiKey;

    public AeroDataBoxClient(RestClient.Builder restClientBuilder,
                             JsonMapper jsonMapper,
                             @Value("${jittertravel.aerodatabox.base-url}") String baseUrl,
                             @Value("${jittertravel.aerodatabox.host}") String host,
                             @Value("${jittertravel.aerodatabox.api-key:}") String apiKey) {
        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-RapidAPI-Host", host)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public FlightLookupCandidates lookup(String flightNumber, LocalDate departureDate) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AeroDataBox API key is not configured; skipping lookup");
            return FlightLookupCandidates.none();
        }
        String trimmedFlightNumber = flightNumber == null ? "" : flightNumber.replace(" ", "").trim();
        if (trimmedFlightNumber.isEmpty() || departureDate == null) {
            return FlightLookupCandidates.none();
        }

        try {
            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/flights/number/{flightNumber}/{date}")
                            .queryParam("withAircraftImage", false)
                            .queryParam("withLocation", false)
                            .queryParam("withFlightPlan", false)
                            .queryParam("dateLocalRole", "Departure")
                            .build(trimmedFlightNumber, departureDate.toString()))
                    .header("X-RapidAPI-Key", apiKey)
                    .retrieve()
                    .body(String.class);
            return parse(json);
        } catch (Exception e) {
            log.warn("AeroDataBox lookup failed for {} on {}", trimmedFlightNumber, departureDate, e);
            return FlightLookupCandidates.none();
        }
    }

    FlightLookupCandidates parse(String json) {
        if (json == null || json.isBlank()) {
            return FlightLookupCandidates.none();
        }
        try {
            JsonNode root = jsonMapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return FlightLookupCandidates.none();
            }
            List<FlightLookupResult> segments = new ArrayList<>();
            for (JsonNode segment : root) {
                parseSegment(segment).ifPresent(segments::add);
            }
            return new FlightLookupCandidates(segments);
        } catch (Exception e) {
            log.warn("Failed to parse AeroDataBox response", e);
            return FlightLookupCandidates.none();
        }
    }

    private Optional<FlightLookupResult> parseSegment(JsonNode segment) {
        String airline = textAt(segment, "airline", "name");
        String rawFlightNumber = textAt(segment, "number");
        String flightNumber = rawFlightNumber == null ? null : rawFlightNumber.replace(" ", "");

        String departureAirport = textAt(segment, "departure", "airport", "iata");
        LocalDateTime departureDateTime = parseLocal(textAt(segment, "departure", "scheduledTime", "local"));
        String departureZoneId = textAt(segment, "departure", "airport", "timeZone");

        String arrivalAirport = textAt(segment, "arrival", "airport", "iata");
        LocalDateTime arrivalDateTime = parseLocal(textAt(segment, "arrival", "scheduledTime", "local"));
        String arrivalZoneId = textAt(segment, "arrival", "airport", "timeZone");

        if (departureAirport == null || arrivalAirport == null
                || departureDateTime == null || arrivalDateTime == null) {
            return Optional.empty();
        }
        return Optional.of(new FlightLookupResult(
                airline, flightNumber,
                departureAirport, departureDateTime, departureZoneId,
                arrivalAirport, arrivalDateTime, arrivalZoneId
        ));
    }

    private static String textAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            if (current == null || !current.has(segment)) {
                return null;
            }
            current = current.get(segment);
        }
        return current.isTextual() ? current.asText() : null;
    }

    private static LocalDateTime parseLocal(String localWithOffset) {
        // e.g., "2026-06-28 11:45+02:00" — preserve wall-clock time, drop offset.
        if (localWithOffset == null) {
            return null;
        }
        return OffsetDateTime.parse(localWithOffset, LOCAL_TIME_FORMAT).toLocalDateTime();
    }
}
