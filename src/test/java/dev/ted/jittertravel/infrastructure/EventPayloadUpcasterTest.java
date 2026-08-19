package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
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
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The composite's own contract: it normalizes the wire id, drives the version ladder from the stored
 * {@code schema_version}, and fails loud when the ladder is broken. The per-rung migration mechanics
 * live in the individual {@code *UpcasterTest}s; here we assert only the composition — that the right
 * rungs run, from the right floor, in the right order.
 */
class EventPayloadUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final EventPayloadUpcaster upcaster =
            EventPayloadUpcaster.standard(new LocationZoneResolver(), new AirportZoneResolver(), mapper);

    // ---- Version-driven laddering ---------------------------------------------------------------

    @Test
    void climbsEveryRungFromVersionOneWhenTheStampIsAbsent() {
        // A legacy (unstamped ⇒ version 1) conference walks BOTH rungs: datetime (v1→v2) then format
        // (v2→v3). Proving both ran in one climb is the composition test the per-rung tests cannot be.
        JsonNode result = upcaster.upcast("ConferencePlanned", legacyScalarConference(), null);

        assertThat(result.get("startDate").isObject())
                .as("the v1→v2 datetime rung ran: startDate is now a {utc,zone} object")
                .isTrue();
        assertThat(result.get("format").asString())
                .as("the v2→v3 format rung ran too: the default was injected")
                .isEqualTo("CALL_FOR_PAPERS");
    }

    @Test
    void startsFromTheStoredVersionAndSkipsRungsAlreadyPassed() {
        // Same legacy scalar payload, but stamped version 2: the climb must start at 2, so ONLY the
        // format rung runs. The datetime rung (v1) is skipped, leaving startDate the bare scalar it
        // came in as — the signal that the stored version, not the payload shape, drove the climb.
        JsonNode result = upcaster.upcast("ConferencePlanned", legacyScalarConference(), 2);

        assertThat(result.get("startDate").isString())
                .as("the v1 datetime rung was skipped: startDate is still the bare scalar")
                .isTrue();
        assertThat(result.get("format").asString())
                .as("only the v2 format rung ran")
                .isEqualTo("CALL_FOR_PAPERS");
    }

    @Test
    void aRowAlreadyAtTheCurrentVersionDoesNoWork() {
        ObjectNode current = (ObjectNode) mapper.readTree("""
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
        JsonNode before = current.deepCopy();

        JsonNode result = upcaster.upcast("ConferencePlanned", current, 3);

        assertThat(result)
                .as("a row stamped at the current version is returned untouched")
                .isEqualTo(before);
    }

    @Test
    void nonObjectPayloadIsReturnedUntouched() {
        JsonNode scalar = mapper.readTree("\"not an object\"");

        assertThat(upcaster.upcast("HotelBooked", scalar, null))
                .isSameAs(scalar);
    }

    // ---- Failure modes (fake / degenerate ladders) ----------------------------------------------

    @Test
    void aMissingRungFailsLoudRatherThanBindingAStaleShape() {
        // An empty ladder cannot advance HotelBooked from v1 toward its current v2 — the signal that
        // a rung was retired before its rows were migrated past it.
        EventPayloadUpcaster broken = new EventPayloadUpcaster(List.of());

        assertThatThrownBy(() -> broken.upcast("HotelBooked", legacyScalarHotel(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HotelBooked")
                .hasMessageContaining("schema version 1");
    }

    @Test
    void twoRungsClaimingTheSameStepFailLoud() {
        WallClockZoning zoning = new WallClockZoning(mapper);
        EventPayloadUpcaster ambiguous = new EventPayloadUpcaster(List.of(
                new HotelTimeZoneUpcaster(new LocationZoneResolver(), zoning),
                new HotelTimeZoneUpcaster(new LocationZoneResolver(), zoning)));

        assertThatThrownBy(() -> ambiguous.upcast("HotelBooked", legacyScalarHotel(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two upcasters")
                .hasMessageContaining("HotelBooked");
    }

    @Test
    void anUnknownWireIdFailsLoudRatherThanSilentlySkippingAStoredEvent() {
        assertThatThrownBy(() -> upcaster.upcast("dev.ted.jittertravel.domain.NeverRegistered",
                legacyScalarHotel(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NeverRegistered");
    }

    @Test
    void aRegisteredTypeWithNoRungsPassesThroughUntouched() {
        // HotelBookingCancelled is current at version 1 and has no rungs; its FQCN must neither throw
        // nor alter the payload.
        String payload = """
                {"hotelBookingId": {"id": "acaa3fd8-1585-4708-8975-8f4f720a1482"}}
                """;

        JsonNode result = upcaster.upcast(HotelBookingCancelled.class.getName(), mapper.readTree(payload));

        assertThat(result)
                .isEqualTo(mapper.readTree(payload));
    }

    // ---- Retirement simulation: the ladder as it will look after a rung is deleted ---------------
    //
    // Retiring a rung is "delete the class and drop it from standard(...)", and it is safe only
    // because a row still sitting below the deleted rung fails loud instead of binding a stale shape.
    // These pin that safety net now, while every rung is still present: the composite takes its rung
    // list, so a ladder can simply be assembled with one omitted. See
    // docs/RestoreCompatibilityFloorPlan.md — this fail-loud climb is what makes restore refuse a
    // backup that predates a retirement, writing nothing.

    @Test
    void aRetiredRungFailsLoudForARowStillBelowIt() {
        // The conference ladder as it will look once the datetime rung (v1→v2) is retired: the format
        // rung (v2→v3) remains. A row that never climbed to v2 cannot reach v3 — and the composite
        // must say so rather than accept "some rung exists for this type" as good enough.
        EventPayloadUpcaster afterRetirement = new EventPayloadUpcaster(List.of(new ConferenceFormatUpcaster()));

        assertThatThrownBy(() -> afterRetirement.upcast("ConferencePlanned", legacyScalarConference(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No upcaster advances ConferencePlanned from schema version 1")
                .hasMessageContaining("was a rung retired before its rows were migrated?");
    }

    @Test
    void aRetiredRungIsInvisibleToARowAlreadyMigratedPastIt() {
        // The property that makes retirement safe, and the reason the eager migration must run first:
        // the climb starts at the stored version, so it never looks up the deleted rung at all. Same
        // degraded ladder as above; this row is stamped v2, so only the surviving format rung runs.
        EventPayloadUpcaster afterRetirement = new EventPayloadUpcaster(List.of(new ConferenceFormatUpcaster()));

        JsonNode result = afterRetirement.upcast("ConferencePlanned", legacyScalarConference(), 2);

        assertThat(result.get("format").asString())
                .as("the surviving v2→v3 rung still ran")
                .isEqualTo("CALL_FOR_PAPERS");
        assertThat(result.get("startDate").isString())
                .as("the retired v1→v2 rung was never looked up: startDate is untouched")
                .isTrue();
    }

    @Test
    void retiringOneTypesRungLeavesEveryOtherTypeClimbing() {
        // Retirement is per (type, version), so deleting the hotel rung must not disturb the train
        // one. A ladder with the hotel rung omitted: hotels below it fail loud, trains still climb.
        WallClockZoning zoning = new WallClockZoning(mapper);
        EventPayloadUpcaster hotelRungRetired = new EventPayloadUpcaster(List.of(
                new TrainTimeZoneUpcaster(new LocationZoneResolver(), zoning),
                new ConferenceFormatUpcaster()));

        assertThatThrownBy(() -> hotelRungRetired.upcast("HotelBooked", legacyScalarHotel(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No upcaster advances HotelBooked from schema version 1");

        JsonNode train = hotelRungRetired.upcast("TrainBooked", legacyScalarTrain(), null);

        assertThat(train.get("departureDateTime").isObject())
                .as("the surviving train rung is unaffected by the hotel retirement")
                .isTrue();
    }

    // ---- Wire-id normalization: a legacy FQCN `type` must reach the same rungs as the logical name -
    //
    // Production event_log is mixed-format: rows written before the logical-name migration store the
    // FQCN (e.g. "dev.ted.jittertravel.domain.HotelBooked"), newer rows store the logical name. Those
    // legacy FQCN rows are also exactly the ones still holding bare-scalar datetimes, so the composite
    // must normalize the wire id to the logical name before it looks up the rungs.

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
    }

    @Test
    void aRenamedTypesRetiredWireIdsStillClimbTheirLadder() {
        // ConferenceTentativelyPlanned was renamed to ConferencePlanned on 2026-08-19; stored rows
        // keep both retired wire ids (the old logical name, and the older FQCN). Each must normalize
        // to the new logical name, or those rows silently stop being upcast — bare-scalar datetimes
        // and no format, exactly the shape that fails to bind.
        for (String retiredWireId : List.of("ConferenceTentativelyPlanned",
                                            "dev.ted.jittertravel.domain.ConferenceTentativelyPlanned")) {
            JsonNode result = upcaster.upcast(retiredWireId, legacyScalarConference(), null);

            assertThat(result.get("startDate").isObject())
                    .as("%s: the datetime rung must run for the retired wire id too", retiredWireId)
                    .isTrue();
            assertThat(result.get("format").asString())
                    .as("%s: and so must the format rung", retiredWireId)
                    .isEqualTo("CALL_FOR_PAPERS");
        }
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
        // {utc, zone} object, not the bare scalar it started as. This is the assertion that fails for
        // an un-normalized wire id — equality alone would not, since two skipped payloads also match.
        assertThat(viaFqcn.get(movedDatetimeField).isObject())
                .as("%s: the FQCN-keyed upcast must produce the {utc,zone} object shape", type.getName())
                .isTrue();
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
                Arguments.of(ConferencePlanned.class, "startDate", """
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

    private ObjectNode legacyScalarConference() {
        return (ObjectNode) mapper.readTree("""
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
                """);
    }

    private ObjectNode legacyScalarTrain() {
        return (ObjectNode) mapper.readTree("""
                {
                  "tripId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "departureStation": {"name": "Paris Est", "city": "Paris",
                    "country": "France", "mapsUrl": null},
                  "departureDateTime": "2026-06-09T14:30:00",
                  "arrivalStation": {"name": "Frankfurt Hbf", "city": "Frankfurt",
                    "country": "Germany", "mapsUrl": null},
                  "arrivalDateTime": "2026-06-09T18:15:00"
                }
                """);
    }

    private ObjectNode legacyScalarHotel() {
        return (ObjectNode) mapper.readTree("""
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Milton Mill House",
                  "address": {"street": "Milton Hill", "city": "Steventon", "region": "Oxfordshire",
                    "postalCode": "OX13 6AF", "country": "UK", "locationForMatching": "Steventon"},
                  "checkIn": "2026-06-17T15:00:00",
                  "checkOut": "2026-06-21T11:00:00",
                  "bookingIntent": "FINAL"
                }
                """);
    }
}
