package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces R6 (no event-structure change without a migration plan) at CI time.
 * <p>
 * For each persisted event type we commit a canonical JSON sample under
 * {@code src/test/resources/event-samples/}. This test deserializes each
 * sample using a {@link JsonMapper} configured with
 * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} = true
 * (stricter than production):
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
    void flightBookedSampleDeserializes() throws IOException {
        FlightBooked event = readSample("FlightBooked.json", FlightBooked.class);

        assertThat(event.flightId().id())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(event.airline()).isEqualTo("United");
        assertThat(event.departureAirport().code()).isEqualTo("SFO");
    }

    @Test
    void flightChangedSampleDeserializes() throws IOException {
        FlightChanged event = readSample("FlightChanged.json", FlightChanged.class);

        assertThat(event.flightNumber()).isEqualTo("LH441");
        assertThat(event.arrivalAirport().code()).isEqualTo("MUC");
        assertThat(event.reason()).isEqualTo("Schedule shifted by airline");
    }

    @Test
    void conferenceTentativelyPlannedSampleDeserializes() throws IOException {
        ConferenceTentativelyPlanned event =
                readSample("ConferenceTentativelyPlanned.json", ConferenceTentativelyPlanned.class);

        assertThat(event.name()).isEqualTo("JitterConf");
        assertThat(event.venueAddress().city()).isEqualTo("San Francisco");
    }

    private static <T> T readSample(String fileName, Class<T> type) throws IOException {
        try (InputStream in = GoldenEventDeserializationTest.class.getClassLoader()
                .getResourceAsStream("event-samples/" + fileName)) {
            assertThat(in).as("missing event sample: %s", fileName).isNotNull();
            return MAPPER.readValue(in, type);
        }
    }
}
