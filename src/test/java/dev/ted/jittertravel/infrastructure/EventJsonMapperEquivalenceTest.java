package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.TrainBooked;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Safety net for "pinning" the event {@link JsonMapper}: proves that
 * {@link EventJsonMapperFactory#create()} serializes byte-for-byte identically to the mapper
 * Spring Boot currently auto-configures. As long as this passes, replacing the auto-configured
 * bean with the pinned factory cannot change the on-the-wire format of stored events or backups.
 * <p>
 * Samples mirror the real stored shapes covered by
 * {@link dev.ted.jittertravel.domain.GoldenEventDeserializationTest} and exercise the parts of the
 * mapper most likely to differ between configs: {@code LocalDateTime} / {@code LocalDate} /
 * {@code LocalTime} (date module + timestamp-vs-string), nested value records, optional empty
 * strings, and booleans.
 */
@SpringJUnitConfig
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class EventJsonMapperEquivalenceTest {

    /** The mapper Spring Boot auto-configures today — current production behavior. */
    @Autowired
    private JsonMapper autoConfiguredMapper;

    /** The pinned, version-controlled config we want to swap in. */
    private final JsonMapper pinnedMapper = EventJsonMapperFactory.create();

    @Test
    void flightBooked() {
        assertSerializesIdentically("""
                {"flightId":{"id":"11111111-1111-1111-1111-111111111111"},"airline":"United",\
                "flightNumber":"UA59","departureAirport":{"code":"SFO"},\
                "departureDateTime":"2026-06-06T13:55:00","arrivalAirport":{"code":"FRA"},\
                "arrivalDateTime":"2026-06-07T09:45:00"}""", FlightBooked.class);
    }

    @Test
    void hotelBookedWithMapsUrl() {
        assertSerializesIdentically("""
                {"hotelBookingId":{"id":"33333333-3333-3333-3333-333333333333"},"hotelName":"Savoy",\
                "address":{"street":"Strand","city":"London","region":"","postalCode":"WC2R 0EZ",\
                "country":"GB","locationForMatching":"London"},"checkIn":"2026-07-10T15:00:00",\
                "checkOut":"2026-07-12T11:00:00","bookingIntent":"FINAL",\
                "mapsUrl":"https://maps.google.com/?q=place_id:ChIJB9OTMDIbdkgRp0JWR_EVkZM"}""",
                HotelBooked.class);
    }

    @Test
    void trainBookedWithEmptyOptionalStrings() {
        assertSerializesIdentically("""
                {"tripId":{"id":"22222222-2222-2222-2222-222222222222"},\
                "departureStation":{"name":"London Euston","city":"London","country":"UK","mapsUrl":null},\
                "departureDateTime":"2026-06-09T09:00:00",\
                "arrivalStation":{"name":"Manchester Piccadilly","city":"Manchester","country":"UK","mapsUrl":null},\
                "arrivalDateTime":"2026-06-09T11:15:00","serviceId":"DB - ICE 610"}""", TrainBooked.class);
    }

    @Test
    void gatheringPlannedWithDateTimeAndBoolean() {
        assertSerializesIdentically("""
                {"gatheringId":{"id":"44444444-4444-4444-4444-444444444444"},\
                "title":"London Java Community","venueName":"Skills Matter",\
                "location":{"street":"1 Example Street","city":"London","region":"","postalCode":"EC1A 1BB",\
                "country":"GB","locationForMatching":"London"},"date":"2026-09-15","startTime":"18:30",\
                "endTime":"21:00","speaking":true,"infoUrl":"https://example.com/e/1"}""",
                GatheringPlanned.class);
    }

    @Test
    void conferenceTentativelyPlanned() {
        assertSerializesIdentically("""
                {"conferenceId":{"id":"22222222-2222-2222-2222-222222222222"},"name":"JitterConf",\
                "startDate":"2026-09-15T09:00:00","endDate":"2026-09-17T17:00:00",\
                "venueName":"Moscone Center","venueAddress":{"street":"747 Howard St","city":"San Francisco",\
                "region":"CA","country":"USA","postalCode":"94103","locationForMatching":"San Francisco"}}""",
                ConferenceTentativelyPlanned.class);
    }

    /**
     * Deserialize a representative payload with the current auto-configured mapper, then assert
     * the pinned mapper re-serializes the resulting object to the exact same bytes.
     */
    private <T> void assertSerializesIdentically(String json, Class<T> type) {
        T event = autoConfiguredMapper.readValue(json, type);
        String fromAutoConfigured = autoConfiguredMapper.writeValueAsString(event);
        String fromPinned = pinnedMapper.writeValueAsString(event);
        assertThat(fromPinned)
                .as("pinned EventJsonMapperFactory must serialize %s identically to the "
                        + "auto-configured production mapper", type.getSimpleName())
                .isEqualTo(fromAutoConfigured);
    }
}