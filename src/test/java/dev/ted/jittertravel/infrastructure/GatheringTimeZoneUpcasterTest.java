package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.GatheringPlanned;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1→v2 rung for gatherings: the one migration that changes the field <em>set</em> rather than a
 * field's type — {@code date} + {@code startTime} + {@code endTime} collapse into {@code startsAt} +
 * {@code endsAt}, both in the venue's zone, and the legacy keys are removed so the record can bind.
 */
class GatheringTimeZoneUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final GatheringTimeZoneUpcaster upcaster =
            new GatheringTimeZoneUpcaster(new LocationZoneResolver(), new WallClockZoning(mapper));

    @Test
    void handlesBothGatheringTypesAtVersionOneOnly() {
        assertThat(upcaster.canHandle("GatheringPlanned", 1))
                .isTrue();
        assertThat(upcaster.canHandle("GatheringChanged", 1))
                .isTrue();
        assertThat(upcaster.canHandle("GatheringPlanned", 2))
                .isFalse();
        assertThat(upcaster.canHandle("ConferencePlanned", 1))
                .isFalse();
    }

    @Test
    void collapsesItsDateAndTwoTimesIntoTwoTimestamps() {
        GatheringPlanned event = upcast("""
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "London Java Community",
                  "venueName": "Skills Matter",
                  "location": {
                    "street": "1 Example St", "city": "London", "region": "",
                    "postalCode": "EC1A 1BB", "country": "United Kingdom",
                    "locationForMatching": "London"
                  },
                  "date": "2026-09-15",
                  "startTime": "18:30",
                  "endTime": "21:00",
                  "speaking": true,
                  "infoUrl": ""
                }
                """);

        assertThat(event.startsAt().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
        assertThat(event.startsAt().utc())
                .as("18:30 BST is 17:30Z")
                .isEqualTo(Instant.parse("2026-09-15T17:30:00Z"));
        assertThat(event.endsAt().localDateTime().toString())
                .as("the original end wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-09-15T21:00");
    }

    @Test
    void legacyKeysAreRemovedSoTheRecordCanBind() {
        // Unlike the other migrations, leaving the old keys behind would break binding: the record no
        // longer declares them and the golden contract test rejects unknown properties.
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "London Java Community",
                  "venueName": "Skills Matter",
                  "location": {
                    "street": "1 Example St", "city": "London", "region": "",
                    "postalCode": "EC1A 1BB", "country": "United Kingdom",
                    "locationForMatching": "London"
                  },
                  "date": "2026-09-15",
                  "startTime": "18:30",
                  "endTime": "21:00",
                  "speaking": false,
                  "infoUrl": ""
                }
                """);

        upcaster.upcast(payload, "GatheringPlanned");

        assertThat(payload.has("date") || payload.has("startTime") || payload.has("endTime"))
                .as("legacy wall-clock keys must not survive the upcast")
                .isFalse();
        assertThat(payload.has("startsAt") && payload.has("endsAt"))
                .as("both replacement timestamps must be present")
                .isTrue();
    }

    @Test
    void newShapePassesThroughUnchanged() {
        // Note the unresolvable "GB" country: an already-migrated payload must never reach the
        // resolver, so a stored event whose location the tables don't know still replays.
        GatheringPlanned event = upcast("""
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "London Java Community",
                  "venueName": "Skills Matter",
                  "location": {
                    "street": "1 Example St", "city": "London", "region": "",
                    "postalCode": "EC1A 1BB", "country": "GB", "locationForMatching": "London"
                  },
                  "startsAt": {"utc": "2026-09-15T17:30:00Z", "zone": "Europe/London"},
                  "endsAt": {"utc": "2026-09-15T20:00:00Z", "zone": "Europe/London"},
                  "speaking": true,
                  "infoUrl": ""
                }
                """);

        assertThat(event.startsAt().utc())
                .isEqualTo(Instant.parse("2026-09-15T17:30:00Z"));
        assertThat(event.endsAt().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    private GatheringPlanned upcast(String json) {
        ObjectNode payload = (ObjectNode) mapper.readTree(json);
        upcaster.upcast(payload, "GatheringPlanned");
        return mapper.treeToValue(payload, GatheringPlanned.class);
    }
}
