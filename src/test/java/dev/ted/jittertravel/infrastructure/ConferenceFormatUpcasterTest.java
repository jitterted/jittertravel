package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferencePlanned;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v2→v3 rung for conferences: an absent {@code format} is injected as the {@link
 * ConferenceFormat#CALL_FOR_PAPERS} default; an explicit one survives. A pure field-default rung — it
 * needs no zone resolver, which is exactly why it is separate from the datetime rung.
 */
class ConferenceFormatUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final ConferenceFormatUpcaster upcaster = new ConferenceFormatUpcaster();

    @Test
    void handlesConferenceAtVersionTwoOnly() {
        assertThat(upcaster.canHandle("ConferencePlanned", 2))
                .isTrue();
        assertThat(upcaster.canHandle("ConferencePlanned", 1))
                .as("v1→v2 is the datetime rung, not this one")
                .isFalse();
        assertThat(upcaster.canHandle("GatheringPlanned", 2))
                .isFalse();
    }

    @Test
    void injectsTheDefaultFormatWhenAbsentLeavingTheDatetimeAlone() {
        // A row written after the datetime migration (startDate is a {utc,zone} object) but before
        // format existed: format is injected, the already-migrated datetime is untouched.
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

        upcaster.upcast(payload, "ConferencePlanned");
        ConferencePlanned event = mapper.treeToValue(payload, ConferencePlanned.class);

        assertThat(event.format())
                .as("an absent format defaults to CALL_FOR_PAPERS")
                .isEqualTo(ConferenceFormat.CALL_FOR_PAPERS);
        assertThat(event.startDate().utc())
                .as("the datetime rung's work is left untouched")
                .isEqualTo(Instant.parse("2026-09-15T16:00:00Z"));
    }

    @Test
    void anExplicitFormatSurvivesUnchanged() {
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {
                  "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "name": "SoCraTes",
                  "startDate": {"utc": "2026-09-15T16:00:00Z", "zone": "America/Los_Angeles"},
                  "endDate": {"utc": "2026-09-18T00:00:00Z", "zone": "America/Los_Angeles"},
                  "venueName": "Moscone Center",
                  "venueAddress": {"street": "747 Howard St", "city": "San Francisco",
                    "region": "CA", "postalCode": "94103", "country": "USA",
                    "locationForMatching": "San Francisco"},
                  "format": "OPEN_SPACE"
                }
                """);

        upcaster.upcast(payload, "ConferencePlanned");
        ConferencePlanned event = mapper.treeToValue(payload, ConferencePlanned.class);

        assertThat(event.format())
                .as("an explicit format is never overwritten by the default")
                .isEqualTo(ConferenceFormat.OPEN_SPACE);
    }
}
