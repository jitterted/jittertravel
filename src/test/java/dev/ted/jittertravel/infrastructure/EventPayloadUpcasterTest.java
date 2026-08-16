package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.TrainBooked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backward-compatibility contract for the datetime migration: a legacy {@link HotelBooked} payload
 * (bare wall-clock scalars, no zone) must upcast to the current {@code ZonedTimestamp} shape, with
 * the zone derived from the event's own location and the wall-clock preserved as the entry-zone
 * time. New-shape payloads must pass through untouched (idempotency).
 */
class EventPayloadUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final EventPayloadUpcaster upcaster =
            new EventPayloadUpcaster(new LocationZoneResolver(), new AirportZoneResolver(), mapper);

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

    @Test
    void legacyTrainBookedScalarDatetimesUpcastEachEndpointInItsOwnZone() {
        // A Paris→Frankfurt trip: both endpoints happen to be CET (+02:00 in summer), so 14:30
        // local == 12:30Z. Departure resolves from Paris/France, arrival from Frankfurt/Germany —
        // independently.
        String legacy = """
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
                """;

        TrainBooked event = upcastTo(legacy, TrainBooked.class);

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

    @Test
    void legacyFlightBookedResolvesEachEndpointFromItsAirportCode() {
        // SFO→FRA: the two endpoints are nine hours apart, so a single shared zone would put one of
        // them badly wrong. SFO 15:55 PDT (-07:00) is 22:55Z; FRA 11:45 CEST (+02:00) is 09:45Z.
        String legacy = """
                {
                  "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": "2026-06-06T15:55:00",
                  "arrivalAirport": {"code": "FRA"},
                  "arrivalDateTime": "2026-06-07T11:45:00"
                }
                """;

        FlightBooked event = upcastTo(legacy, FlightBooked.class);

        assertThat(event.departureDateTime().zone())
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(event.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-06-06T22:55:00Z"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(event.arrivalDateTime().utc())
                .isEqualTo(Instant.parse("2026-06-07T09:45:00Z"));
        assertThat(event.arrivalDateTime().localDateTime().toString())
                .as("the original arrival wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-06-07T11:45");
    }

    @Test
    void legacyFlightChangedResolvesEachEndpointFromItsAirportCode() {
        // FlightChanged carries the same endpoint pair as FlightBooked and would fail identically
        // if the upcaster's case list missed it — which nothing else covers.
        String legacy = """
                {
                  "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "LHR"},
                  "departureDateTime": "2026-06-06T09:15:00",
                  "arrivalAirport": {"code": "JFK"},
                  "arrivalDateTime": "2026-06-06T12:30:00",
                  "reason": "Schedule shifted by airline"
                }
                """;

        FlightChanged event = upcastTo(legacy, FlightChanged.class);

        assertThat(event.departureDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
        assertThat(event.departureDateTime().utc())
                .as("09:15 BST is 08:15Z")
                .isEqualTo(Instant.parse("2026-06-06T08:15:00Z"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("America/New_York"));
        assertThat(event.arrivalDateTime().utc())
                .as("12:30 EDT is 16:30Z — the westbound flight lands 'before' it departed in "
                    + "wall-clock terms, which only instants make sense of")
                .isEqualTo(Instant.parse("2026-06-06T16:30:00Z"));
    }

    @Test
    void newShapeFlightPassesThroughUnchanged() {
        // The airport code is deliberately one the curated table does not know: an already-migrated
        // payload must never reach the resolver, so a stored event still replays.
        String current = """
                {
                  "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "ZZZ"},
                  "departureDateTime": {"utc": "2026-06-06T22:55:00Z", "zone": "America/Los_Angeles"},
                  "arrivalAirport": {"code": "QQQ"},
                  "arrivalDateTime": {"utc": "2026-06-07T09:45:00Z", "zone": "Europe/Berlin"}
                }
                """;

        FlightBooked event = upcastTo(current, FlightBooked.class);

        assertThat(event.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-06-06T22:55:00Z"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void legacyConferenceResolvesBothEndsFromTheVenueAddress() {
        String legacy = """
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
                """;

        ConferenceTentativelyPlanned event = upcastTo(legacy, ConferenceTentativelyPlanned.class);

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
    void legacyGatheringCollapsesItsDateAndTwoTimesIntoTwoTimestamps() {
        // The one migration that changes the field *set* rather than a field's type: date +
        // startTime + endTime become startsAt + endsAt, both in the venue's zone.
        String legacy = """
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
                """;

        GatheringPlanned event = upcastTo(legacy, GatheringPlanned.class);

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
    void legacyGatheringKeysAreRemovedSoTheRecordCanBind() {
        // Unlike the other migrations, leaving the old keys behind would break binding: the record
        // no longer declares them and the golden contract test rejects unknown properties.
        String legacy = """
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
                """;

        JsonNode upcast = upcaster.upcast("GatheringPlanned", mapper.readTree(legacy));

        assertThat(upcast.has("date") || upcast.has("startTime") || upcast.has("endTime"))
                .as("legacy wall-clock keys must not survive the upcast")
                .isFalse();
        assertThat(upcast.has("startsAt") && upcast.has("endsAt"))
                .as("both replacement timestamps must be present")
                .isTrue();
    }

    @Test
    void newShapeGatheringPassesThroughUnchanged() {
        String current = """
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
                """;

        // Note the unresolvable "GB" country: an already-migrated payload must never reach the
        // resolver, so a stored event whose location the tables don't know still replays.
        GatheringPlanned event = upcastTo(current, GatheringPlanned.class);

        assertThat(event.startsAt().utc())
                .isEqualTo(Instant.parse("2026-09-15T17:30:00Z"));
        assertThat(event.endsAt().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    // ---- Wire-id normalization: legacy FQCN `type` must reach the same case as the logical name ----
    //
    // Production event_log is mixed-format: rows written before the logical-name migration store the
    // FQCN (e.g. "dev.ted.jittertravel.domain.HotelBooked"), newer rows store the logical name
    // ("HotelBooked"). Those legacy FQCN rows are also exactly the ones still holding bare-scalar
    // datetimes. Before the normalization fix, the upcaster switched on the raw wire id, so an FQCN
    // row fell through to `default` unmigrated and boot replay died binding the scalar to
    // ZonedTimestamp. Every test above feeds the logical name, so none of them exercised this path.

    @Test
    void legacyHotelBookedKeyedByItsFqcnIsUpcast_reproducesTheProductionReplayFailure() {
        // Byte-for-byte the production row (event_log sequence 1) that took the 2026-08-16 deploy to
        // read-only: type column is the legacy FQCN, checkIn/checkOut are bare wall-clock scalars.
        // Cologne in June is CEST (+02:00), so 15:00 local == 13:00Z.
        String legacy = """
                {
                  "address": {
                    "city": "Cologne", "region": "",
                    "street": "Stolkgasse / An den Dominikanern 4a", "country": "Germany",
                    "postalCode": "50668", "locationForMatching": "Cologne"
                  },
                  "checkIn": "2026-06-07T15:00:00",
                  "mapsUrl": "",
                  "checkOut": "2026-06-08T11:00:00",
                  "hotelName": "Lindner Hotel Cologne Am Dom",
                  "bookingIntent": "TENTATIVE",
                  "hotelBookingId": {"id": "acaa3fd8-1585-4708-8975-8f4f720a1482"}
                }
                """;

        JsonNode upcast = upcaster.upcast(HotelBooked.class.getName(), mapper.readTree(legacy));
        HotelBooked event = mapper.treeToValue(upcast, HotelBooked.class);

        assertThat(event.checkIn().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(event.checkIn().utc())
                .as("15:00 CEST is 13:00Z")
                .isEqualTo(Instant.parse("2026-06-07T13:00:00Z"));
        assertThat(event.checkIn().localDateTime().toString())
                .as("the original wall-clock is preserved as the entry-zone local time")
                .isEqualTo("2026-06-07T15:00");
        assertThat(event.checkOut().utc())
                .as("11:00 CEST is 09:00Z")
                .isEqualTo(Instant.parse("2026-06-08T09:00:00Z"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("migratedLegacyPayloads")
    void everyMigratedTypeUpcastsIdenticallyWhetherKeyedByLegacyFqcnOrLogicalName(
            Class<? extends Event> type,
            String movedDatetimeField,
            String legacyJson) {
        JsonNode viaFqcn = upcaster.upcast(type.getName(), mapper.readTree(legacyJson));
        JsonNode viaLogical = upcaster.upcast(type.getSimpleName(), mapper.readTree(legacyJson));

        // The FQCN path must actually migrate (not silently skip): the datetime field is now a
        // {utc, zone} object, not the bare scalar it started as. This is the assertion that fails
        // for the pre-fix code — equality alone would not, since two skipped payloads also match.
        assertThat(viaFqcn.get(movedDatetimeField).isObject())
                .as("%s: the FQCN-keyed upcast must produce the {utc,zone} object shape", type.getName())
                .isTrue();
        // And both wire-id forms must agree exactly, so the FQCN path is not merely non-empty but
        // identical to the well-tested logical-name path.
        assertThat(viaFqcn)
                .as("%s: FQCN and logical-name wire ids must upcast to the same payload", type.getName())
                .isEqualTo(viaLogical);
    }

    static Stream<Arguments> migratedLegacyPayloads() {
        return Stream.of(
                Arguments.of(HotelBooked.class, "checkIn", """
                        {
                          "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                          "hotelName": "Milton Mill House",
                          "address": {"street": "Milton Hill", "city": "Steventon",
                            "region": "Oxfordshire", "postalCode": "OX13 6AF", "country": "UK",
                            "locationForMatching": "Steventon"},
                          "checkIn": "2026-06-17T15:00:00",
                          "checkOut": "2026-06-21T11:00:00",
                          "bookingIntent": "FINAL"
                        }
                        """),
                Arguments.of(TrainBooked.class, "departureDateTime", """
                        {
                          "tripId": {"id": "22222222-2222-2222-2222-222222222222"},
                          "departureStation": {"name": "Paris Est", "city": "Paris",
                            "country": "France", "mapsUrl": null},
                          "departureDateTime": "2026-06-09T14:30:00",
                          "arrivalStation": {"name": "Frankfurt Hbf", "city": "Frankfurt",
                            "country": "Germany", "mapsUrl": null},
                          "arrivalDateTime": "2026-06-09T18:15:00"
                        }
                        """),
                Arguments.of(FlightBooked.class, "departureDateTime", """
                        {
                          "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                          "airline": "Lufthansa", "flightNumber": "LH441",
                          "departureAirport": {"code": "SFO"},
                          "departureDateTime": "2026-06-06T15:55:00",
                          "arrivalAirport": {"code": "FRA"},
                          "arrivalDateTime": "2026-06-07T11:45:00"
                        }
                        """),
                Arguments.of(ConferenceTentativelyPlanned.class, "startDate", """
                        {
                          "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                          "name": "JitterConf",
                          "startDate": "2026-09-15T09:00:00",
                          "endDate": "2026-09-17T17:00:00",
                          "venueName": "Moscone Center",
                          "venueAddress": {"street": "747 Howard St", "city": "San Francisco",
                            "region": "CA", "postalCode": "94103", "country": "USA",
                            "locationForMatching": "San Francisco"}
                        }
                        """),
                Arguments.of(GatheringPlanned.class, "startsAt", """
                        {
                          "gatheringId": {"id": "44444444-4444-4444-4444-444444444444"},
                          "title": "London Java Community", "venueName": "Skills Matter",
                          "location": {"street": "1 Example St", "city": "London", "region": "",
                            "postalCode": "EC1A 1BB", "country": "United Kingdom",
                            "locationForMatching": "London"},
                          "date": "2026-09-15", "startTime": "18:30", "endTime": "21:00",
                          "speaking": true, "infoUrl": ""
                        }
                        """));
    }

    @Test
    void legacyFqcnOfAnUnmigratedTypePassesThroughUntouched() {
        // A registered type with no migrated datetime field (HotelBookingCancelled) reaches the
        // `default` branch. Normalizing its FQCN must neither throw nor alter the payload.
        String payload = """
                {"hotelBookingId": {"id": "acaa3fd8-1585-4708-8975-8f4f720a1482"}}
                """;

        JsonNode result = upcaster.upcast(HotelBookingCancelled.class.getName(), mapper.readTree(payload));

        assertThat(result)
                .isEqualTo(mapper.readTree(payload));
    }

    @Test
    void anUnknownWireIdFailsLoudRatherThanSilentlySkippingAStoredEvent() {
        String payload = """
                {"checkIn": "2026-06-07T15:00:00"}
                """;

        assertThatThrownBy(() -> upcaster.upcast("dev.ted.jittertravel.domain.NeverRegistered",
                mapper.readTree(payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NeverRegistered");
    }

    private <T> T upcastTo(String json, Class<T> type) {
        JsonNode upcast = upcaster.upcast(type.getSimpleName(), mapper.readTree(json));
        return mapper.treeToValue(upcast, type);
    }
}
