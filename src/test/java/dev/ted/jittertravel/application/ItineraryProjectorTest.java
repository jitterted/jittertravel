package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryProjectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);
    private static final LocalDateTime DEPARTURE = DATE.atTime(9, 0);
    private static final LocalDateTime ARRIVAL = DATE.atTime(11, 15);

    @Test
    void firstDateOnOrAfterReturnsTodayWhenNoEntries() {
        ItineraryProjector projector = new ItineraryProjector();

        assertThat(projector.firstDateOnOrAfter(DATE))
                .isEqualTo(DATE);
    }

    @Test
    void firstDateOnOrAfterReturnsTodayWhenAllEntriesAreInPast() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(new FlightBooked(
                FlightId.random(), "BA", "BA1",
                AirportCode.of("SFO"), DATE.minusDays(5).atTime(9, 0),
                AirportCode.of("LHR"), DATE.minusDays(4).atTime(17, 0)))));

        assertThat(projector.firstDateOnOrAfter(DATE))
                .isEqualTo(DATE);
    }

    @Test
    void firstDateOnOrAfterReturnsEarliestFutureEntryDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate nextWeek = DATE.plusWeeks(1);
        LocalDate twoWeeks = DATE.plusWeeks(2);
        projector.handle(Stream.of(
                stored(new FlightBooked(FlightId.random(), "BA", "BA1",
                        AirportCode.of("SFO"), twoWeeks.atTime(9, 0),
                        AirportCode.of("LHR"), twoWeeks.atTime(17, 0))),
                stored(new FlightBooked(FlightId.random(), "UA", "UA2",
                        AirportCode.of("LHR"), nextWeek.atTime(10, 0),
                        AirportCode.of("SFO"), nextWeek.atTime(14, 0)))));

        assertThat(projector.firstDateOnOrAfter(DATE))
                .isEqualTo(nextWeek);
    }

    @Test
    void multiDayFlightAppearsOnBothDepartureDateAndArrivalDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate arrivalDate = DATE.plusDays(1);
        FlightBooked event = new FlightBooked(
                FlightId.random(), "United", "UA58",
                AirportCode.of("SFO"), DATE.atTime(13, 55),
                AirportCode.of("FRA"), arrivalDate.atTime(9, 45));

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE))
                .as("multi-day flight must appear on departure date")
                .hasSize(1);
        assertThat(projector.entriesForDate(arrivalDate))
                .as("multi-day flight must appear on arrival date")
                .hasSize(1);
    }

    @Test
    void sameDayFlightAppearsOnlyOnDepartureDate() {
        ItineraryProjector projector = new ItineraryProjector();
        FlightBooked event = new FlightBooked(
                FlightId.random(), "Ryanair", "FR123",
                AirportCode.of("LHR"), DATE.atTime(7, 0),
                AirportCode.of("AMS"), DATE.atTime(9, 15));

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE))
                .as("same-day flight must appear on departure date")
                .hasSize(1);
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("same-day flight must not appear on any other date")
                .isEmpty();
    }

    @Test
    void multiDayTrainAppearsOnBothDepartureDateAndArrivalDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate arrivalDate = DATE.plusDays(1);
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress edinburgh = new TrainStationAddress("Edinburgh Waverley", "Edinburgh", "UK", "");
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), london, DATE.atTime(23, 45),
                edinburgh, arrivalDate.atTime(7, 30), "Caledonian Sleeper");

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE))
                .as("overnight train must appear on departure date")
                .hasSize(1);
        assertThat(projector.entriesForDate(arrivalDate))
                .as("overnight train must appear on arrival date")
                .hasSize(1);
    }

    @Test
    void sameDayTrainAppearsOnlyOnDepartureDate() {
        ItineraryProjector projector = new ItineraryProjector();
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress manchester = new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), london, DATE.atTime(9, 0),
                manchester, DATE.atTime(11, 15), "");

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE))
                .as("same-day train must appear on departure date")
                .hasSize(1);
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("same-day train must not appear on any other date")
                .isEmpty();
    }

    @Test
    void flightBookedAppearsOnDepartureDate() {
        ItineraryProjector projector = new ItineraryProjector();
        FlightBooked event = new FlightBooked(
                FlightId.random(), "United", "UA59",
                AirportCode.of("SFO"), DEPARTURE,
                AirportCode.of("FRA"), ARRIVAL);

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries)
                .hasSize(1);
        FlightItineraryEntry entry = (FlightItineraryEntry) entries.getFirst();
        assertThat(entry.airline())
                .isEqualTo("United");
        assertThat(entry.flightNumber())
                .isEqualTo("UA59");
        assertThat(entry.departureAirportCode())
                .isEqualTo("SFO");
        assertThat(entry.arrivalAirportCode())
                .isEqualTo("FRA");
    }

    @Test
    void flightChangedReplacesOriginalFlightEntry() {
        ItineraryProjector projector = new ItineraryProjector();
        FlightId flightId = FlightId.random();
        FlightBooked booked = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), DEPARTURE,
                AirportCode.of("FRA"), ARRIVAL);
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), DEPARTURE.plusHours(2),
                AirportCode.of("MUC"), ARRIVAL.plusHours(8),
                "Schedule shifted by airline");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries)
                .hasSize(1);
        FlightItineraryEntry entry = (FlightItineraryEntry) entries.getFirst();
        assertThat(entry.airline())
                .isEqualTo("Lufthansa");
        assertThat(entry.flightNumber())
                .isEqualTo("LH441");
    }

    @Test
    void trainBookedAppearsOnDepartureDate() {
        ItineraryProjector projector = new ItineraryProjector();
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress manchester = new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), london, DEPARTURE, manchester, ARRIVAL, "LNER - Azuma 1A");

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries)
                .hasSize(1);
        TrainItineraryEntry entry = (TrainItineraryEntry) entries.getFirst();
        assertThat(entry.serviceId())
                .isEqualTo("LNER - Azuma 1A");
        assertThat(entry.departureStationName())
                .isEqualTo("London Euston");
        assertThat(entry.arrivalStationName())
                .isEqualTo("Manchester Piccadilly");
    }

    @Test
    void hotelBookedCreatesCheckInOnCheckInDateAndCheckOutOnCheckOutDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate checkIn = DATE;
        LocalDate checkOut = DATE.plusDays(3);
        HotelBooked event = new HotelBooked(
                HotelBookingId.random(), "Marriott Downtown",
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "USA", null),
                checkIn.atTime(15, 0), checkOut.atTime(11, 0), BookingIntent.FINAL, null);

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> checkInEntries = projector.entriesForDate(checkIn);
        assertThat(checkInEntries)
                .hasSize(1);
        HotelItineraryEntry checkInEntry = (HotelItineraryEntry) checkInEntries.getFirst();
        assertThat(checkInEntry.dayRole())
                .isEqualTo(HotelDayRole.CHECK_IN);
        assertThat(checkInEntry.hotelName())
                .isEqualTo("Marriott Downtown");

        List<ItineraryEntry> checkOutEntries = projector.entriesForDate(checkOut);
        assertThat(checkOutEntries)
                .hasSize(1);
        HotelItineraryEntry checkOutEntry = (HotelItineraryEntry) checkOutEntries.getFirst();
        assertThat(checkOutEntry.dayRole())
                .isEqualTo(HotelDayRole.CHECK_OUT);
    }

    @Test
    void hotelIntermediateDaysProduceNoEntries() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate checkIn = DATE;
        LocalDate checkOut = DATE.plusDays(3);
        HotelBooked event = new HotelBooked(
                HotelBookingId.random(), "Marriott Downtown",
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "USA", null),
                checkIn.atTime(15, 0), checkOut.atTime(11, 0), BookingIntent.FINAL, null);

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(checkIn.plusDays(1)))
                .as("Intermediate hotel day must produce no itinerary entries")
                .isEmpty();
        assertThat(projector.entriesForDate(checkIn.plusDays(2)))
                .as("Intermediate hotel day must produce no itinerary entries")
                .isEmpty();
    }

    @Test
    void conferencePlannedCreatesOneEntryPerDayWithDayOfNIndicator() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                ConferenceId.random(), "JitterConf 2026",
                DATE.atStartOfDay(), DATE.plusDays(2).atStartOfDay(),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "USA", null));

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> day1 = projector.entriesForDate(DATE);
        assertThat(day1).hasSize(1);
        ConferenceItineraryEntry entry1 = (ConferenceItineraryEntry) day1.getFirst();
        assertThat(entry1.dayNumber()).isEqualTo(1);
        assertThat(entry1.totalDays()).isEqualTo(3);
        assertThat(entry1.name()).isEqualTo("JitterConf 2026");

        List<ItineraryEntry> day2 = projector.entriesForDate(DATE.plusDays(1));
        assertThat(day2).hasSize(1);
        assertThat(((ConferenceItineraryEntry) day2.getFirst()).dayNumber()).isEqualTo(2);

        List<ItineraryEntry> day3 = projector.entriesForDate(DATE.plusDays(2));
        assertThat(day3).hasSize(1);
        assertThat(((ConferenceItineraryEntry) day3.getFirst()).dayNumber()).isEqualTo(3);
    }

    @Test
    void entriesForDateAreSortedByAnchorTime() {
        ItineraryProjector projector = new ItineraryProjector();
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress manchester = new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
        TrainBooked afternoon = new TrainBooked(
                TrainTripId.random(), london, DATE.atTime(15, 0), manchester, DATE.atTime(17, 0), "");
        TrainBooked morning = new TrainBooked(
                TrainTripId.random(), london, DATE.atTime(9, 0), manchester, DATE.atTime(11, 0), "");

        projector.handle(Stream.of(stored(afternoon), stored(morning)));

        assertThat(projector.entriesForDate(DATE))
                .extracting(e -> ((TrainItineraryEntry) e).departureDateTime().getHour())
                .containsExactly(9, 15);
    }

    @Test
    void entriesAreSortedByActualTime() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate date = LocalDate.of(2026, 9, 15);

        // Hotel check-out 7:00 AM
        HotelBooked hotel = new HotelBooked(
                HotelBookingId.random(), "Grand Hotel",
                new Address("1 Main St", "Amsterdam", "", "1000", "NL", null),
                date.minusDays(3).atTime(15, 0), date.atTime(7, 0), BookingIntent.FINAL, null);

        // Train departs 7:51 AM
        TrainStationAddress amsterdam = new TrainStationAddress("Amsterdam Centraal", "Amsterdam", "NL", "");
        TrainStationAddress brussels = new TrainStationAddress("Brussels Midi", "Brussels", "BE", "");
        TrainBooked train = new TrainBooked(
                TrainTripId.random(), amsterdam, date.atTime(7, 51), brussels, date.atTime(9, 30), "");

        // Conference starts 9:00 AM
        ConferenceTentativelyPlanned conference = new ConferenceTentativelyPlanned(
                ConferenceId.random(), "DevConf 2026",
                date.atTime(9, 0), date.atTime(17, 0),
                "Conference Center",
                new Address("10 Expo Blvd", "Brussels", "", "1000", "BE", null));

        projector.handle(Stream.of(stored(hotel), stored(train), stored(conference)));

        assertThat(projector.entriesForDate(date))
                .as("hotel check-out (7:00) → train (7:51) → conference (9:00)")
                .extracting(ItineraryEntry::kind)
                .containsExactly(EntryKind.LODGING, EntryKind.TRAIN, EntryKind.CONFERENCE);
    }

    @Test
    void gatheringPlannedAppearsOnItsDate() {
        ItineraryProjector projector = new ItineraryProjector();
        GatheringPlanned event = new GatheringPlanned(
                GatheringId.random(), "London Java Community", "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                DATE, LocalTime.of(18, 0), LocalTime.of(21, 0), true,
                "https://meetup.com/ljc/events/123");

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries).hasSize(1);
        GatheringItineraryEntry entry = (GatheringItineraryEntry) entries.getFirst();
        assertThat(entry.title()).isEqualTo("London Java Community");
        assertThat(entry.venueName()).isEqualTo("Skills Matter");
        assertThat(entry.city()).isEqualTo("London");
        assertThat(entry.country()).isEqualTo("GB");
        assertThat(entry.speaking()).as("speaking flag must be true").isTrue();
        assertThat(entry.infoUrl()).isEqualTo("https://meetup.com/ljc/events/123");
        assertThat(entry.anchorTime()).isEqualTo(DATE.atTime(18, 0));
    }

    @Test
    void gatheringDoesNotAppearOnOtherDates() {
        ItineraryProjector projector = new ItineraryProjector();
        GatheringPlanned event = new GatheringPlanned(
                GatheringId.random(), "Some Meetup", "",
                new Address("1 St", "London", "", "EC1A 1BB", "GB", null),
                DATE, LocalTime.of(18, 0), LocalTime.of(21, 0), false, "");

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE.minusDays(1)))
                .as("gathering must not appear before its date")
                .isEmpty();
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("gathering must not appear after its date")
                .isEmpty();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
