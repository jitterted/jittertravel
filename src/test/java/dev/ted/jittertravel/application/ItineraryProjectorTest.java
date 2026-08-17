package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryProjectorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
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
                AirportCode.of("SFO"), zt(DATE.minusDays(5).atTime(9, 0)),
                AirportCode.of("LHR"), zt(DATE.minusDays(4).atTime(17, 0))))));

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
                        AirportCode.of("SFO"), zt(twoWeeks.atTime(9, 0)),
                        AirportCode.of("LHR"), zt(twoWeeks.atTime(17, 0)))),
                stored(new FlightBooked(FlightId.random(), "UA", "UA2",
                        AirportCode.of("LHR"), zt(nextWeek.atTime(10, 0)),
                        AirportCode.of("SFO"), zt(nextWeek.atTime(14, 0))))));

        assertThat(projector.firstDateOnOrAfter(DATE))
                .isEqualTo(nextWeek);
    }

    @Test
    void multiDayFlightAppearsOnBothDepartureDateAndArrivalDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate arrivalDate = DATE.plusDays(1);
        FlightBooked event = new FlightBooked(
                FlightId.random(), "United", "UA58",
                AirportCode.of("SFO"), zt(DATE.atTime(13, 55)),
                AirportCode.of("FRA"), zt(arrivalDate.atTime(9, 45)));

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE))
                .as("multi-day flight must appear on departure date")
                .hasSize(1);
        assertThat(projector.entriesForDate(arrivalDate))
                .as("multi-day flight must appear on arrival date")
                .hasSize(1);
    }

    @Test
    void eachFlightEndpointKeepsItsOwnZoneAndInstant() {
        // SFO 1:55 PM PDT is 20:55Z; FRA 9:45 AM CEST the next day is 07:45Z. Collapsing both
        // onto one zone loses whichever end it discards, and the renderer's <time datetime>
        // would then advertise a moment the traveler is nowhere near.
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate arrivalDate = DATE.plusDays(1);
        FlightBooked event = new FlightBooked(
                FlightId.random(), "United", "UA58",
                AirportCode.of("SFO"), ZonedTimestamp.fromLocal(DATE.atTime(13, 55), ZONE),
                AirportCode.of("FRA"), ZonedTimestamp.fromLocal(arrivalDate.atTime(9, 45),
                                                                ZoneId.of("Europe/Berlin")));

        projector.handle(Stream.of(stored(event)));

        FlightItineraryEntry entry = (FlightItineraryEntry) projector.entriesForDate(DATE).getFirst();
        assertThat(entry.departureDateTime().zone())
                .isEqualTo(ZONE);
        assertThat(entry.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-09-15T20:55:00Z"));
        assertThat(entry.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(entry.arrivalDateTime().utc())
                .isEqualTo(Instant.parse("2026-09-16T07:45:00Z"));
    }

    @Test
    void eachTrainEndpointKeepsItsOwnZoneAndInstant() {
        // Frankfurt 08:00 CEST is 06:00Z; London 10:30 BST is 09:30Z — a leg that crosses zones.
        ItineraryProjector projector = new ItineraryProjector();
        TrainStationAddress frankfurt = new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "DE", "");
        TrainStationAddress london = new TrainStationAddress("London St Pancras", "London", "UK", "");
        TrainBooked event = new TrainBooked(
                TrainTripId.random(),
                frankfurt, ZonedTimestamp.fromLocal(DATE.atTime(8, 0), ZoneId.of("Europe/Berlin")),
                london, ukTime(DATE, LocalTime.of(10, 30)), "");

        projector.handle(Stream.of(stored(event)));

        TrainItineraryEntry entry = (TrainItineraryEntry) projector.entriesForDate(DATE).getFirst();
        assertThat(entry.departureDateTime().utc())
                .isEqualTo(Instant.parse("2026-09-15T06:00:00Z"));
        assertThat(entry.arrivalDateTime().utc())
                .isEqualTo(Instant.parse("2026-09-15T09:30:00Z"));
    }

    @Test
    void sameDayFlightAppearsOnlyOnDepartureDate() {
        ItineraryProjector projector = new ItineraryProjector();
        FlightBooked event = new FlightBooked(
                FlightId.random(), "Ryanair", "FR123",
                AirportCode.of("LHR"), zt(DATE.atTime(7, 0)),
                AirportCode.of("AMS"), zt(DATE.atTime(9, 15)));

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
                TrainTripId.random(), london, zt(DATE.atTime(23, 45)),
                edinburgh, zt(arrivalDate.atTime(7, 30)), "Caledonian Sleeper");

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
                TrainTripId.random(), london, zt(DATE.atTime(9, 0)),
                manchester, zt(DATE.atTime(11, 15)), "");

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
                AirportCode.of("SFO"), zt(DEPARTURE),
                AirportCode.of("FRA"), zt(ARRIVAL));

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
                AirportCode.of("SFO"), zt(DEPARTURE),
                AirportCode.of("FRA"), zt(ARRIVAL));
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), zt(DEPARTURE.plusHours(2)),
                AirportCode.of("MUC"), zt(ARRIVAL.plusHours(8)),
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
                TrainTripId.random(), london, zt(DEPARTURE), manchester, zt(ARRIVAL), "LNER - Azuma 1A");

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
    void trainChangedReplacesOriginalTrainEntry() {
        ItineraryProjector projector = new ItineraryProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress manchester = new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
        TrainStationAddress edinburgh = new TrainStationAddress("Edinburgh Waverley", "Edinburgh", "UK", "");
        TrainBooked booked = new TrainBooked(
                tripId, london, zt(DEPARTURE), manchester, zt(ARRIVAL), "LNER - Azuma 1A");
        TrainChanged changed = new TrainChanged(
                tripId, london, zt(DEPARTURE.plusHours(1)), edinburgh, zt(ARRIVAL.plusHours(2)), "LNER - Azuma 9E22");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries)
                .hasSize(1);
        TrainItineraryEntry entry = (TrainItineraryEntry) entries.getFirst();
        assertThat(entry.serviceId())
                .isEqualTo("LNER - Azuma 9E22");
        assertThat(entry.arrivalStationName())
                .isEqualTo("Edinburgh Waverley");
    }

    @Test
    void hotelBookedCreatesCheckInOnCheckInDateAndCheckOutOnCheckOutDate() {
        ItineraryProjector projector = new ItineraryProjector();
        LocalDate checkIn = DATE;
        LocalDate checkOut = DATE.plusDays(3);
        HotelBooked event = new HotelBooked(
                HotelBookingId.random(), "Marriott Downtown",
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "USA", null),
                zt(checkIn.atTime(15, 0)), zt(checkOut.atTime(11, 0)), BookingIntent.FINAL, null, null);

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
                zt(checkIn.atTime(15, 0)), zt(checkOut.atTime(11, 0)), BookingIntent.FINAL, null, null);

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(checkIn.plusDays(1)))
                .as("Intermediate hotel day must produce no itinerary entries")
                .isEmpty();
        assertThat(projector.entriesForDate(checkIn.plusDays(2)))
                .as("Intermediate hotel day must produce no itinerary entries")
                .isEmpty();
    }

    @Test
    void hotelChangedReplacesOriginalHotelEntry() {
        ItineraryProjector projector = new ItineraryProjector();
        HotelBookingId id = HotelBookingId.random();
        LocalDate checkIn = DATE;
        LocalDate checkOut = DATE.plusDays(3);
        HotelBooked booked = new HotelBooked(
                id, "Marriott Downtown",
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "USA", null),
                zt(checkIn.atTime(15, 0)), zt(checkOut.atTime(11, 0)), BookingIntent.FINAL, null, null);
        LocalDate newCheckIn = DATE.plusDays(10);
        LocalDate newCheckOut = DATE.plusDays(12);
        HotelChanged changed = new HotelChanged(
                id, "Hilton Union Square",
                new Address("333 O'Farrell St", "San Francisco", "CA", "94102", "USA", null),
                zt(newCheckIn.atTime(16, 0)), zt(newCheckOut.atTime(10, 0)), BookingIntent.FINAL, null, null);

        projector.handle(Stream.of(stored(booked), stored(changed)));

        assertThat(projector.entriesForDate(checkIn))
                .as("original check-in date must no longer have an entry after the change")
                .isEmpty();
        List<ItineraryEntry> newCheckInEntries = projector.entriesForDate(newCheckIn);
        assertThat(newCheckInEntries)
                .hasSize(1);
        HotelItineraryEntry entry = (HotelItineraryEntry) newCheckInEntries.getFirst();
        assertThat(entry.dayRole())
                .isEqualTo(HotelDayRole.CHECK_IN);
        assertThat(entry.hotelName())
                .isEqualTo("Hilton Union Square");
        assertThat(projector.entriesForDate(newCheckOut))
                .hasSize(1);
    }

    @Test
    void conferencePlannedCreatesOneEntryPerDayWithDayOfNIndicator() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                ConferenceId.random(), "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(2).atStartOfDay()),
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
    void conferenceCancelledRemovesConferenceFromItinerary() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceTentativelyPlanned planned = new ConferenceTentativelyPlanned(
                conferenceId, "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(2).atStartOfDay()),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "USA", null));

        projector.handle(Stream.of(stored(planned)));
        assertThat(projector.entriesForDate(DATE))
                .as("conference appears on the itinerary before cancellation")
                .hasSize(1);

        // e.g. migrating the conference to a gathering emits ConferenceCancelled
        projector.handle(Stream.of(stored(new ConferenceCancelled(conferenceId, "Migrated to gathering"))));

        assertThat(projector.entriesForDate(DATE))
                .as("cancelled (migrated) conference must not appear on the itinerary")
                .isEmpty();
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("cancelled conference must not appear on any of its days")
                .isEmpty();
    }

    @Test
    void decliningAttendanceRemovesConferenceFromItinerary() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceTentativelyPlanned planned = new ConferenceTentativelyPlanned(
                conferenceId, "Devoxx Morocco",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(2).atStartOfDay()),
                "Palais des Congrès",
                new Address("Avenue de France", "Marrakesh", "", "40000", "Morocco", null));

        projector.handle(Stream.of(stored(planned)));
        assertThat(projector.entriesForDate(DATE))
                .as("conference appears on the itinerary before it is declined")
                .hasSize(1);

        projector.handle(Stream.of(stored(new ConferenceAttendanceDeclined(
                conferenceId, "Schedule clash", Instant.parse("2026-08-16T18:30:00Z")))));

        assertThat(projector.entriesForDate(DATE))
                .as("a declined conference must not appear on the itinerary")
                .isEmpty();
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("a declined conference must not appear on any of its days")
                .isEmpty();
    }

    @Test
    void entriesForDateAreSortedByAnchorTime() {
        ItineraryProjector projector = new ItineraryProjector();
        TrainStationAddress london = new TrainStationAddress("London Euston", "London", "UK", "");
        TrainStationAddress manchester = new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
        TrainBooked afternoon = new TrainBooked(
                TrainTripId.random(), london, zt(DATE.atTime(15, 0)), manchester, zt(DATE.atTime(17, 0)), "");
        TrainBooked morning = new TrainBooked(
                TrainTripId.random(), london, zt(DATE.atTime(9, 0)), manchester, zt(DATE.atTime(11, 0)), "");

        projector.handle(Stream.of(stored(afternoon), stored(morning)));

        assertThat(projector.entriesForDate(DATE))
                .extracting(e -> ((TrainItineraryEntry) e).departureDateTime().localDateTime().getHour())
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
                zt(date.minusDays(3).atTime(15, 0)), zt(date.atTime(7, 0)), BookingIntent.FINAL, null, null);

        // Train departs 7:51 AM
        TrainStationAddress amsterdam = new TrainStationAddress("Amsterdam Centraal", "Amsterdam", "NL", "");
        TrainStationAddress brussels = new TrainStationAddress("Brussels Midi", "Brussels", "BE", "");
        TrainBooked train = new TrainBooked(
                TrainTripId.random(), amsterdam, zt(date.atTime(7, 51)), brussels, zt(date.atTime(9, 30)), "");

        // Conference starts 9:00 AM
        ConferenceTentativelyPlanned conference = new ConferenceTentativelyPlanned(
                ConferenceId.random(), "DevConf 2026",
                zt(date.atTime(9, 0)), zt(date.atTime(17, 0)),
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
                ukTime(DATE, LocalTime.of(18, 0)), ukTime(DATE, LocalTime.of(21, 0)), true,
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
                ukTime(DATE, LocalTime.of(18, 0)), ukTime(DATE, LocalTime.of(21, 0)), false, "");

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE.minusDays(1)))
                .as("gathering must not appear before its date")
                .isEmpty();
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("gathering must not appear after its date")
                .isEmpty();
    }

    @Test
    void gatheringChangedMovesTheEntryToItsNewDateAndDetails() {
        ItineraryProjector projector = new ItineraryProjector();
        GatheringId gatheringId = GatheringId.random();
        GatheringPlanned planned = new GatheringPlanned(
                gatheringId, "Old Title", "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                ukTime(DATE, LocalTime.of(18, 0)), ukTime(DATE, LocalTime.of(21, 0)), true, "https://old.example.com");
        GatheringChanged changed = new GatheringChanged(
                gatheringId, "New Title", "Federation House",
                new Address("2 New St", "Manchester", "", "M1 1AA", "GB", null),
                ukTime(DATE.plusDays(1), LocalTime.of(17, 30)), ukTime(DATE.plusDays(1), LocalTime.of(20, 0)), false, "https://new.example.com");

        projector.handle(Stream.of(stored(planned), stored(changed)));

        assertThat(projector.entriesForDate(DATE))
                .as("gathering must no longer appear on its original date")
                .isEmpty();
        List<ItineraryEntry> entries = projector.entriesForDate(DATE.plusDays(1));
        assertThat(entries).hasSize(1);
        GatheringItineraryEntry entry = (GatheringItineraryEntry) entries.getFirst();
        assertThat(entry.title()).isEqualTo("New Title");
        assertThat(entry.venueName()).isEqualTo("Federation House");
        assertThat(entry.city()).isEqualTo("Manchester");
        assertThat(entry.speaking()).as("speaking flag should be overwritten by the change").isFalse();
        assertThat(entry.infoUrl()).isEqualTo("https://new.example.com");
        assertThat(entry.anchorTime()).isEqualTo(DATE.plusDays(1).atTime(17, 30));
    }

    @Test
    void privateEventPlannedAppearsOnItsDate() {
        ItineraryProjector projector = new ItineraryProjector();
        PrivateEventPlanned event = new PrivateEventPlanned(
                PrivateEventId.random(), "Dinner with the Smiths", "Alo",
                new Address("163 Spadina Ave", "Toronto", "ON", "M5V 2L6", "Canada", null),
                ukTime(DATE, LocalTime.of(19, 0)), ukTime(DATE, LocalTime.of(22, 0)));

        projector.handle(Stream.of(stored(event)));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries).hasSize(1);
        PrivateEventItineraryEntry entry = (PrivateEventItineraryEntry) entries.getFirst();
        assertThat(entry.title()).isEqualTo("Dinner with the Smiths");
        assertThat(entry.venueName()).isEqualTo("Alo");
        assertThat(entry.city()).isEqualTo("Toronto");
        assertThat(entry.country()).isEqualTo("Canada");
        assertThat(entry.anchorTime()).isEqualTo(DATE.atTime(19, 0));
    }

    @Test
    void privateEventDoesNotAppearOnOtherDates() {
        ItineraryProjector projector = new ItineraryProjector();
        PrivateEventPlanned event = new PrivateEventPlanned(
                PrivateEventId.random(), "Evening out", "",
                new Address("1 St", "Toronto", "ON", "M5V 2L6", "Canada", null),
                ukTime(DATE, LocalTime.of(19, 0)), ukTime(DATE, LocalTime.of(22, 0)));

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entriesForDate(DATE.minusDays(1)))
                .as("private event must not appear before its date")
                .isEmpty();
        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("private event must not appear after its date")
                .isEmpty();
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static ZonedTimestamp ukTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), ZoneId.of("Europe/London"));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
