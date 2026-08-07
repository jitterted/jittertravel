package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end export/import round trip across <em>every</em> command type. Creates one of each
 * through the real application services, exports, truncates, imports, and asserts the original
 * domain events are reproduced exactly. This is the test that would have caught the missing
 * PlanGatheringRequest import branch and the migrate-to-gathering command being stored as a
 * raw Map. Add a command here whenever a new command type is introduced.
 */
@SpringBootTest
class CommandExportImportRoundTripTest extends AbstractTestcontainerIntegrationTest {

    private static final LocalDate FUTURE = LocalDate.now().plusMonths(3);

    @Autowired ConferencePlanning conferencePlanning;
    @Autowired FlightBooking flightBooking;
    @Autowired ChangeFlight changeFlight;
    @Autowired HotelBooking hotelBooking;
    @Autowired ChangeHotel changeHotel;
    @Autowired TrainBooking trainBooking;
    @Autowired ChangeTrain changeTrain;
    @Autowired GatheringPlanning gatheringPlanning;
    @Autowired ChangeGathering changeGathering;
    @Autowired ConferenceMigrationService conferenceMigrationService;
    @Autowired CommandImporter commandImporter;
    @Autowired PostgresPersister persister;

    @Test
    void everyCommandTypeSurvivesExportImportRoundTrip() {
        // one of every command type
        String flightId = UUID.randomUUID().toString();
        flightBooking.bookFlight(bookFlight(flightId), Instant.now());
        changeFlight.changeFlight(UUID.randomUUID(), changeFlight(flightId), Instant.now());
        String hotelBookingId = UUID.randomUUID().toString();
        hotelBooking.bookHotel(bookHotel(hotelBookingId), Instant.now());
        changeHotel.changeHotel(UUID.randomUUID(), changeHotel(hotelBookingId), Instant.now());
        String trainTripId = UUID.randomUUID().toString();
        trainBooking.bookTrain(bookTrain(trainTripId), Instant.now());
        changeTrain.changeTrain(UUID.randomUUID(), changeTrain(trainTripId), Instant.now());
        conferencePlanning.planConference(planConference(UUID.randomUUID().toString(),
                FUTURE.atTime(9, 0), FUTURE.plusDays(2).atTime(17, 0)), Instant.now());  // multi-day, stays tentative
        String gatheringId = UUID.randomUUID().toString();
        gatheringPlanning.planGathering(planGathering(gatheringId), Instant.now());
        changeGathering.changeGathering(UUID.randomUUID(), changeGathering(gatheringId), Instant.now());

        // single-day conference that we then migrate to a gathering
        String migratedConferenceId = UUID.randomUUID().toString();
        conferencePlanning.planConference(planConference(migratedConferenceId,
                FUTURE.atTime(9, 0), FUTURE.atTime(17, 0)), Instant.now());
        conferenceMigrationService.migrateToGathering(ConferenceId.of(UUID.fromString(migratedConferenceId)), true);

        gatheringPlanning.clearConflict(GatheringId.random(), ConferenceId.random(), "Attending virtually", UUID.randomUUID());

        List<Event> before = currentEvents();
        assertThat(before)
                .as("sanity: the bug-relevant events were produced")
                .hasAtLeastOneElementOfType(GatheringPlanned.class)
                .hasAtLeastOneElementOfType(ConferenceCancelled.class)
                .hasAtLeastOneElementOfType(DifferentCityConflictCleared.class);

        String exported = commandImporter.exportJson();

        persister.truncateAllTables();
        assertThat(currentEvents())
                .as("database cleared before import")
                .isEmpty();

        CommandImporter.ImportResult result = commandImporter.importJson(exported);

        assertThat(result.hasErrors())
                .as("import errors: %s", result.errors())
                .isFalse();

        assertThat(currentEvents())
                .as("every exported command re-produced its original events on import")
                .containsExactlyInAnyOrderElementsOf(before);
    }

