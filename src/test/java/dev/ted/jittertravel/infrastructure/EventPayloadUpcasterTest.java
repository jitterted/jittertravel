package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.HotelBooked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backward-compatibility contract for the datetime migration: a legacy {@link HotelBooked} payload
 * (bare wall-clock scalars, no zone) must upcast to the current {@code ZonedTimestamp} shape, with
 * the zone derived from the event's own location and the wall-clock preserved as the entry-zone
 * time. New-shape payloads must pass through untouched (idempotency).
 */
class EventPayloadUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final EventPayloadUpcaster upcaster =
            new EventPayloadUpcaster(new LocationZoneResolver(), mapper);

    @Test
    void legacyHotelBookedScalarDatetimesAreUpcastUsingTheAddressZone() {
        // 2026-06-17 is BST (+01:00) in London, so 15:00 local == 14:00Z.
        String legacy = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Milton Mill House",
                  "address": {
                    "street": "Milton Hill", "city": "Steventon", "region": "Oxfordshire",
                    "postalCode": "OX13 6AF", "country": "UK", "locationForMatching": "Steventon"
                  },
                  "checkIn": "2026-06-17T15:00:00",
                  "checkOut": "2026-06-21T11:00:00",
                  "bookingIntent": "FINAL"
                }
                """;

        HotelBooked event = upcastTo(legacy, HotelBooked.class);

        assertThat(event.checkIn().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
        assertThat(event.checkIn().utc())
                .isEqualTo(Instant.parse("2026-06-17T14:00:00Z"));
        assertThat(event.checkIn().localDateTime().toString())
                .as("the original wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-06-17T15:00");
        assertThat(event.checkOut().utc())
                .isEqualTo(Instant.parse("2026-06-21T10:00:00Z"));
    }

    @Test
    void newShapePayloadPassesThroughUnchanged() {
        String current = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Savoy",
                  "address": {
                    "street": "Strand", "city": "London", "region": "",
                    "postalCode": "WC2R 0EZ", "country": "GB", "locationForMatching": "London"
                  },
                  "checkIn": {"utc": "2026-07-10T14:00:00Z", "zone": "Europe/London"},
                  "checkOut": {"utc": "2026-07-12T10:00:00Z", "zone": "Europe/London"},
                  "bookingIntent": "FINAL"
                }
                """;

        HotelBooked event = upcastTo(current, HotelBooked.class);

        assertThat(event.checkIn().utc())
                .isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
        assertThat(event.checkIn().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    private <T> T upcastTo(String json, Class<T> type) {
        JsonNode upcast = upcaster.upcast(type.getSimpleName(), mapper.readTree(json));
        return mapper.treeToValue(upcast, type);
    }
}
