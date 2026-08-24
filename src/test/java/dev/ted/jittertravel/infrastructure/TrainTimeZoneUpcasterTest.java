package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.TrainBooked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1→v2 datetime rung for trains: departure and arrival resolve <em>independently</em> from their
 * own station's city/country, so a trip may span two zones.
 */
class TrainTimeZoneUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final TrainTimeZoneUpcaster upcaster =
            new TrainTimeZoneUpcaster(new LocationZoneResolver(), new WallClockZoning(mapper));

    @Test
    void handlesBothTrainTypesAtVersionOneOnly() {
        assertThat(upcaster.canHandle("TrainBooked", 1))
                .isTrue();
        assertThat(upcaster.canHandle("TrainChanged", 1))
                .isTrue();
        assertThat(upcaster.canHandle("TrainBooked", 2))
                .isFalse();
        assertThat(upcaster.canHandle("FlightBooked", 1))
                .isFalse();
    }

    @Test
    void legacyScalarDatetimesUpcastEachEndpointInItsOwnZone() {
        // A Paris→Frankfurt trip: departure resolves from Paris/France, arrival from
        // Frankfurt/Germany — independently. Both are CEST (+02:00) in summer, so 14:30 local == 12:30Z.
        TrainBooked event = upcast("""
                {
                  "tripId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "departureStation": {
                    "name": "Paris Est", "city": "Paris", "country": "France", "mapsUrl": null
                  },
                  "departureDateTime": "2026-06-09T14:30:00",
                  "arrivalStation": {
                    "name": "Frankfurt Hbf", "city": "Frankfurt", "country": "Germany", "mapsUrl": null
                  },
                  "arrivalDateTime": "2026-06-09T18:15:00"
                }
                """);

        assertThat(event.departureDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(event.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-06-09T12:30:00Z"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(event.arrivalDateTime().localDateTime().toString())
                .as("the original arrival wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-06-09T18:15");
    }

    private TrainBooked upcast(String json) {
        ObjectNode payload = (ObjectNode) mapper.readTree(json);
        upcaster.upcast(payload, "TrainBooked");
        return mapper.treeToValue(payload, TrainBooked.class);
    }
}
