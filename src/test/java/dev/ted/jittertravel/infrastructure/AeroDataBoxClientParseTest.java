package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AeroDataBoxClientParseTest {

    private static final String SAMPLE_RESPONSE = """
            [{"greatCircleDistance":{"meter":9460750.05,"km":9460.75,"mile":5878.64,"nm":5108.4,"feet":31039206.2},
             "departure":{"airport":{"icao":"EDDM","iata":"MUC","name":"Munich","shortName":"Munich",
              "municipalityName":"Munich","location":{"lat":48.3538,"lon":11.7861},
              "countryCode":"DE","timeZone":"Europe/Berlin"},
              "scheduledTime":{"utc":"2026-06-28 09:45Z","local":"2026-06-28 11:45+02:00"},
              "terminal":"2","quality":["Basic"]},
             "arrival":{"airport":{"icao":"KSFO","iata":"SFO","name":"San Francisco","shortName":"San Francisco",
              "municipalityName":"San Francisco","location":{"lat":37.619,"lon":-122.375},
              "countryCode":"US","timeZone":"America/Los_Angeles"},
              "scheduledTime":{"utc":"2026-06-28 21:20Z","local":"2026-06-28 14:20-07:00"},
              "predictedTime":{"utc":"2026-06-28 21:12Z","local":"2026-06-28 14:12-07:00"},
              "terminal":"I","quality":["Basic"]},
             "lastUpdatedUtc":"2025-11-02 00:16Z","number":"UA 195","status":"Expected",
             "codeshareStatus":"Unknown","isCargo":false,"aircraft":{"model":"Boeing 777"},
             "airline":{"name":"United Airlines","iata":"UA","icao":"UAL"}}]
            """;

    private final AeroDataBoxClient client = new AeroDataBoxClient(
            RestClient.builder(),
            JsonMapper.builder().build(),
            "https://example.invalid",
            "example.invalid",
            "" // no API key needed for parse-only tests
    );

    @Test
    void parsesSampleResponseIntoFlightLookupResult() {
        Optional<FlightLookupResult> result = client.parse(SAMPLE_RESPONSE);

        assertThat(result).isPresent();
        FlightLookupResult lookup = result.get();
        assertThat(lookup.airline()).isEqualTo("United Airlines");
        assertThat(lookup.flightNumber()).isEqualTo("UA195");
        assertThat(lookup.departureAirport()).isEqualTo("MUC");
        assertThat(lookup.departureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 11, 45));
        assertThat(lookup.arrivalAirport()).isEqualTo("SFO");
        assertThat(lookup.arrivalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 14, 20));
    }

    @Test
    void emptyJsonArrayReturnsEmpty() {
        assertThat(client.parse("[]")).isEmpty();
    }

    @Test
    void blankResponseReturnsEmpty() {
        assertThat(client.parse("")).isEmpty();
        assertThat(client.parse(null)).isEmpty();
    }

    @Test
    void malformedResponseReturnsEmpty() {
        assertThat(client.parse("not json")).isEmpty();
    }

    @Test
    void multiSegmentTakesFirstDepartureAndLastArrival() {
        String multiSegment = """
                [
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"SFO"},
                                "scheduledTime":{"local":"2026-06-28 06:00-07:00"}},
                   "arrival":{"airport":{"iata":"DFW"},
                              "scheduledTime":{"local":"2026-06-28 11:30-05:00"}}},
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"DFW"},
                                "scheduledTime":{"local":"2026-06-28 12:30-05:00"}},
                   "arrival":{"airport":{"iata":"JFK"},
                              "scheduledTime":{"local":"2026-06-28 16:45-04:00"}}}
                ]
                """;

        Optional<FlightLookupResult> result = client.parse(multiSegment);

        assertThat(result).isPresent();
        assertThat(result.get().departureAirport()).isEqualTo("SFO");
        assertThat(result.get().departureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 6, 0));
        assertThat(result.get().arrivalAirport()).isEqualTo("JFK");
        assertThat(result.get().arrivalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 16, 45));
    }

    @Test
    void lookupWithBlankApiKeyReturnsEmptyWithoutCallingApi() {
        assertThat(client.lookup("UA195", java.time.LocalDate.of(2026, 6, 28))).isEmpty();
    }
}
