package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1→v2 datetime rung for hotels: a legacy bare wall-clock scalar upcasts to the current {@code
 * ZonedTimestamp} shape with the zone derived from the hotel's own address, the wall-clock preserved
 * as the entry-zone time; an already-migrated payload passes through untouched.
 */
class HotelTimeZoneUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final HotelTimeZoneUpcaster upcaster =
            new HotelTimeZoneUpcaster(new LocationZoneResolver(), new WallClockZoning(mapper));

    @Test
    void handlesBothHotelTypesAtVersionOneOnly() {
        assertThat(upcaster.canHandle("HotelBooked", 1))
                .as("HotelBooked v1 is this rung")
                .isTrue();
        assertThat(upcaster.canHandle("HotelChanged", 1))
                .as("HotelChanged shares the shape and so the rung")
                .isTrue();
        assertThat(upcaster.canHandle("HotelBooked", 2))
                .as("a v2 hotel is already current — not this rung")
                .isFalse();
        assertThat(upcaster.canHandle("TrainBooked", 1))
                .as("another type's v1 is another rung")
                .isFalse();
    }

    @Test
    void legacyScalarDatetimesAreUpcastUsingTheAddressZone() {
        // 2026-06-17 is BST (+01:00) in London, so 15:00 local == 14:00Z.
        HotelBooked event = upcast("""
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
                """);

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
        HotelBooked event = upcast("""
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
                """);

        assertThat(event.checkIn().utc())
                .isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
        assertThat(event.checkIn().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    private HotelBooked upcast(String json) {
        ObjectNode payload = (ObjectNode) mapper.readTree(json);
        upcaster.upcast(payload, "HotelBooked");
        return mapper.treeToValue(payload, HotelBooked.class);
    }
}
