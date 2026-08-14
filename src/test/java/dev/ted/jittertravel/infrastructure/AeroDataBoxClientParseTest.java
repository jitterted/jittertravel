package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    // One flight number, two chained legs on the same day — what UA 1604 (RDU-DEN-RNO) looks like.
    private static final String TWO_LEG_RESPONSE = """
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

    private final AeroDataBoxClient client = new AeroDataBoxClient(
            RestClient.builder(),
            JsonMapper.builder().build(),
            "https://example.invalid",
            "example.invalid",
            "" // no API key needed for parse-only tests
    );

    @Test
    void parsesSampleResponseIntoFlightLookupResult() {
        FlightLookupCandidates candidates = client.parse(SAMPLE_RESPONSE);

        assertThat(candidates.requiresChoice())
                .as("a single-segment response needs no choice from the user")
                .isFalse();
        FlightLookupResult lookup = candidates.single();
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
    void emptyJsonArrayReturnsNoCandidates() {
        assertThat(client.parse("[]").isEmpty())
                .as("an empty array yields no candidates")
                .isTrue();
    }

    @Test
    void blankResponseReturnsNoCandidates() {
        assertThat(client.parse("").segments()).isEmpty();
        assertThat(client.parse(null).segments()).isEmpty();
    }

    @Test
    void malformedResponseReturnsNoCandidates() {
        assertThat(client.parse("not json").segments()).isEmpty();
    }

    @Test
    void multiSegmentKeepsEveryLegSeparately() {
        FlightLookupCandidates candidates = client.parse(TWO_LEG_RESPONSE);

        assertThat(candidates.requiresChoice())
                .as("two segments means the user must pick which leg they are on")
                .isTrue();
        assertThat(candidates.segments())
                .extracting(FlightLookupResult::departureAirport,
                            FlightLookupResult::departureDateTime,
                            FlightLookupResult::arrivalAirport,
                            FlightLookupResult::arrivalDateTime)
                .containsExactly(
                        tuple("SFO", LocalDateTime.of(2026, 6, 28, 6, 0),
                              "DFW", LocalDateTime.of(2026, 6, 28, 11, 30)),
                        tuple("DFW", LocalDateTime.of(2026, 6, 28, 12, 30),
                              "JFK", LocalDateTime.of(2026, 6, 28, 16, 45)));
    }

    @Test
    void chainingSegmentsAlsoOfferTheWholeTripAsOneOption() {
        FlightLookupCandidates candidates = client.parse(TWO_LEG_RESPONSE);

        assertThat(candidates.throughFlight()).isPresent();
        FlightLookupResult through = candidates.throughFlight().get();
        assertThat(through.departureAirport()).isEqualTo("SFO");
        assertThat(through.departureDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 6, 0));
        assertThat(through.arrivalAirport()).isEqualTo("JFK");
        assertThat(through.arrivalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 28, 16, 45));
    }

    @Test
    void unrelatedFlightsSharingANumberOfferNoWholeTripOption() {
        // Second segment does not start where the first one ends: these are not legs of one
        // journey, so collapsing them would invent a route nobody flies.
        String unrelated = """
                [
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"SFO"},
                                "scheduledTime":{"local":"2026-06-28 06:00-07:00"}},
                   "arrival":{"airport":{"iata":"DFW"},
                              "scheduledTime":{"local":"2026-06-28 11:30-05:00"}}},
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"ORD"},
                                "scheduledTime":{"local":"2026-06-28 12:30-05:00"}},
                   "arrival":{"airport":{"iata":"JFK"},
                              "scheduledTime":{"local":"2026-06-28 16:45-04:00"}}}
                ]
                """;

        assertThat(client.parse(unrelated).throughFlight()).isEmpty();
    }

    @Test
    void segmentsAreOrderedByDepartureTimeRegardlessOfResponseOrder() {
        String reversed = """
                [
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"DFW"},
                                "scheduledTime":{"local":"2026-06-28 12:30-05:00"}},
                   "arrival":{"airport":{"iata":"JFK"},
                              "scheduledTime":{"local":"2026-06-28 16:45-04:00"}}},
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"SFO"},
                                "scheduledTime":{"local":"2026-06-28 06:00-07:00"}},
                   "arrival":{"airport":{"iata":"DFW"},
                              "scheduledTime":{"local":"2026-06-28 11:30-05:00"}}}
                ]
                """;

        assertThat(client.parse(reversed).segments())
                .extracting(FlightLookupResult::departureAirport)
                .containsExactly("SFO", "DFW");
    }

    @Test
    void segmentMissingRequiredFieldsIsDroppedWithoutLosingTheOthers() {
        String oneBadSegment = """
                [
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"SFO"}},
                   "arrival":{"airport":{"iata":"DFW"},
                              "scheduledTime":{"local":"2026-06-28 11:30-05:00"}}},
                  {"number":"AA 1","airline":{"name":"American Airlines"},
                   "departure":{"airport":{"iata":"DFW"},
                                "scheduledTime":{"local":"2026-06-28 12:30-05:00"}},
                   "arrival":{"airport":{"iata":"JFK"},
                              "scheduledTime":{"local":"2026-06-28 16:45-04:00"}}}
                ]
                """;

        FlightLookupCandidates candidates = client.parse(oneBadSegment);

        assertThat(candidates.single().departureAirport()).isEqualTo("DFW");
    }

    @Test
    void lookupWithBlankApiKeyReturnsNoCandidatesWithoutCallingApi() {
        assertThat(client.lookup("UA195", LocalDate.of(2026, 6, 28)).isEmpty())
                .as("a blank API key short-circuits before any HTTP call")
                .isTrue();
    }
}