    /**
     * A backup written before the UTC migration has no {@code zone} field anywhere — that absence
     * is the whole backward-compatibility strategy: requests kept their scalar wall-clock fields,
     * so "no zone" means "derive it from the location" and old files import with no command-path
     * upcaster at all. The venues here are deliberately far from the UTC-pinned test JVM, so a zone
     * silently read from the server instead of the location shows up as a wrong instant.
     */
    @Test
    void legacyZoneLessCommandsImportAndDeriveTheirZonesFromTheLocation() {
        String legacyBackup = """
                [
                  {"type": "PlanGathering", "payload": {
                    "gatheringId": "77777777-7777-7777-7777-777777777777",
                    "title": "Tokyo Rubyist Meetup", "venueName": "Shibuya Hikarie",
                    "street": "2-21-1 Shibuya", "city": "Tokyo", "region": "",
                    "postalCode": "150-8510", "country": "Japan", "locationForMatching": "Tokyo",
                    "date": "2026-09-15", "startTime": "19:00", "endTime": "21:30",
                    "speaking": false, "infoUrl": ""
                  }},
                  {"type": "PlanTentativeConference", "payload": {
                    "conferenceId": "88888888-8888-8888-8888-888888888888",
                    "name": "JitterConf",
                    "startDate": "2026-09-15T09:00:00", "endDate": "2026-09-17T17:00:00",
                    "venueName": "Moscone Center", "venueStreet": "747 Howard St",
                    "venueCity": "San Francisco", "venueState": "CA",
                    "venueCountry": "USA", "venuePostalCode": "94103"
                  }}
                ]
                """;

        CommandImporter.ImportResult result = commandImporter.importJson(legacyBackup);

        assertThat(result.hasErrors())
                .as("a pre-migration backup must import unchanged: %s", result.errors())
                .isFalse();
        GatheringPlanned gathering = onlyEventOfType(GatheringPlanned.class);
        assertThat(gathering.startsAt())
                .as("19:00 in Tokyo is 10:00Z, derived from the venue's country")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 19, 0), ZoneId.of("Asia/Tokyo")));
        assertThat(gathering.endsAt())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 21, 30), ZoneId.of("Asia/Tokyo")));

        ConferenceTentativelyPlanned conference = onlyEventOfType(ConferenceTentativelyPlanned.class);
        assertThat(conference.startDate())
                .as("09:00 in San Francisco is 16:00Z, derived from the venue city")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 15, 9, 0), ZoneId.of("America/Los_Angeles")));
        assertThat(conference.endDate())
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 9, 17, 17, 0), ZoneId.of("America/Los_Angeles")));
    }

    /**
     * The venues that broke a real production import: small towns in multi-zone countries, where the
     * city table has no entry and the country is ambiguous by construction. Their zone comes from the
     * state/province the address already carries — abbreviated in one entry and spelled out in the
     * other, because stored data uses both.
     */
    @Test
    void conferencesInUnlistedTownsResolveTheirZoneFromTheStateOrProvince() {
        String backup = """
                [
                  {"type": "PlanTentativeConference", "payload": {
                    "conferenceId": "99999999-9999-9999-9999-999999999999",
                    "name": "dev2next",
                    "startDate": "2026-10-12T09:00:00", "endDate": "2026-10-15T16:00:00",
                    "venueName": "Denver Marriott South at Park Meadows",
                    "venueStreet": "10345 Park Meadows Drive",
                    "venueCity": "Lone Tree", "venueState": "CO",
                    "venueCountry": "USA", "venuePostalCode": "80124"
                  }},
                  {"type": "PlanTentativeConference", "payload": {
                    "conferenceId": "aaaaaaaa-9999-9999-9999-999999999999",
                    "name": "PLoP Conference",
                    "startDate": "2026-10-19T09:00:00", "endDate": "2026-10-22T17:00:00",
                    "venueName": "Strathmere Country Retreat",
                    "venueStreet": "1980 Phelan Road W",
                    "venueCity": "North Gower", "venueState": "Ontario",
                    "venueCountry": "Canada", "venuePostalCode": "K0A 2T0"
                  }}
                ]
                """;

        CommandImporter.ImportResult result = commandImporter.importJson(backup);

        assertThat(result.hasErrors())
                .as("a town absent from the city table must still import: %s", result.errors())
                .isFalse();
        assertThat(conferenceNamed("dev2next").startDate())
                .as("09:00 in Lone Tree is 15:00Z — Mountain, from CO")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 10, 12, 9, 0), ZoneId.of("America/Denver")));
        assertThat(conferenceNamed("PLoP Conference").startDate())
                .as("09:00 in North Gower is 13:00Z — Eastern, from Ontario")
                .isEqualTo(ZonedTimestamp.fromLocal(
                        LocalDateTime.of(2026, 10, 19, 9, 0), ZoneId.of("America/Toronto")));
    }

    private ConferenceTentativelyPlanned conferenceNamed(String name) {
        return currentEvents().stream()
                .filter(ConferenceTentativelyPlanned.class::isInstance)
                .map(ConferenceTentativelyPlanned.class::cast)
                .filter(conference -> conference.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no conference named " + name + " was imported"));
    }

    private <T extends Event> T onlyEventOfType(Class<T> type) {
        return currentEvents().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .reduce((a, b) -> { throw new AssertionError("more than one " + type.getSimpleName()); })
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " was imported"));
    }

    private List<Event> currentEvents() {
        return persister.loadAllEvents().stream()
                .map(StoredEvent::payload)
                .toList();
    }

    private static BookFlightRequest bookFlight(String flightId) {
        BookFlightRequest r = new BookFlightRequest();
        r.setFlightId(flightId);
        r.setAirline("United");
        r.setFlightNumber("UA59");
        r.setDepartureAirport("SFO");
        r.setDepartureDateTime(FUTURE.atTime(9, 0));
        r.setArrivalAirport("FRA");
        r.setArrivalDateTime(FUTURE.plusDays(1).atTime(9, 45));
        return r;
    }

    private static ChangeFlightRequest changeFlight(String flightId) {
        ChangeFlightRequest r = new ChangeFlightRequest();
        r.setFlightId(flightId);
        r.setAirline("Lufthansa");
        r.setFlightNumber("LH441");
        r.setDepartureAirport("SFO");
        r.setDepartureDateTime(FUTURE.atTime(11, 0));
        r.setArrivalAirport("MUC");
        r.setArrivalDateTime(FUTURE.plusDays(1).atTime(13, 30));
        r.setReason("Schedule shifted by airline");
        return r;
    }

    private static BookHotelRequest bookHotel(String hotelBookingId) {
        BookHotelRequest r = new BookHotelRequest();
        r.setHotelBookingId(hotelBookingId);
        r.setHotelName("Marriott Downtown");
        r.setStreet("742 Evergreen Terrace");
        r.setCity("San Francisco");
        r.setRegion("CA");
        r.setCountry("USA");
        r.setPostalCode("94103");
        r.setLocationForMatching("San Francisco");
        r.setMapsUrl("");
        r.setCheckIn(FUTURE.atTime(15, 0));
        r.setCheckOut(FUTURE.plusDays(2).atTime(11, 0));
        r.setBookingIntent(BookingIntent.FINAL);
        return r;
    }

    private static ChangeHotelRequest changeHotel(String hotelBookingId) {
        ChangeHotelRequest r = new ChangeHotelRequest();
        r.setHotelBookingId(hotelBookingId);
        r.setHotelName("Hilton Union Square");
        r.setStreet("333 O'Farrell St");
        r.setCity("San Francisco");
        r.setRegion("CA");
        r.setCountry("USA");
        r.setPostalCode("94102");
        r.setLocationForMatching("San Francisco");
        r.setMapsUrl("");
        r.setCheckIn(FUTURE.plusDays(1).atTime(16, 0));
        r.setCheckOut(FUTURE.plusDays(3).atTime(10, 0));
        r.setBookingIntent(BookingIntent.FINAL);
        return r;
    }

    private static BookTrainRequest bookTrain(String tripId) {
        BookTrainRequest r = new BookTrainRequest();
        r.setTrainTripId(tripId);
        r.setServiceId("LNER - Azuma 1A");
        r.setDepartureStationName("London Euston");
        r.setDepartureCityName("London");
        r.setDepartureCountry("UK");
        r.setDepartureMapsUrl("");
        r.setDepartureDateTime(FUTURE.atTime(9, 0));
        r.setArrivalStationName("Manchester Piccadilly");
        r.setArrivalCityName("Manchester");
        r.setArrivalCountry("UK");
        r.setArrivalMapsUrl("");
        r.setArrivalDateTime(FUTURE.atTime(11, 15));
        return r;
    }

    private static ChangeTrainRequest changeTrain(String tripId) {
        ChangeTrainRequest r = new ChangeTrainRequest();
        r.setTrainTripId(tripId);
        r.setServiceId("Avanti - 9M12");
        r.setDepartureStationName("London Kings Cross");
        r.setDepartureCityName("London");
        r.setDepartureCountry("UK");
        r.setDepartureMapsUrl("");
        r.setDepartureDateTime(FUTURE.atTime(10, 30));
        r.setArrivalStationName("Edinburgh Waverley");
        r.setArrivalCityName("Edinburgh");
        r.setArrivalCountry("UK");
        r.setArrivalMapsUrl("");
        r.setArrivalDateTime(FUTURE.atTime(15, 0));
        return r;
    }

    private static PlanTentativeConferenceRequest planConference(String conferenceId,
                                                                 LocalDateTime start,
                                                                 LocalDateTime end) {
        PlanTentativeConferenceRequest r = new PlanTentativeConferenceRequest();
        r.setConferenceId(conferenceId);
        r.setName("JitterConf 2027");
        r.setStartDate(start);
        r.setEndDate(end);
        r.setVenueName("Moscone Center");
        r.setVenueStreet("747 Howard St");
        r.setVenueCity("San Francisco");
        r.setVenueState("CA");
        r.setVenueCountry("USA");
        r.setVenuePostalCode("94103");
        return r;
    }

    private static PlanGatheringRequest planGathering(String gatheringId) {
        PlanGatheringRequest r = new PlanGatheringRequest();
        r.setGatheringId(gatheringId);
        r.setTitle("London Java Community");
        r.setVenueName("Skills Matter");
        r.setStreet("1 Example St");
        r.setCity("London");
        r.setRegion("");
        r.setPostalCode("EC1A 1BB");
        // "United Kingdom", not "GB": the curated zone table keys on country names (what the
        // address parser returns), and a gathering's zone is now derived from this location.
        r.setCountry("United Kingdom");
        r.setLocationForMatching("London");
        r.setDate(FUTURE);
        r.setStartTime(LocalTime.of(18, 0));
        r.setEndTime(LocalTime.of(21, 0));
        r.setSpeaking(true);
        r.setInfoUrl("https://meetup.com/ljc/events/123");
        return r;
    }

    private static ChangeGatheringRequest changeGathering(String gatheringId) {
        ChangeGatheringRequest r = new ChangeGatheringRequest();
        r.setGatheringId(gatheringId);
        r.setTitle("London Java Community — rescheduled");
        r.setVenueName("Federation House");
        r.setStreet("2 New St");
        r.setCity("Manchester");
        r.setRegion("Greater Manchester");
        r.setPostalCode("M1 1AA");
        r.setCountry("United Kingdom");
        r.setLocationForMatching("Manchester");
        r.setDate(FUTURE.plusDays(1));
        r.setStartTime(LocalTime.of(17, 30));
        r.setEndTime(LocalTime.of(20, 0));
        r.setSpeaking(false);
        r.setInfoUrl("https://meetup.com/ljc/events/456");
        return r;
    }
}
