package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v1→v2 datetime rung for flights — the one rung wired to the {@link AirportZoneResolver}. Departure
 * and arrival resolve independently from their airport codes, so the two ends (which may be nine
 * hours apart) never share one wrong zone.
 */
class FlightTimeZoneUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final FlightTimeZoneUpcaster upcaster =
            new FlightTimeZoneUpcaster(new AirportZoneResolver(), new WallClockZoning(mapper));

    @Test
    void handlesBothFlightTypesAtVersionOneOnly() {
        assertThat(upcaster.canHandle("FlightBooked", 1))
                .isTrue();
        assertThat(upcaster.canHandle("FlightChanged", 1))
                .isTrue();
        assertThat(upcaster.canHandle("FlightBooked", 2))
                .isFalse();
        assertThat(upcaster.canHandle("HotelBooked", 1))
                .isFalse();
    }

    @Test
    void legacyFlightBookedResolvesEachEndpointFromItsAirportCode() {
        // SFO→FRA: the two endpoints are nine hours apart, so a single shared zone would put one of
        // them badly wrong. SFO 15:55 PDT (-07:00) is 22:55Z; FRA 11:45 CEST (+02:00) is 09:45Z.
        FlightBooked event = upcast("""
                {
                  "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "SFO"},
                  "departureDateTime": "2026-06-06T15:55:00",
                  "arrivalAirport": {"code": "FRA"},
                  "arrivalDateTime": "2026-06-07T11:45:00"
                }
                """, FlightBooked.class);

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
        // FlightChanged carries the same endpoint pair and would fail identically if this rung's
        // canHandle missed it — which nothing else covers.
        FlightChanged event = upcast("""
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
                """, FlightChanged.class);

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
    void newShapePassesThroughUnchanged() {
        // The airport code is deliberately one the curated table does not know: an already-migrated
        // payload must never reach the resolver, so a stored event still replays.
        FlightBooked event = upcast("""
                {
                  "flightId": {"id": "55555555-5555-5555-5555-555555555555"},
                  "airline": "Lufthansa",
                  "flightNumber": "LH441",
                  "departureAirport": {"code": "ZZZ"},
                  "departureDateTime": {"utc": "2026-06-06T22:55:00Z", "zone": "America/Los_Angeles"},
                  "arrivalAirport": {"code": "QQQ"},
                  "arrivalDateTime": {"utc": "2026-06-07T09:45:00Z", "zone": "Europe/Berlin"}
                }
                """, FlightBooked.class);

        assertThat(event.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-06-06T22:55:00Z"));
        assertThat(event.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    private <T extends Event> T upcast(String json, Class<T> type) {
        ObjectNode payload = (ObjectNode) mapper.readTree(json);
        upcaster.upcast(payload, type.getSimpleName());
        return mapper.treeToValue(payload, type);
    }
}
