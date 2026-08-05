package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.TrainBooked;
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

    private <T> T upcastTo(String json, Class<T> type) {
        JsonNode upcast = upcaster.upcast(type.getSimpleName(), mapper.readTree(json));
        return mapper.treeToValue(upcast, type);
    }
}
