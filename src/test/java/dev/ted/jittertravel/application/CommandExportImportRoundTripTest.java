package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
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
    @Autowired TrainBooking trainBooking;
    @Autowired GatheringPlanning gatheringPlanning;
    @Autowired ConferenceMigrationService conferenceMigrationService;
    @Autowired CommandImporter commandImporter;
    @Autowired PostgresPersister persister;

    @Test
    void everyCommandTypeSurvivesExportImportRoundTrip() {
        // one of every command type
        String flightId = UUID.randomUUID().toString();
        flightBooking.bookFlight(bookFlight(flightId));
        changeFlight.changeFlight(changeFlight(flightId));
        hotelBooking.bookHotel(bookHotel());
        trainBooking.bookTrain(bookTrain());
        conferencePlanning.planConference(planConference(UUID.randomUUID().toString(),
                FUTURE.atTime(9, 0), FUTURE.plusDays(2).atTime(17, 0)));  // multi-day, stays tentative
        gatheringPlanning.planGathering(planGathering());

        // single-day conference that we then migrate to a gathering
        String migratedConferenceId = UUID.randomUUID().toString();
        conferencePlanning.planConference(planConference(migratedConferenceId,
                FUTURE.atTime(9, 0), FUTURE.atTime(17, 0)));
        conferenceMigrationService.migrateToGathering(ConferenceId.of(UUID.fromString(migratedConferenceId)), true);

        gatheringPlanning.clearConflict(GatheringId.random(), ConferenceId.random(), "Attending virtually");

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

    private static BookHotelRequest bookHotel() {
        BookHotelRequest r = new BookHotelRequest();
        r.setHotelBookingId(UUID.randomUUID().toString());
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

    private static BookTrainRequest bookTrain() {
        BookTrainRequest r = new BookTrainRequest();
        r.setTrainTripId(UUID.randomUUID().toString());
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

    private static PlanTentativeConferenceRequest planConference(String conferenceId,
                                                                 java.time.LocalDateTime start,
                                                                 java.time.LocalDateTime end) {
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

    private static PlanGatheringRequest planGathering() {
        PlanGatheringRequest r = new PlanGatheringRequest();
        r.setGatheringId(UUID.randomUUID().toString());
        r.setTitle("London Java Community");
        r.setVenueName("Skills Matter");
        r.setStreet("1 Example St");
        r.setCity("London");
        r.setRegion("");
        r.setPostalCode("EC1A 1BB");
        r.setCountry("GB");
        r.setLocationForMatching("London");
        r.setDate(FUTURE);
        r.setStartTime(LocalTime.of(18, 0));
        r.setEndTime(LocalTime.of(21, 0));
        r.setSpeaking(true);
        r.setInfoUrl("https://meetup.com/ljc/events/123");
        return r;
    }
}
