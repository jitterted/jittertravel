package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces R6 (no event-structure change without a migration plan) at CI time.
 * <p>
 * For each persisted event type we keep a canonical JSON sample inline as a
 * text block. The mapper is configured with
 * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} = true (stricter
 * than production):
 * <ul>
 *   <li>Adding an optional/nullable field stays green (the old JSON has no
 *       such field; the new component is populated with {@code null}).</li>
 *   <li>Removing a field fails — the old JSON still carries it and unknown
 *       properties are now errors.</li>
 *   <li>Renaming a field fails for the same reason.</li>
 *   <li>Changing a field's type generally fails at parse time.</li>
 * </ul>
 * If you intentionally change an event's structure, the migration plan
 * (R6) tells you what to do; once that plan is in place, update the
 * sample JSON to match the new shape.
 */
class GoldenEventDeserializationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    @Test
    void flightBookedSampleDeserializes() {
        String json = """
                {
                  "flightId": {"id": "11111111-1111-1111-1111-111111111111"},
                  "airline": "United",
                  "flightNumber": "UA59",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": "2026-06-06T13:55:00",
                  "arrivalAirport": {"code": "FRA"},
                  "arrivalDateTime": "2026-06-07T09:45:00"
                }
                """;

        FlightBooked event = deserialize(json, FlightBooked.class);

        assertThat(event.flightId().id())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(event.airline())
                .isEqualTo("United");
        assertThat(event.departureAirport().code())
                .isEqualTo("SFO");
    }

    @Test
    void flightChangedSampleDeserializes() {
        String json = """
                {
                  "flightId": {"id": "11111111-1111-1111-1111-111111111111"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": "2026-06-08T16:00:00",
                  "arrivalAirport": {"code": "MUC"},
                  "arrivalDateTime": "2026-06-09T11:30:00",
                  "reason": "Schedule shifted by airline"
                }
                """;

        FlightChanged event = deserialize(json, FlightChanged.class);

        assertThat(event.flightNumber())
                .isEqualTo("LH441");
        assertThat(event.arrivalAirport().code())
                .isEqualTo("MUC");
        assertThat(event.reason())
                .isEqualTo("Schedule shifted by airline");
    }

    @Test
    void conferenceTentativelyPlannedSampleDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "name": "JitterConf",
                  "startDate": "2026-09-15T09:00:00",
                  "endDate": "2026-09-17T17:00:00",
                  "venueName": "Moscone Center",
                  "venueAddress": {
                    "street": "747 Howard St",
                    "city": "San Francisco",
                    "state": "CA",
                    "country": "USA",
                    "postalCode": "94103"
                  }
                }
                """;

        ConferenceTentativelyPlanned event = deserialize(json, ConferenceTentativelyPlanned.class);

        assertThat(event.name())
                .isEqualTo("JitterConf");
        assertThat(event.venueAddress().city())
                .isEqualTo("San Francisco");
    }

    @Test
    void trainBookedLegacyPayloadWithoutServiceIdDeserializesToEmptyString() {
        String json = """
                {
                  "tripId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "departureStation": {
                    "name": "London Euston",
                    "city": "London",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "departureDateTime": "2026-06-09T09:00:00",
                  "arrivalStation": {
                    "name": "Manchester Piccadilly",
                    "city": "Manchester",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "arrivalDateTime": "2026-06-09T11:15:00"
                }
                """;

        TrainBooked event = deserialize(json, TrainBooked.class);

        assertThat(event.serviceId())
                .as("Legacy payload missing serviceId must deserialize to empty string, not null")
                .isEmpty();
        assertThat(event.departureStation().mapsUrl())
                .as("null mapsUrl in JSON payload must deserialize to empty string, not null")
                .isEmpty();
    }

    @Test
    void trainBookedCurrentPayloadWithServiceIdDeserializes() {
        String json = """
                {
                  "tripId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "departureStation": {
                    "name": "London Euston",
                    "city": "London",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "departureDateTime": "2026-06-09T09:00:00",
                  "arrivalStation": {
                    "name": "Manchester Piccadilly",
                    "city": "Manchester",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "arrivalDateTime": "2026-06-09T11:15:00",
                  "serviceId": "DB - ICE 610"
                }
                """;

        TrainBooked event = deserialize(json, TrainBooked.class);

        assertThat(event.serviceId())
                .isEqualTo("DB - ICE 610");
    }

    private static <T> T deserialize(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
