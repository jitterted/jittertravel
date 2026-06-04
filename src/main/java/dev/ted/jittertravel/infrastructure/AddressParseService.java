package dev.ted.jittertravel.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Parses a free-text address using the Nominatim (OpenStreetMap) geocoding API.
 * Nominatim usage policy requires a descriptive User-Agent header.
 * Rate limit: 1 request/second — acceptable for manual form entry.
 */
@Service
public class AddressParseService {

    private static final Logger log = LoggerFactory.getLogger(AddressParseService.class);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public AddressParseService(RestClient.Builder restClientBuilder, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.restClient = restClientBuilder
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "JitterTravel/1.0 (travel scheduling app)")
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    public record ParsedAddress(
            String street,
            String city,
            String region,
            String postalCode,
            String country,
            String locationForMatching
    ) {}

    public Optional<ParsedAddress> parse(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = restClient.get()
                    .uri(u -> u.path("/search")
                            .queryParam("q", rawAddress.trim())
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .queryParam("limit", "1")
                            .build())
                    .retrieve()
                    .body(String.class);
            return parseNominatimResponse(json);
        } catch (Exception e) {
            log.warn("Nominatim address parse failed for input: {}", rawAddress, e);
            return Optional.empty();
        }
    }

    Optional<ParsedAddress> parseNominatimResponse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = jsonMapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            JsonNode addr = root.get(0).path("address");
            if (addr.isMissingNode()) {
                return Optional.empty();
            }

            String houseNumber = text(addr, "house_number");
            String road = text(addr, "road");
            String street = (houseNumber != null && road != null) ? houseNumber + " " + road
                    : road != null ? road
                    : "";

            String locality = firstOf(addr, "city", "town", "village", "hamlet", "suburb");
            String region = firstOf(addr, "state", "county", "state_district");
            String postalCode = text(addr, "postcode");
            String country = text(addr, "country");

            return Optional.of(new ParsedAddress(
                    coalesce(street),
                    coalesce(locality),
                    coalesce(region),
                    coalesce(postalCode),
                    coalesce(country),
                    coalesce(locality)
            ));
        } catch (Exception e) {
            log.warn("Failed to parse Nominatim JSON response", e);
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private static String firstOf(JsonNode node, String... fields) {
        for (String field : fields) {
            String val = text(node, field);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        return null;
    }

    private static String coalesce(String value) {
        return value != null ? value : "";
    }
}
