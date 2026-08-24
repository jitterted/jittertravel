package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.CfpOpened;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.InvitedToSpeak;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.TalkSubmitted;
import dev.ted.jittertravel.domain.TalkWithdrawn;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.ZonedTimestamp;
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

    // Built from the production factory and then made stricter, never configured from scratch: a
    // golden sample must bind through the very mapper that reads event_log, or this test certifies
    // a configuration production does not use. The one deliberate difference is the strict setting
    // below, which is what turns a removed or renamed field into a failure.
    private static final JsonMapper MAPPER = EventJsonMapperFactory.create()
            .rebuild()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    private static final EventPayloadUpcaster UPCASTER = EventPayloadUpcaster.standard(
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
    void conferencePlannedLegacyPayloadDeserializes() {
        // Missing locationForMatching defaults to city via compact constructor.
        // This sample carried "state" until 2026-08-23, when that alias was retired: no artifact in
        // rotation uses the spelling. An address field is "region" everywhere now.
        // Keyed by the retired logical name (renamed to ConferencePlanned 2026-08-19): a stored row
        // carries the name it was written under, so the golden sample keeps it.
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
                    "region": "CA",
                    "country": "USA",
                    "postalCode": "94103"
                  }
                }
                """;

        ConferencePlanned event =
                deserializeLegacy(json, "ConferenceTentativelyPlanned", ConferencePlanned.class);

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
        assertThat(event.format())
                .as("a pre-v3 payload with no format upcasts to the safe CALL_FOR_PAPERS default")
                .isEqualTo(ConferenceFormat.CALL_FOR_PAPERS);
    }

    @Test
    void conferenceTentativelyPlannedNewShapePayloadDeserializes() {
        // format is deliberately OPEN_SPACE (not the CALL_FOR_PAPERS default) so the assertion proves
        // the value is read from the payload, not conjured by the default.
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
                  },
                  "format": "OPEN_SPACE"
                }
                """;

        ConferencePlanned event = deserialize(json, ConferencePlanned.class);

        assertThat(event.startDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 9, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.endDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 17, 17, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(event.venueName())
                .isEqualTo("Moscone Center");
        assertThat(event.format())
                .as("a v3 payload's format is read verbatim from the payload")
                .isEqualTo(ConferenceFormat.OPEN_SPACE);
        assertThat(event.infoUrl())
                .as("this sample predates infoUrl, and an absent one must read back as the empty "
                    + "sentinel — which is what let the field ship with no schema bump")
                .isEmpty();
    }

    /**
     * The conference's own web page, added 2026-08-22. Deliberately a <em>second</em> sample rather
     * than a field added to the one above: that one is now the pre-{@code infoUrl} shape and has to
     * keep binding, since it is what every row written before that date looks like.
     * <p>
     * Note the version is still 3. An optional String with an empty-sentinel default needs no rung —
     * the contrast with {@code format} above, which is behavioural and non-null and therefore had to
     * be stored rather than invented at read time.
     */
    @Test
    void conferencePlannedWithInfoUrlDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "name": "J-Fall",
                  "startDate": {"utc": "2026-11-05T08:00:00Z", "zone": "Europe/Amsterdam"},
                  "endDate": {"utc": "2026-11-05T17:00:00Z", "zone": "Europe/Amsterdam"},
                  "venueName": "Reehorst",
                  "venueAddress": {
                    "street": "Bennekomseweg 24",
                    "city": "Ede",
                    "region": "",
                    "country": "Netherlands",
                    "postalCode": "6717 LM",
                    "locationForMatching": "Ede"
                  },
                  "format": "CALL_FOR_PAPERS",
                  "infoUrl": "https://jfall.nl/"
                }
                """;

        ConferencePlanned event = deserialize(json, ConferencePlanned.class);

        assertThat(event.infoUrl())
                .as("the conference's own public page is read verbatim")
                .isEqualTo("https://jfall.nl/");
        assertThat(event.name())
                .isEqualTo("J-Fall");
    }

    @Test
    void conferenceAttendanceDeclinedSampleDeserializes() {
        // Ted's own decision not to attend — distinct from ConferenceCancelled (organizers). declinedOn
        // is a plain audit instant (a moment, not a venue wall-clock), serialized as an ISO instant.
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "reason": "Schedule clash",
                  "declinedOn": "2026-08-16T18:30:00Z"
                }
                """;

        ConferenceAttendanceDeclined event = deserialize(json, ConferenceAttendanceDeclined.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.reason())
                .isEqualTo("Schedule clash");
        assertThat(event.declinedOn())
                .isEqualTo(Instant.parse("2026-08-16T18:30:00Z"));
    }

    @Test
    void conferenceAttendanceConfirmedCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "basis": "SPEAKING_ACCEPTED",
                  "confirmedOn": "2026-08-19T16:45:00Z"
                }
                """;

        ConferenceAttendanceConfirmed event = deserialize(json, ConferenceAttendanceConfirmed.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.basis())
                .isEqualTo(AttendanceBasis.SPEAKING_ACCEPTED);
        assertThat(event.confirmedOn())
                .isEqualTo(Instant.parse("2026-08-19T16:45:00Z"));
    }

    @Test
    void cfpOpenedCurrentPayloadDeserializes() {
        // Born with a ZonedTimestamp, so this is version 1 and there is no pre-zone form to upcast.
        // The zone is the conference's own venue zone, carried so the deadline reads back as the
        // wall-clock the CFP page stated.
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "closesOn": {"utc": "2026-09-12T21:59:00Z", "zone": "Europe/Amsterdam"}
                }
                """;

        CfpOpened event = deserialize(json, CfpOpened.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.closesOn().utc())
                .isEqualTo(Instant.parse("2026-09-12T21:59:00Z"));
        assertThat(event.closesOn().zone())
                .isEqualTo(ZoneId.of("Europe/Amsterdam"));
        assertThat(event.submissionUrl())
                .as("every CFP recorded before 2026-08-22 is this shape — a deadline and nothing "
                    + "else — and an absent URL must read back as the empty sentinel")
                .isEmpty();
    }

    /**
     * Where the talk is submitted, added 2026-08-22 beside the deadline rather than on the
     * conference: one CFP is one fact, and re-recording replaces both together.
     * <p>
     * <strong>OWNER-only</strong>, unlike {@code ConferencePlanned.infoUrl} above — a Sessionize
     * link says Ted is considering submitting somewhere. Nothing enforces that here; this sample
     * pins the wire shape, and {@code PublicCalendarProjectorTest} plus
     * {@code CalendarRedactionSecurityTest} pin that it never reaches an anonymous page.
     */
    @Test
    void cfpOpenedWithSubmissionUrlDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "closesOn": {"utc": "2026-09-12T21:59:00Z", "zone": "Europe/Amsterdam"},
                  "submissionUrl": "https://sessionize.com/jfall-2027/"
                }
                """;

        CfpOpened event = deserialize(json, CfpOpened.class);

        assertThat(event.submissionUrl())
                .isEqualTo("https://sessionize.com/jfall-2027/");
        assertThat(event.closesOn().utc())
                .as("the deadline is unaffected by the field beside it")
                .isEqualTo(Instant.parse("2026-09-12T21:59:00Z"));
    }

    /**
     * The five speaking-axis events share one shape — the conference id and when Ted recorded the
     * fact — so they are exercised together. Each is asserted on its own so a divergence in one
     * cannot hide behind the others.
     */
    @Test
    void talkSubmittedCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "submittedOn": "2026-08-22T10:15:00Z"
                }
                """;

        TalkSubmitted event = deserialize(json, TalkSubmitted.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.submittedOn())
                .isEqualTo(Instant.parse("2026-08-22T10:15:00Z"));
    }

    @Test
    void talkAcceptedCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "decidedOn": "2026-08-22T10:15:00Z"
                }
                """;

        TalkAccepted event = deserialize(json, TalkAccepted.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.decidedOn())
                .isEqualTo(Instant.parse("2026-08-22T10:15:00Z"));
    }

    @Test
    void talkRejectedCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "decidedOn": "2026-08-22T10:15:00Z"
                }
                """;

        TalkRejected event = deserialize(json, TalkRejected.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.decidedOn())
                .isEqualTo(Instant.parse("2026-08-22T10:15:00Z"));
    }

    /**
     * No reason field, deliberately — the original draft carried free text and nothing was ever
     * going to read it. This sample is what would fail if one were added back without a plan.
     */
    @Test
    void talkWithdrawnCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "withdrawnOn": "2026-08-22T10:15:00Z"
                }
                """;

        TalkWithdrawn event = deserialize(json, TalkWithdrawn.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.withdrawnOn())
                .isEqualTo(Instant.parse("2026-08-22T10:15:00Z"));
    }

    @Test
    void invitedToSpeakCurrentPayloadDeserializes() {
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "invitedOn": "2026-08-22T10:15:00Z"
                }
                """;

        InvitedToSpeak event = deserialize(json, InvitedToSpeak.class);

        assertThat(event.conferenceId().id())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.invitedOn())
                .isEqualTo(Instant.parse("2026-08-22T10:15:00Z"));
    }

    @Test
    void oneOffTaskCompletedCurrentPayloadDeserializes() {
        // The taskId is a hand-written registry id, not a UUID: it has to survive the code that
        // declared it being deleted, which is the normal end of a post-deploy task's life.
        String json = """
                {
                  "taskId": "normalize-event-log-type",
                  "completedOn": "2026-08-20T14:00:00Z"
                }
                """;

        OneOffTaskCompleted event = deserialize(json, OneOffTaskCompleted.class);

        assertThat(event.taskId())
                .isEqualTo("normalize-event-log-type");
        assertThat(event.completedOn())
                .isEqualTo(Instant.parse("2026-08-20T14:00:00Z"));
    }

    @Test
    void conferenceAttendanceDeclinedWithoutReasonReadsBackAsEmpty() {
        // A payload written with no reason must read back as "" (no-null-Strings rule), not null.
        String json = """
                {
                  "conferenceId": {"id": "22222222-2222-2222-2222-222222222222"},
                  "declinedOn": "2026-08-16T18:30:00Z"
                }
                """;

        ConferenceAttendanceDeclined event = deserialize(json, ConferenceAttendanceDeclined.class);

        assertThat(event.reason())
                .as("absent reason must be empty string, not null")
                .isEmpty();
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
    void hotelBookedLegacyPayloadWithNoLocationForMatchingDeserializes() {
        // locationForMatching absent → defaults to city. This sample carried "state" for the region
        // until 2026-08-23, when that alias was retired — see EventJsonMapperFactoryTest.
        String json = """
                {
                  "hotelBookingId": {"id": "33333333-3333-3333-3333-333333333333"},
                  "hotelName": "Grand Hotel",
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield",
                    "region": "IL",
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
    void privateEventPlannedSampleDeserializes() {
        // A private social event has no speaking flag and no infoUrl — those are public-event
        // concepts. venueName normalizes to "" when absent (no-null-Strings rule).
        String json = """
                {
                  "privateEventId": {"id": "88888888-8888-8888-8888-888888888888"},
                  "title": "Dinner with the Smiths",
                  "venueName": "Alo",
                  "location": {
                    "street": "163 Spadina Ave",
                    "city": "Toronto",
                    "region": "ON",
                    "postalCode": "M5V 2L6",
                    "country": "Canada",
                    "locationForMatching": "Toronto"
                  },
                  "startsAt": {"utc": "2026-09-15T23:00:00Z", "zone": "America/Toronto"},
                  "endsAt": {"utc": "2026-09-16T02:00:00Z", "zone": "America/Toronto"}
                }
                """;

        PrivateEventPlanned event = deserialize(json, PrivateEventPlanned.class);

        assertThat(event.privateEventId().id())
                .isEqualTo(UUID.fromString("88888888-8888-8888-8888-888888888888"));
        assertThat(event.title())
                .isEqualTo("Dinner with the Smiths");
        assertThat(event.venueName())
                .isEqualTo("Alo");
        assertThat(event.location().city())
                .isEqualTo("Toronto");
        assertThat(event.startsAt().localDateTime().toString())
                .isEqualTo("2026-09-15T19:00");
        assertThat(event.endsAt().localDateTime().toString())
                .isEqualTo("2026-09-15T22:00");
    }

    @Test
    void groundTransferPlannedSampleDeserializes() {
        // Flat by design: two Strings and an Address per end, rather than a sealed TransferPoint
        // that would need Jackson type information in every stored payload. Exactly one of
        // airportCode / name is set at each end, and that is what decides how the end is published.
        String json = """
                {
                  "groundTransferId": {"id": "77777777-7777-7777-7777-777777777777"},
                  "originAirportCode": "DEN",
                  "originName": "",
                  "origin": {
                    "street": "",
                    "city": "Denver",
                    "region": "",
                    "postalCode": "",
                    "country": "",
                    "locationForMatching": "Denver"
                  },
                  "destinationAirportCode": "",
                  "destinationName": "Marriott Lone Tree",
                  "destination": {
                    "street": "10345 Park Meadows Dr",
                    "city": "Lone Tree",
                    "region": "CO",
                    "postalCode": "80124",
                    "country": "US",
                    "locationForMatching": "Lone Tree"
                  },
                  "departsAt": {"utc": "2026-09-14T18:00:00Z", "zone": "America/Denver"},
                  "arrivesAt": {"utc": "2026-09-14T18:45:00Z", "zone": "America/Denver"}
                }
                """;

        GroundTransferPlanned event = deserialize(json, GroundTransferPlanned.class);

        assertThat(event.groundTransferId().id())
                .isEqualTo(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        assertThat(event.originAirportCode())
                .isEqualTo("DEN");
        assertThat(event.originName())
                .isEmpty();
        assertThat(event.origin().locationForMatching())
                .isEqualTo("Denver");
        assertThat(event.destinationAirportCode())
                .isEmpty();
        assertThat(event.destinationName())
                .isEqualTo("Marriott Lone Tree");
        assertThat(event.destination().locationForMatching())
                .as("the match location is what connects the transfer to the stay it serves")
                .isEqualTo("Lone Tree");
        assertThat(event.departsAt().localDateTime().toString())
                .isEqualTo("2026-09-14T12:00");
        assertThat(event.arrivesAt().localDateTime().toString())
                .isEqualTo("2026-09-14T12:45");
        assertThat(event.mode())
                .as("this sample predates the mode field, and a payload without one is not broken "
                    + "by it — that is what makes the addition an additive change")
                .isEmpty();
    }

    /**
     * The same event once a mode has been recorded. The sample above is deliberately left as it
     * was: together the two say that a transfer stored before the field existed and one stored
     * after it both read back correctly.
     */
    @Test
    void groundTransferPlannedWithAModeSampleDeserializes() {
        String json = """
                {
                  "groundTransferId": {"id": "77777777-7777-7777-7777-777777777777"},
                  "originAirportCode": "DEN",
                  "originName": "",
                  "origin": {
                    "street": "",
                    "city": "Denver",
                    "region": "",
                    "postalCode": "",
                    "country": "",
                    "locationForMatching": "Denver"
                  },
                  "destinationAirportCode": "",
                  "destinationName": "Marriott Lone Tree",
                  "destination": {
                    "street": "10345 Park Meadows Dr",
                    "city": "Lone Tree",
                    "region": "CO",
                    "postalCode": "80124",
                    "country": "US",
                    "locationForMatching": "Lone Tree"
                  },
                  "departsAt": {"utc": "2026-09-14T18:00:00Z", "zone": "America/Denver"},
                  "arrivesAt": {"utc": "2026-09-14T18:45:00Z", "zone": "America/Denver"},
                  "mode": "A16 hotel shuttle"
                }
                """;

        GroundTransferPlanned event = deserialize(json, GroundTransferPlanned.class);

        assertThat(event.mode())
                .isEqualTo("A16 hotel shuttle");
    }

    @Test
    void groundTransferCancelledSampleDeserializes() {
        // The id alone: unlike a cancelled hotel there is no reason to record, because a transfer
        // has no booking to explain away — the entry being wrong is the usual reason it is going.
        String json = """
                {
                  "groundTransferId": {"id": "77777777-7777-7777-7777-777777777777"}
                }
                """;

        GroundTransferCancelled event = deserialize(json, GroundTransferCancelled.class);

        assertThat(event.groundTransferId().id())
                .isEqualTo(UUID.fromString("77777777-7777-7777-7777-777777777777"));
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
