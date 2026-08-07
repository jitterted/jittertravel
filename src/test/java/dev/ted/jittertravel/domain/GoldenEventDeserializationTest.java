package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.infrastructure.EventPayloadUpcaster;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final EventPayloadUpcaster UPCASTER = new EventPayloadUpcaster(
            new LocationZoneResolver(), new AirportZoneResolver(), MAPPER);

    @Test
    void flightBookedSampleDeserializes() {
        String json = """
                {
                  "flightId": {"id": "11111111-1111-1111-1111-111111111111"},
                  "airline": "United",
                  "flightNumber": "UA59",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": {"utc": "2026-06-06T13:55:00Z", "zone": "UTC"},
                  "arrivalAirport": {"code": "FRA"},
                  "arrivalDateTime": {"utc": "2026-06-07T09:45:00Z", "zone": "UTC"}
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
                  "departureDateTime": {"utc": "2026-06-08T16:00:00Z", "zone": "UTC"},
                  "arrivalAirport": {"code": "MUC"},
                  "arrivalDateTime": {"utc": "2026-06-09T11:30:00Z", "zone": "UTC"},
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
    void conferenceTentativelyPlannedLegacyPayloadWithStateFieldDeserializes() {
        // Legacy events use "state" — @JsonAlias("state") on Address.region reads both old and new shape.
        // Missing locationForMatching defaults to city via compact constructor.
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

        ConferenceTentativelyPlanned event =
                deserializeLegacy(json, "ConferenceTentativelyPlanned", ConferenceTentativelyPlanned.class);

        assertThat(event.startDate())
                .as("the bare wall-clock is reinterpreted in the venue's zone, not the server's")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 9, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.endDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 17, 17, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.name())
                .isEqualTo("JitterConf");
        assertThat(event.venueAddress().city())
                .isEqualTo("San Francisco");
        assertThat(event.venueAddress().region())
                .isEqualTo("CA");
        assertThat(event.venueAddress().locationForMatching())
                .as("locationForMatching absent in legacy JSON defaults to city")
                .isEqualTo("San Francisco");
    }

    @Test
    void conferenceTentativelyPlannedNewShapePayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "name": "JitterConf",
                  "startDate": {"utc": "2026-09-15T16:00:00Z", "zone": "America/Los_Angeles"},
                  "endDate": {"utc": "2026-09-18T00:00:00Z", "zone": "America/Los_Angeles"},
                  "venueName": "Moscone Center",
                  "venueAddress": {
                    "street": "747 Howard St",
                    "city": "San Francisco",
                    "region": "CA",
                    "country": "USA",
                    "postalCode": "94103",
                    "locationForMatching": "San Francisco"
                  }
                }
                """;

        ConferenceTentativelyPlanned event = deserialize(json, ConferenceTentativelyPlanned.class);

        assertThat(event.startDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 9, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.endDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 17, 17, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.venueName())
                .isEqualTo("Moscone Center");
    }

    @Test
    void hotelBookedCurrentPayloadDeserializes() {
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Milton Mill House",
                  "address": {
                    "street": "Milton Hill",
                    "city": "Steventon",
                    "region": "Oxfordshire",
                    "postalCode": "OX13 6AF",
                    "country": "GB",
                    "locationForMatching": "Steventon"
                  },
                  "checkIn": {"utc": "2026-06-17T14:00:00Z", "zone": "Europe/London"},
                  "checkOut": {"utc": "2026-06-21T10:00:00Z", "zone": "Europe/London"},
                  "bookingIntent": "FINAL"
                }
                """;

        HotelBooked event = deserialize(json, HotelBooked.class);

        assertThat(event.hotelName())
                .isEqualTo("Milton Mill House");
        assertThat(event.address().city())
                .isEqualTo("Steventon");
        assertThat(event.address().locationForMatching())
                .isEqualTo("Steventon");
        assertThat(event.mapsUrl())
                .as("mapsUrl absent in JSON must be empty — render-time generation applies")
                .isEmpty();
        assertThat(event.cancelBy())
                .as("a payload written before cancel-by existed must read back as no deadline")
                .isNull();
    }

    @Test
    void hotelBookedWithMapsUrlInPayloadPreservesIt() {
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Savoy",
                  "address": {
                    "street": "Strand",
                    "city": "London",
                    "region": "",
                    "postalCode": "WC2R 0EZ",
                    "country": "GB",
                    "locationForMatching": "London"
                  },
                  "checkIn": {"utc": "2026-07-10T14:00:00Z", "zone": "Europe/London"},
                  "checkOut": {"utc": "2026-07-12T10:00:00Z", "zone": "Europe/London"},
                  "bookingIntent": "FINAL",
                  "mapsUrl": "https://maps.google.com/?q=place_id:ChIJB9OTMDIbdkgRp0JWR_EVkZM"
                }
                """;

        HotelBooked event = deserialize(json, HotelBooked.class);

        assertThat(event.mapsUrl())
                .as("mapsUrl present in JSON must be preserved, not overwritten by fallback")
                .isEqualTo("https://maps.google.com/?q=place_id:ChIJB9OTMDIbdkgRp0JWR_EVkZM");
    }

    @Test
    void hotelChangedCurrentPayloadDeserializes() {
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Milton Mill House",
                  "address": {
                    "street": "Milton Hill",
                    "city": "Steventon",
                    "region": "Oxfordshire",
                    "postalCode": "OX13 6AF",
                    "country": "GB",
                    "locationForMatching": "Steventon"
                  },
                  "checkIn": {"utc": "2026-06-18T15:00:00Z", "zone": "Europe/London"},
                  "checkOut": {"utc": "2026-06-22T09:00:00Z", "zone": "Europe/London"},
                  "bookingIntent": "FINAL",
                  "mapsUrl": "https://maps.google.com/?q=place_id:ChIJexample"
                }
                """;

        HotelChanged event = deserialize(json, HotelChanged.class);

        assertThat(event.hotelName())
                .isEqualTo("Milton Mill House");
        assertThat(event.address().city())
                .isEqualTo("Steventon");
        assertThat(event.checkIn().utc())
                .isEqualTo(Instant.parse("2026-06-18T15:00:00Z"));
        assertThat(event.checkIn().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
        assertThat(event.mapsUrl())
                .isEqualTo("https://maps.google.com/?q=place_id:ChIJexample");
    }

    @Test
    void hotelBookedWithCancelByInPayloadPreservesTheDeadlineAndItsZone() {
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Savoy",
                  "address": {
                    "street": "Strand",
                    "city": "London",
                    "region": "",
                    "postalCode": "WC2R 0EZ",
                    "country": "GB",
                    "locationForMatching": "London"
                  },
                  "checkIn": {"utc": "2026-07-10T14:00:00Z", "zone": "Europe/London"},
                  "checkOut": {"utc": "2026-07-12T10:00:00Z", "zone": "Europe/London"},
                  "bookingIntent": "FINAL",
                  "mapsUrl": "",
                  "cancelBy": {"utc": "2026-07-08T17:00:00Z", "zone": "Europe/London"}
                }
                """;

        HotelBooked event = deserialize(json, HotelBooked.class);

        assertThat(event.cancelBy())
                .as("the deadline is a moment in the hotel's zone, like check-in")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 7, 8, 18, 0), ZoneId.of("Europe/London")));
    }

    @Test
    void hotelChangedPayloadWithoutMapsUrlDefaultsToEmpty() {
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Grand Hotel",
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield",
                    "region": "IL",
                    "postalCode": "62701",
                    "country": "US",
                    "locationForMatching": "Springfield"
                  },
                  "checkIn": {"utc": "2026-09-15T20:00:00Z", "zone": "America/Chicago"},
                  "checkOut": {"utc": "2026-09-18T16:00:00Z", "zone": "America/Chicago"},
                  "bookingIntent": "TENTATIVE"
                }
                """;

        HotelChanged event = deserialize(json, HotelChanged.class);

        assertThat(event.mapsUrl())
                .as("mapsUrl absent in JSON must be empty — render-time generation applies")
                .isEmpty();
        assertThat(event.cancelBy())
                .as("a snapshot written before cancel-by existed must read back as no deadline")
                .isNull();
    }

    @Test
    void hotelBookedLegacyPayloadWithStateFieldAndNoLocationForMatchingDeserializes() {
        // Legacy events use "state" not "region"; locationForMatching absent → defaults to city.
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Grand Hotel",
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield",
                    "state": "IL",
                    "postalCode": "62701",
                    "country": "US"
                  },
                  "checkIn": {"utc": "2026-09-15T20:00:00Z", "zone": "America/Chicago"},
                  "checkOut": {"utc": "2026-09-18T16:00:00Z", "zone": "America/Chicago"},
                  "bookingIntent": "TENTATIVE"
                }
                """;

        HotelBooked event = deserialize(json, HotelBooked.class);

        assertThat(event.address().region())
                .as("@JsonAlias maps legacy 'state' field to region")
                .isEqualTo("IL");
        assertThat(event.address().locationForMatching())
                .as("locationForMatching absent in legacy JSON defaults to city")
                .isEqualTo("Springfield");
        assertThat(event.mapsUrl())
                .as("mapsUrl absent in legacy JSON must be empty — render-time generation applies")
                .isEmpty();
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
                  "departureDateTime": {"utc": "2026-06-09T08:00:00Z", "zone": "Europe/London"},
                  "arrivalStation": {
                    "name": "Manchester Piccadilly",
                    "city": "Manchester",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "arrivalDateTime": {"utc": "2026-06-09T10:15:00Z", "zone": "Europe/London"}
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
                  "departureDateTime": {"utc": "2026-06-09T08:00:00Z", "zone": "Europe/London"},
                  "arrivalStation": {
                    "name": "Manchester Piccadilly",
                    "city": "Manchester",
                    "country": "UK",
                    "mapsUrl": null
                  },
                  "arrivalDateTime": {"utc": "2026-06-09T10:15:00Z", "zone": "Europe/London"},
                  "serviceId": "DB - ICE 610"
                }
                """;

        TrainBooked event = deserialize(json, TrainBooked.class);

        assertThat(event.serviceId())
                .isEqualTo("DB - ICE 610");
    }

    @Test
    void gatheringPlannedSampleDeserializes() {
        String json = """
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "London Java Community — November Meetup",
                  "venueName": "Skills Matter",
                  "location": {
                    "street": "1 Example Street",
                    "city": "London",
                    "region": "",
                    "postalCode": "EC1A 1BB",
                    "country": "GB",
                    "locationForMatching": "London"
                  },
                  "startsAt": {"utc": "2026-09-15T17:30:00Z", "zone": "Europe/London"},
                  "endsAt": {"utc": "2026-09-15T20:00:00Z", "zone": "Europe/London"},
                  "speaking": true,
                  "infoUrl": "https://www.meetup.com/londonjavacommunity/events/123456/"
                }
                """;

        GatheringPlanned event = deserialize(json, GatheringPlanned.class);

        assertThat(event.gatheringId().id())
                .isEqualTo(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        assertThat(event.title())
                .isEqualTo("London Java Community — November Meetup");
        assertThat(event.venueName())
                .isEqualTo("Skills Matter");
        assertThat(event.location().city())
                .isEqualTo("London");
        assertThat(event.startsAt().localDateTime().toString())
                .isEqualTo("2026-09-15T18:30");
        assertThat(event.endsAt().localDateTime().toString())
                .isEqualTo("2026-09-15T21:00");
        assertThat(event.speaking())
                .as("speaking flag must be preserved from JSON")
                .isTrue();
        assertThat(event.infoUrl())
                .isEqualTo("https://www.meetup.com/londonjavacommunity/events/123456/");
    }

    @Test
    void legacyGatheringPlannedWallClockTrioIsUpcastToStartsAtAndEndsAt() {
        // Written before gatherings stored instants: a date plus two times, no zone anywhere. The
        // zone comes from the payload's own location, so the upcast lands on the same moment the
        // traveler meant. Note this is a *shape* change — the three legacy keys must be gone, or
        // FAIL_ON_UNKNOWN_PROPERTIES below would reject the payload.
        String json = """
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "London Java Community — November Meetup",
                  "venueName": "Skills Matter",
                  "location": {
                    "street": "1 Example Street",
                    "city": "London",
                    "region": "",
                    "postalCode": "EC1A 1BB",
                    "country": "United Kingdom",
                    "locationForMatching": "London"
                  },
                  "date": "2026-09-15",
                  "startTime": "18:30",
                  "endTime": "21:00",
                  "speaking": true,
                  "infoUrl": "https://www.meetup.com/londonjavacommunity/events/123456/"
                }
                """;

        GatheringPlanned event = deserializeLegacy(json, "GatheringPlanned", GatheringPlanned.class);

        assertThat(event.startsAt())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 18, 30), ZoneId.of("Europe/London")));
        assertThat(event.endsAt())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 21, 0), ZoneId.of("Europe/London")));
        assertThat(event.title())
                .isEqualTo("London Java Community — November Meetup");
        assertThat(event.speaking())
                .as("speaking flag must survive the upcast")
                .isTrue();
    }

    @Test
    void legacyGatheringChangedWallClockTrioIsUpcastToStartsAtAndEndsAt() {
        // GatheringChanged carries the same date+startTime+endTime trio as GatheringPlanned and
        // would fail identically if the upcaster's case list or key removal missed it. The venue is
        // deliberately outside the server zone, so a zone read from the wrong place is visible.
        String json = """
                {
                  "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                  "title": "Tokyo Rubyist Meetup",
                  "venueName": "Shibuya Hikarie",
                  "location": {
                    "street": "2-21-1 Shibuya",
                    "city": "Tokyo",
                    "region": "",
                    "postalCode": "150-8510",
                    "country": "Japan",
                    "locationForMatching": "Tokyo"
                  },
                  "date": "2026-09-15",
                  "startTime": "19:00",
                  "endTime": "21:30",
                  "speaking": false,
                  "infoUrl": ""
                }
                """;

        GatheringChanged event = deserializeLegacy(json, "GatheringChanged", GatheringChanged.class);

        assertThat(event.startsAt())
                .as("19:00 JST is 10:00Z")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 19, 0), ZoneId.of("Asia/Tokyo")));
        assertThat(event.endsAt())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 21, 30), ZoneId.of("Asia/Tokyo")));
        assertThat(event.title())
                .isEqualTo("Tokyo Rubyist Meetup");
    }

    @Test
    void legacyHotelBookedScalarDatetimesAreUpcastFromTheAddressZone() {
        String json = """
                {
                  "hotelBookingId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "hotelName": "Hotel Amsterdam",
                  "address": {
                    "street": "1 Dam Square",
                    "city": "Amsterdam",
                    "region": "",
                    "postalCode": "1012",
                    "country": "Netherlands",
                    "locationForMatching": "Amsterdam"
                  },
                  "checkIn": "2026-06-17T15:00:00",
                  "checkOut": "2026-06-20T11:00:00",
                  "bookingIntent": "FINAL",
                  "mapsUrl": ""
                }
                """;

        HotelBooked event = deserializeLegacy(json, "HotelBooked", HotelBooked.class);

        assertThat(event.checkIn())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 6, 17, 15, 0), ZoneId.of("Europe/Amsterdam")));
        assertThat(event.checkOut())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 6, 20, 11, 0), ZoneId.of("Europe/Amsterdam")));
    }

    @Test
    void legacyTrainBookedUpcastsEachEndpointInItsOwnStationZone() {
        String json = """
                {
                  "tripId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "departureStation": {
                    "name": "Frankfurt Hbf", "city": "Frankfurt", "country": "Germany", "mapsUrl": ""
                  },
                  "departureDateTime": "2026-06-28T09:00:00",
                  "arrivalStation": {
                    "name": "Paris Est", "city": "Paris", "country": "France", "mapsUrl": ""
                  },
                  "arrivalDateTime": "2026-06-28T13:00:00",
                  "serviceId": "ICE 9553"
                }
                """;

        TrainBooked event = deserializeLegacy(json, "TrainBooked", TrainBooked.class);

        assertThat(event.departureDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Paris"));
    }

    @Test
    void legacyFlightBookedUpcastsEachEndpointFromItsAirportCode() {
        String json = """
                {
                  "flightId": {"id": "77777777-7777-7777-7777-777777777777"},
                  "airline": "United",
                  "flightNumber": "UA59",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": "2026-06-06T15:55:00",
                  "arrivalAirport": {"code": "FRA"},
                  "arrivalDateTime": "2026-06-07T11:45:00"
                }
                """;

        FlightBooked event = deserializeLegacy(json, "FlightBooked", FlightBooked.class);

        assertThat(event.departureDateTime())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 6, 6, 15, 55), ZoneId.of("America/Los_Angeles")));
        assertThat(event.arrivalDateTime())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 6, 7, 11, 45), ZoneId.of("Europe/Berlin")));
    }

    private static <T> T deserialize(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    /**
     * Binds a payload written in a pre-migration shape the way production reads it: through
     * {@link EventPayloadUpcaster} first. Deliberately still uses the strict {@link #MAPPER}, so a
     * legacy key the upcaster forgot to consume fails the test rather than being ignored.
     */
    private static <T> T deserializeLegacy(String json, String logicalType, Class<T> type) {
        JsonNode upcast = UPCASTER.upcast(logicalType, MAPPER.readTree(json));
        return MAPPER.treeToValue(upcast, type);
    }
}
