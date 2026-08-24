package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1→v2 datetime rung for conferences: start and end are at the one venue, so they share its
 * address-derived zone. This rung is <em>only</em> the datetime migration — injecting the v3 {@code
 * format} field is a separate rung ({@link ConferenceFormatUpcaster}), so this one leaves an absent
 * {@code format} absent.
 */
class ConferenceTimeZoneUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final ConferenceTimeZoneUpcaster upcaster =
            new ConferenceTimeZoneUpcaster(new LocationZoneResolver(), new WallClockZoning(mapper));

    @Test
    void handlesConferenceAtVersionOneOnly() {
        assertThat(upcaster.canHandle("ConferencePlanned", 1))
                .isTrue();
        assertThat(upcaster.canHandle("ConferencePlanned", 2))
                .as("v2→v3 is the format rung, not this one")
                .isFalse();
        assertThat(upcaster.canHandle("GatheringPlanned", 1))
                .isFalse();
    }

    @Test
    void resolvesBothEndsFromTheVenueAddress() {
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {
                  "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "name": "JitterConf",
                  "startDate": "2026-09-15T09:00:00",
                  "endDate": "2026-09-17T17:00:00",
                  "venueName": "Moscone Center",
                  "venueAddress": {
                    "street": "747 Howard St", "city": "San Francisco", "region": "CA",
                    "postalCode": "94103", "country": "USA", "locationForMatching": "San Francisco"
                  }
                }
                """);

        upcaster.upcast(payload, "ConferencePlanned");

        assertThat(payload.has("format"))
                .as("the datetime rung must not inject format — that is the separate v2→v3 rung")
                .isFalse();
        // Bind with format supplied out-of-band, since this rung alone leaves it absent.
        payload.put("format", "CALL_FOR_PAPERS");
        ConferencePlanned event = mapper.treeToValue(payload, ConferencePlanned.class);
        assertThat(event.startDate().zone())
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(event.startDate().utc())
                .as("09:00 PDT is 16:00Z")
                .isEqualTo(Instant.parse("2026-09-15T16:00:00Z"));
        assertThat(event.endDate().localDateTime().toString())
                .as("the original end wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-09-17T17:00");
    }

    @Test
    void newShapeDatetimePassesThroughUntouched() {
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {
                  "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "name": "JitterConf",
                  "startDate": {"utc": "2026-09-15T16:00:00Z", "zone": "America/Los_Angeles"},
                  "endDate": {"utc": "2026-09-18T00:00:00Z", "zone": "America/Los_Angeles"},
                  "venueName": "Moscone Center",
                  "venueAddress": {"street": "747 Howard St", "city": "San Francisco",
                    "region": "CA", "postalCode": "94103", "country": "USA",
                    "locationForMatching": "San Francisco"}
                }
                """);
        JsonNode before = payload.deepCopy();

        upcaster.upcast(payload, "ConferencePlanned");

        assertThat(payload)
                .as("an already-migrated datetime is left untouched")
                .isEqualTo(before);
    }
}
