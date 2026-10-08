package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.TrainBooked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The composite's own contract: it normalizes the wire id, drives the version ladder from the stored
 * {@code schema_version}, and fails loud when the ladder is broken. The per-rung migration mechanics
 * live in the individual {@code *UpcasterTest}s; here we assert only the composition — that the right
 * rungs run, from the right floor, in the right order.
 * <p>
 * The ladder today is two rungs: the conference {@code format} rung (v2→v3) and the location-codes
 * rung (one version up for each address-bearing type). The datetime rungs below them were retired on
 * 2026-10-08, so a row from before zones were stored now fails loud, and these tests pin that too.
 * Payloads here carry only the fields the rungs read; binding is the golden tests' job.
 */
class EventPayloadUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final EventPayloadUpcaster upcaster = EventPayloadUpcaster.standard();

    // ---- Version-driven laddering ---------------------------------------------------------------

    @Test
    void climbsEveryRungAboveTheStoredVersion() {
        // A conference stamped v2 walks BOTH rungs: format (v2→v3) then location codes (v3→v4).
        // Proving both ran in one climb is the composition test the per-rung tests cannot be.
        JsonNode result = upcaster.upcast("ConferencePlanned", conferenceAtVersion2(), 2);

        assertThat(result.get("format").asString())
                .as("the v2→v3 format rung ran: the default was injected")
                .isEqualTo("CALL_FOR_PAPERS");
        assertThat(result.get("venueAddress").get("country").asString())
                .as("the v3→v4 location-codes rung ran too: the country is a code")
                .isEqualTo("US");
    }

    @Test
    void startsFromTheStoredVersionAndSkipsRungsAlreadyPassed() {
        // The same payload stamped v3: the climb must start at 3, so ONLY the location-codes rung runs.
        // The format rung (v2) is skipped, leaving format absent — the signal that the stored version,
        // not the payload shape, drove the climb.
        JsonNode result = upcaster.upcast("ConferencePlanned", conferenceAtVersion2(), 3);

        assertThat(result.has("format"))
                .as("the v2 format rung was skipped: no format was injected")
                .isFalse();
        assertThat(result.get("venueAddress").get("country").asString())
                .as("only the v3 location-codes rung ran")
                .isEqualTo("US");
    }

    @Test
    void aRowAlreadyAtTheCurrentVersionDoesNoWork() {
        ObjectNode current = (ObjectNode) mapper.readTree("""
                {
                  "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "venueAddress": {"city": "San Francisco", "region": "CA", "country": "USA"},
                  "format": "OPEN_SPACE"
                }
                """);
        JsonNode before = current.deepCopy();

        JsonNode result = upcaster.upcast("ConferencePlanned", current, 4);

        assertThat(result)
                .as("a row stamped at the current version is returned untouched, even a country name")
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
        // An empty ladder cannot advance HotelBooked from v2 toward its current v3 — the signal that
        // a rung was retired before its rows were migrated past it.
        EventPayloadUpcaster broken = new EventPayloadUpcaster(List.of());

        assertThatThrownBy(() -> broken.upcast("HotelBooked", hotelAtVersion2(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HotelBooked")
                .hasMessageContaining("schema version 2");
    }

    @Test
    void twoRungsClaimingTheSameStepFailLoud() {
        EventPayloadUpcaster ambiguous = new EventPayloadUpcaster(List.of(
                new LocationCodesUpcaster(),
                new LocationCodesUpcaster()));

        assertThatThrownBy(() -> ambiguous.upcast("HotelBooked", hotelAtVersion2(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two upcasters")
                .hasMessageContaining("HotelBooked");
    }

    @Test
    void anUnknownWireIdFailsLoudRatherThanSilentlySkippingAStoredEvent() {
        assertThatThrownBy(() -> upcaster.upcast("dev.ted.jittertravel.domain.NeverRegistered",
                hotelAtVersion2(), 2))
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

    // ---- Retirement: the safety net that makes deleting a rung safe ------------------------------
    //
    // Retiring a rung is "delete the class and drop it from standard(...)", and it is safe only
    // because a row still sitting below the deleted rung fails loud instead of binding a stale shape.
    // See docs/RestoreCompatibilityFloorPlan.md — this fail-loud climb is what makes restore refuse a
    // backup that predates a retirement, writing nothing.

    @Test
    void aRowFromBeforeZonesWereStoredFailsLoudNowThatTheDatetimeRungsAreRetired() {
        // Byte-for-byte the production row (event_log sequence 1) that took the 2026-08-16 deploy to
        // read-only: an unstamped FQCN type with bare wall-clock datetimes. It sits in the backups
        // from before 2026-08-19, which were retired with the datetime rungs (Ted, 2026-10-08).
        String preZone = """
                {
                  "address": {"city": "Cologne", "region": "", "country": "Germany"},
                  "checkIn": "2026-06-07T15:00:00",
                  "checkOut": "2026-06-08T11:00:00",
                  "hotelBookingId": {"id": "acaa3fd8-1585-4708-8975-8f4f720a1482"}
                }
                """;

        assertThatThrownBy(() -> upcaster.upcast(HotelBooked.class.getName(), mapper.readTree(preZone)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No upcaster advances HotelBooked from schema version 1")
                .hasMessageContaining("was a rung retired before its rows were migrated?");
    }

    @Test
    void retiringOneRungLeavesEveryRowAboveItClimbing() {
        // Retirement is per (type, version). The ladder as it will look once the format rung goes:
        // a conference still below it fails loud, while a hotel, which never needed it, still climbs.
        EventPayloadUpcaster formatRungRetired = new EventPayloadUpcaster(List.of(new LocationCodesUpcaster()));

        assertThatThrownBy(() -> formatRungRetired.upcast("ConferencePlanned", conferenceAtVersion2(), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No upcaster advances ConferencePlanned from schema version 2");

        JsonNode hotel = formatRungRetired.upcast("HotelBooked", hotelAtVersion2(), 2);

        assertThat(hotel.get("address").get("country").asString())
                .as("the hotel's own rung is unaffected by the conference retirement")
                .isEqualTo("GB");
    }

    // ---- Wire-id normalization: a legacy FQCN `type` must reach the same rungs as the logical name -
    //
    // Production event_log was mixed-format: rows written before the logical-name migration stored
    // the FQCN (e.g. "dev.ted.jittertravel.domain.HotelBooked"), and the backups kept from that era
    // still do. The composite must normalize the wire id before it looks up the rungs.

    // ConferenceTentativelyPlanned was renamed to ConferencePlanned on 2026-08-19, and the 2026-08-19
    // backup still holds both retired wire ids (the old logical name, and the older FQCN) at v2. Each
    // must normalize to the new logical name, or those rows silently stop being upcast.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ConferenceTentativelyPlanned",
                            "dev.ted.jittertravel.domain.ConferenceTentativelyPlanned"})
    void aRenamedTypesRetiredWireIdsStillClimbTheirLadder(String retiredWireId) {
        JsonNode result = upcaster.upcast(retiredWireId, conferenceAtVersion2(), 2);

        assertThat(result.get("format").asString())
                .as("%s: the format rung must run for the retired wire id too", retiredWireId)
                .isEqualTo("CALL_FOR_PAPERS");
        assertThat(result.get("venueAddress").get("country").asString())
                .as("%s: and so must the location-codes rung", retiredWireId)
                .isEqualTo("US");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("addressBearingPayloadsAtVersion2")
    void everyAddressBearingTypeUpcastsIdenticallyWhetherKeyedByLegacyFqcnOrLogicalName(
            Class<? extends Event> type,
            String placeField,
            String expectedCountry,
            String json) {
        JsonNode viaFqcn = upcaster.upcast(type.getName(), mapper.readTree(json), 2);
        JsonNode viaLogical = upcaster.upcast(type.getSimpleName(), mapper.readTree(json), 2);

        // The FQCN path must actually migrate (not silently skip): the country is now a code. This is
        // the assertion that fails for an un-normalized wire id — equality alone would not, since two
        // skipped payloads also match.
        assertThat(viaFqcn.get(placeField).get("country").asString())
                .as("%s: the FQCN-keyed upcast must turn the country into a code", type.getName())
                .isEqualTo(expectedCountry);
        assertThat(viaFqcn)
                .as("%s: FQCN and logical-name wire ids must upcast to the same payload", type.getName())
                .isEqualTo(viaLogical);
    }

    static Stream<Arguments> addressBearingPayloadsAtVersion2() {
        return Stream.of(
                Arguments.of(HotelBooked.class, "address", "GB", """
                        {"address": {"city": "Steventon", "region": "Oxfordshire", "country": "UK"}}
                        """),
                Arguments.of(TrainBooked.class, "departureStation", "FR", """
                        {"departureStation": {"name": "Paris Est", "city": "Paris", "country": "France"},
                         "arrivalStation": {"name": "Frankfurt Hbf", "city": "Frankfurt", "country": "Germany"}}
                        """),
                Arguments.of(ConferencePlanned.class, "venueAddress", "US", """
                        {"venueAddress": {"city": "San Francisco", "region": "CA", "country": "USA"}}
                        """),
                Arguments.of(GatheringPlanned.class, "location", "GB", """
                        {"location": {"city": "London", "region": "", "country": "United Kingdom"}}
                        """));
    }

    private ObjectNode conferenceAtVersion2() {
        return (ObjectNode) mapper.readTree("""
                {
                  "conferenceId": {"id": "66666666-6666-6666-6666-666666666666"},
                  "venueAddress": {"city": "San Francisco", "region": "CA", "country": "USA"}
                }
                """);
    }

    private ObjectNode hotelAtVersion2() {
        return (ObjectNode) mapper.readTree("""
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "address": {"city": "Steventon", "region": "Oxfordshire", "country": "UK"}
                }
                """);
    }
}
