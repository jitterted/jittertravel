package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Subdivision;
import dev.ted.jittertravel.domain.Subdivisions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
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
    private final Subdivisions subdivisions = new Subdivisions();

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
            String postalCode = text(addr, "postcode");
            // Codes, because the form's Country and State are selects of codes. Nominatim's full
            // names ("United States", "Colorado") are where half the log's two spellings came from
            // (docs/LocationDataCleanupPlan.md).
            String country = coalesce(text(addr, "country_code")).toUpperCase(Locale.ROOT);
            String region = subdivisions.required(country)
                    ? subdivisionCode(addr, country)
                    : firstOf(addr, "state", "county", "state_district");

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

    /**
     * The postal code of the state or province, from Nominatim's ISO 3166-2 field ({@code "US-CO"}),
     * kept only when it is one of the country's own; otherwise blank, so the form asks for it.
     */
    private String subdivisionCode(JsonNode addr, String country) {
        String iso = coalesce(text(addr, "ISO3166-2-lvl4"));
        String prefix = country + "-";
        if (!iso.toUpperCase(Locale.ROOT).startsWith(prefix)) {
            return "";
        }
        return subdivisions.find(country, iso.substring(prefix.length()))
                           .map(Subdivision::code)
                           .orElse("");
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
