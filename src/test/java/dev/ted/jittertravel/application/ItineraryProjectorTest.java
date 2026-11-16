package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.Assertions.tuple;

class ItineraryProjectorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);
    private static final LocalDateTime DEPARTURE = DATE.atTime(9, 0);
    private static final LocalDateTime ARRIVAL = DATE.atTime(11, 15);

    @Test
    void firstEntryDateOnOrAfterIsEmptyWhenNoEntries() {
        ItineraryProjector projector = new ItineraryProjector();

        assertThat(projector.firstEntryDateOnOrAfter(DATE))
                .as("No entries at all")
                .isEmpty();
    }

    @Test
    void firstEntryDateOnOrAfterIsEmptyWhenAllEntriesAreInPast() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(new FlightBooked(
                FlightId.random(), "BA", "BA1",
                AirportCode.of("SFO"), zt(DATE.minusDays(5).atTime(9, 0)),
                AirportCode.of("LHR"), zt(DATE.minusDays(4).atTime(17, 0))))));

        assertThat(projector.firstEntryDateOnOrAfter(DATE))
                .as("Every entry is before " + DATE)
                .isEmpty();
    }

    @Test
    void firstEntryDateOnOrAfterIncludesAnEntryOnTheDateItself() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(new FlightBooked(
                FlightId.random(), "BA", "BA1",
                AirportCode.of("SFO"), zt(DATE.atTime(9, 0)),
                AirportCode.of("LHR"), zt(DATE.atTime(17, 0))))));

        assertThat(projector.firstEntryDateOnOrAfter(DATE))
                .as("An entry on " + DATE + " itself")
                .hasValue(DATE);
    }

    @Test
    void firstEntryDateOnOrAfterReturnsEarliestFutureEntryDate() {
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

        assertThat(projector.firstEntryDateOnOrAfter(DATE))
                .as("The earlier of two future entries")
                .hasValue(nextWeek);
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
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "US", null),
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
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "US", null),
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
    void intermediateHotelDayReportsTheStayAsWhereTheTravelerIs() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(hotelBooked(HotelBookingId.random(), "Marriott Downtown",
                DATE, DATE.plusDays(3)))));

        assertThat(projector.ongoingStayOn(DATE.plusDays(1)))
                .contains(new OngoingStay("Marriott Downtown", "San Francisco", "CA"));
        assertThat(projector.ongoingStayOn(DATE.plusDays(2)))
                .contains(new OngoingStay("Marriott Downtown", "San Francisco", "CA"));
    }

    @Test
    void checkInAndCheckOutDaysHaveNoOngoingStayBecauseTheirOwnEntriesSayWhereHeIs() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(hotelBooked(HotelBookingId.random(), "Marriott Downtown",
                DATE, DATE.plusDays(3)))));

        assertThat(projector.ongoingStayOn(DATE))
                .as("check-in day already carries a Check-In entry")
                .isEmpty();
        assertThat(projector.ongoingStayOn(DATE.plusDays(3)))
                .as("check-out day already carries a Check-Out entry")
                .isEmpty();
    }

    @Test
    void daysOutsideEveryStayHaveNoOngoingStay() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(Stream.of(stored(hotelBooked(HotelBookingId.random(), "Marriott Downtown",
                DATE, DATE.plusDays(3)))));

        assertThat(projector.ongoingStayOn(DATE.minusDays(1)))
                .isEmpty();
        assertThat(projector.ongoingStayOn(DATE.plusDays(4)))
                .isEmpty();
    }

    @Test
    void cancellingTheBookingRemovesTheOngoingStay() {
        ItineraryProjector projector = new ItineraryProjector();
        HotelBookingId id = HotelBookingId.random();
        projector.handle(Stream.of(
                stored(hotelBooked(id, "Marriott Downtown", DATE, DATE.plusDays(3))),
                stored(new HotelBookingCancelled(id, "plans changed"))));

        assertThat(projector.ongoingStayOn(DATE.plusDays(1)))
                .isEmpty();
    }

    @Test
    void changingTheBookingMovesTheOngoingStayToTheNewHotelAndDates() {
        ItineraryProjector projector = new ItineraryProjector();
        HotelBookingId id = HotelBookingId.random();
        projector.handle(Stream.of(
                stored(hotelBooked(id, "Marriott Downtown", DATE, DATE.plusDays(3))),
                stored(new HotelChanged(id, "Hotel Adlon",
                        new Address("Unter den Linden 77", "Berlin", "", "10117", "DE", null),
                        zt(DATE.plusDays(1).atTime(15, 0)), zt(DATE.plusDays(5).atTime(11, 0)),
                        BookingIntent.FINAL, null, null))));

        assertThat(projector.ongoingStayOn(DATE.plusDays(1)))
                .as("the new check-in day is no longer an in-between day")
                .isEmpty();
        assertThat(projector.ongoingStayOn(DATE.plusDays(4)))
                .contains(new OngoingStay("Hotel Adlon", "Berlin", "Germany"));
    }

    @Test
    void hotelChangedReplacesOriginalHotelEntry() {
        ItineraryProjector projector = new ItineraryProjector();
        HotelBookingId id = HotelBookingId.random();
        LocalDate checkIn = DATE;
        LocalDate checkOut = DATE.plusDays(3);
        HotelBooked booked = new HotelBooked(
                id, "Marriott Downtown",
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "US", null),
                zt(checkIn.atTime(15, 0)), zt(checkOut.atTime(11, 0)), BookingIntent.FINAL, null, null);
        LocalDate newCheckIn = DATE.plusDays(10);
        LocalDate newCheckOut = DATE.plusDays(12);
        HotelChanged changed = new HotelChanged(
                id, "Hilton Union Square",
                new Address("333 O'Farrell St", "San Francisco", "CA", "94102", "US", null),
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

    /**
     * Every day of the conference carries the conference's own id — which is what lets the owner's
     * itinerary card link to {@code /conferences/{id}}, and it has to be on every day because any
     * of them may be the one Ted is looking at.
     */
    @Test
    void everyConferenceDayCarriesTheConferenceId() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(new ConferencePlanned(
                conferenceId, "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(1).atStartOfDay()),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null)))));

        assertThat(List.of(projector.entriesForDate(DATE).getFirst(),
                           projector.entriesForDate(DATE.plusDays(1)).getFirst()))
                .extracting(entry -> ((ConferenceItineraryEntry) entry).conferenceId())
                .containsExactly(conferenceId, conferenceId);
    }

    @Test
    void conferencePlannedCreatesOneEntryPerDayWithDayOfNIndicator() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferencePlanned event = new ConferencePlanned(
                ConferenceId.random(), "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(2).atStartOfDay()),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null));

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
        ConferencePlanned planned = new ConferencePlanned(
                conferenceId, "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(2).atStartOfDay()),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null));

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
        ConferencePlanned planned = new ConferencePlanned(
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
        ConferencePlanned conference = new ConferencePlanned(
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
        assertThat(entry.qualifier()).isEqualTo("United Kingdom");
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
        assertThat(entry.qualifier()).isEqualTo("Canada");
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

    @Test
    void groundTransferPlannedAppearsOnItsDepartureDateWithBothEndsNamed() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(Stream.of(stored(groundTransfer())));

        List<ItineraryEntry> entries = projector.entriesForDate(DATE);
        assertThat(entries).hasSize(1);
        GroundTransferItineraryEntry entry = (GroundTransferItineraryEntry) entries.getFirst();
        assertThat(entry.origin())
                .as("an airport end is named by its code")
                .isEqualTo("DEN");

        assertThat(entry.destination())
                .as("a hotel end is named by its hotel — the itinerary is behind auth")
                .isEqualTo("Marriott Lone Tree");

        assertThat(entry.mode())
                .as("how the hop is made rides to the card Ted reads mid-trip, in his own words")
                .isEqualTo("A16 hotel shuttle");

        assertThat(entry.anchorTime()).isEqualTo(DATE.atTime(12, 0));
    }

    @Test
    void groundTransferGetsNoSecondArrivalDayEntry() {
        // Unlike a flight or a train, both ends share one zone and the hop is short, so there is
        // never a second local day to place it on.
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(Stream.of(stored(groundTransfer())));

        assertThat(projector.entriesForDate(DATE.plusDays(1)))
                .as("a transfer must not appear on the following day")
                .isEmpty();
    }

    /**
     * The itinerary is ordered by time, across kinds — so the taxi that gets Ted to the airport
     * sits above the flight it feeds, and the taxi from the airport sits below the flight that
     * brought him. (The calendar cannot do this: it lays entries out in fixed per-kind lanes, so a
     * transfer is always in the same band relative to flights whichever way it runs.)
     */
    @Test
    void aTransferToTheAirportSortsAboveTheLaterFlightItFeeds() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(Stream.of(
                stored(new FlightBooked(FlightId.random(), "United", "UA58",
                        AirportCode.of("DEN"), ukTime(DATE, LocalTime.of(15, 55)),
                        AirportCode.of("SJC"), ukTime(DATE, LocalTime.of(17, 32)))),
                stored(groundTransfer())));

        assertThat(projector.entriesForDate(DATE))
                .extracting(ItineraryEntry::kind, ItineraryEntry::anchorTime)
                .containsExactly(
                        tuple(EntryKind.GROUND_TRANSFER, DATE.atTime(12, 0)),
                        tuple(EntryKind.FLIGHT, DATE.atTime(15, 55)));
    }

    // --- A check-out sits no later than the first leg leaving its city that day ---
    //
    // docs/mockups/ItineraryCheckOutOrder.html. Every fixture checks out of the same San Francisco
    // hotel at 11:00 on DATE, and checked in two days earlier, so DATE holds only its check-out.

    @Nested
    class CheckOutOrder {

        private final ItineraryProjector projector = new ItineraryProjector();
        private final TrainStationAddress sanFrancisco =
                new TrainStationAddress("4th & King", "San Francisco", "US", "");
        private final TrainStationAddress sanJose =
                new TrainStationAddress("Diridon", "San Jose", "US", "");
        private final TrainStationAddress oakland =
                new TrainStationAddress("Jack London Square", "Oakland", "US", "");

        @Test
        void aTrainLeavingTheHotelsCityBeforeCheckOutTimeComesAfterTheCheckOut() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(trainFrom(sanFrancisco, TrainTripId.random(), 9, 45))));

            assertThat(kindsOnDate())
                    .as("Checked out before taking the 9:45")
                    .containsExactly(EntryKind.LODGING, EntryKind.TRAIN);
        }

        @Test
        void aFlightLeavingTheHotelsCityBeforeCheckOutTimeComesAfterTheCheckOut() {
            // SFO is matched to its city through the curated table, as /schedule-problems does.
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(flightFromSfo(FlightId.random(), 10, 40))));

            assertThat(kindsOnDate())
                    .as("Checked out before the 10:40 flight")
                    .containsExactly(EntryKind.LODGING, EntryKind.FLIGHT);
        }

        @Test
        void aGroundTransferLeavingTheHotelsCityBeforeCheckOutTimeComesAfterTheCheckOut() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(transferFromSanFrancisco(GroundTransferId.random(), 10, 30))));

            assertThat(kindsOnDate())
                    .as("Checked out before the 10:30 transfer")
                    .containsExactly(EntryKind.LODGING, EntryKind.GROUND_TRANSFER);
        }

        @Test
        void anEarlyEventKeepsItsPlaceAndTheCheckOutLandsJustAboveTheTrain() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(trainFrom(sanFrancisco, TrainTripId.random(), 10, 15)),
                    stored(breakfastAt(8, 0))));

            assertThat(kindsOnDate())
                    .as("Breakfast at 8:00, then check-out, then the 10:15 train")
                    .containsExactly(EntryKind.PRIVATE_EVENT, EntryKind.LODGING, EntryKind.TRAIN);
        }

        @Test
        void anEventInTheSameCityDoesNotMoveTheCheckOut() {
            // He may go back to the room after breakfast; only a leg out of town says he left.
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(new GatheringPlanned(GatheringId.random(), "Crafters Breakfast", "Cafe",
                            sanFranciscoAddress(), zt(DATE.atTime(8, 30)), zt(DATE.atTime(10, 0)),
                            false, ""))));

            assertThat(kindsOnDate())
                    .as("Clock order: breakfast at 8:30, check-out at 11:00")
                    .containsExactly(EntryKind.GATHERING, EntryKind.LODGING);
        }

        @Test
        void aLegLeavingAnotherCityDoesNotMoveTheCheckOut() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(10, 0)),
                    stored(trainFrom(oakland, TrainTripId.random(), 9, 45))));

            assertThat(kindsOnDate())
                    .as("The Oakland train says nothing about leaving San Francisco")
                    .containsExactly(EntryKind.TRAIN, EntryKind.PRIVATE_EVENT, EntryKind.LODGING);
        }

        @Test
        void aLegLeavingTheDayBeforeDoesNotMoveTheCheckOut() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(10, 0)),
                    stored(new FlightBooked(FlightId.random(), "UA", "UA1",
                            AirportCode.of("SFO"), zt(DATE.minusDays(1).atTime(23, 0)),
                            AirportCode.of("JFK"), zt(DATE.atTime(7, 30))))));

            assertThat(kindsOnDate())
                    .as("Last night's red-eye is not today's departure")
                    .containsExactly(EntryKind.FLIGHT, EntryKind.PRIVATE_EVENT, EntryKind.LODGING);
        }

        @Test
        void aLegLeavingAfterCheckOutTimeNeverPullsTheCheckOutLater() {
            // Check-out at 11:00, lunch at 12:00, the train at 15:00: the check-out stays first.
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(12, 0)),
                    stored(trainFrom(sanFrancisco, TrainTripId.random(), 15, 0))));

            assertThat(kindsOnDate())
                    .as("A later departure only ever moves the check-out up, never down")
                    .containsExactly(EntryKind.LODGING, EntryKind.PRIVATE_EVENT, EntryKind.TRAIN);
        }

        @Test
        void aCheckOutTiedWithTheTrainOutComesFirst() {
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(trainFrom(sanFrancisco, TrainTripId.random(), 11, 0))));

            assertThat(kindsOnDate())
                    .as("Same minute: he leaves the room, then the town")
                    .containsExactly(EntryKind.LODGING, EntryKind.TRAIN);
        }

        @Test
        void aTrainChangedToLaterStopsMovingTheCheckOut() {
            TrainTripId tripId = TrainTripId.random();
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(10, 0)),
                    stored(trainFrom(sanFrancisco, tripId, 9, 45)),
                    stored(new TrainChanged(tripId, sanFrancisco, zt(DATE.atTime(13, 0)),
                            sanJose, zt(DATE.atTime(14, 0)), ""))));

            assertThat(kindsOnDate())
                    .as("The train now leaves at 13:00, after check-out")
                    .containsExactly(EntryKind.PRIVATE_EVENT, EntryKind.LODGING, EntryKind.TRAIN);
        }

        @Test
        void aFlightChangedToLaterStopsMovingTheCheckOut() {
            FlightId flightId = FlightId.random();
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(10, 0)),
                    stored(flightFromSfo(flightId, 9, 0)),
                    stored(new FlightChanged(flightId, "UA", "UA1",
                            AirportCode.of("SFO"), zt(DATE.atTime(13, 0)),
                            AirportCode.of("JFK"), zt(DATE.atTime(21, 0)), ""))));

            assertThat(kindsOnDate())
                    .as("The flight now leaves at 13:00, after check-out")
                    .containsExactly(EntryKind.PRIVATE_EVENT, EntryKind.LODGING, EntryKind.FLIGHT);
        }

        @Test
        void aCancelledLegStopsMovingTheCheckOut() {
            TrainTripId tripId = TrainTripId.random();
            FlightId flightId = FlightId.random();
            GroundTransferId transferId = GroundTransferId.random();
            projector.handle(Stream.of(stored(checkingOutAtEleven()),
                    stored(breakfastAt(10, 0)),
                    stored(trainFrom(sanFrancisco, tripId, 9, 45)),
                    stored(new TrainCancelled(tripId, "")),
                    stored(flightFromSfo(flightId, 9, 15)),
                    stored(new FlightCancelled(flightId, "", Instant.now(), null)),
                    stored(transferFromSanFrancisco(transferId, 9, 30)),
                    stored(new GroundTransferCancelled(transferId))));

            assertThat(kindsOnDate())
                    .as("Nothing leaves any more, so clock order")
                    .containsExactly(EntryKind.PRIVATE_EVENT, EntryKind.LODGING);
        }

        private List<EntryKind> kindsOnDate() {
            return projector.entriesForDate(DATE).stream().map(ItineraryEntry::kind).toList();
        }

        private static HotelBooked checkingOutAtEleven() {
            return hotelBooked(HotelBookingId.random(), "Hotel Zetta", DATE.minusDays(2), DATE);
        }

        private TrainBooked trainFrom(TrainStationAddress from, TrainTripId tripId, int hour, int minute) {
            return new TrainBooked(tripId, from, zt(DATE.atTime(hour, minute)),
                    sanJose, zt(DATE.atTime(hour + 1, minute)), "");
        }

        private static FlightBooked flightFromSfo(FlightId flightId, int hour, int minute) {
            return new FlightBooked(flightId, "UA", "UA1",
                    AirportCode.of("SFO"), zt(DATE.atTime(hour, minute)),
                    AirportCode.of("JFK"), zt(DATE.atTime(hour + 8, minute)));
        }

        private static GroundTransferPlanned transferFromSanFrancisco(GroundTransferId id, int hour, int minute) {
            return new GroundTransferPlanned(id, "", "Hotel Zetta", sanFranciscoAddress(),
                    "", "Strathmere", new Address("", "San Jose", "CA", "", "US", null),
                    zt(DATE.atTime(hour, minute)), zt(DATE.atTime(hour + 1, minute)), "Rental car");
        }

        private static PrivateEventPlanned breakfastAt(int hour, int minute) {
            return new PrivateEventPlanned(PrivateEventId.random(), "Breakfast", "Cafe",
                    sanFranciscoAddress(), zt(DATE.atTime(hour, minute)), zt(DATE.atTime(hour + 1, minute)));
        }

        private static Address sanFranciscoAddress() {
            return new Address("1 Market St", "San Francisco", "CA", "94105", "US", null);
        }
    }

    // --- A check-in sits no earlier than the last leg arriving in its city that day ---
    //
    // The mirror of CheckOutOrder (Ted, 2026-11-16, from the Nov 20 Potsdam → Berlin itinerary).
    // Every fixture checks in to the same San Francisco hotel at 15:00 on DATE and checks out two
    // days later, so DATE holds only its check-in.

    @Nested
    class CheckInOrder {

        private final ItineraryProjector projector = new ItineraryProjector();
        private final TrainStationAddress sanFrancisco =
                new TrainStationAddress("4th & King", "San Francisco", "US", "");
        private final TrainStationAddress sanJose =
                new TrainStationAddress("Diridon", "San Jose", "US", "");
        private final TrainStationAddress oakland =
                new TrainStationAddress("Jack London Square", "Oakland", "US", "");

        @Test
        void aTransferArrivingInTheHotelsCityAfterCheckInTimeComesBeforeTheCheckIn() {
            // Nov 20: the tour ends at 16:00, the transfer lands at 17:00, check-in opened at 15:00.
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(tourFrom(12, 10, 16, 0)),
                    stored(transferIntoSanFrancisco(GroundTransferId.random(), 16, 0))));

            assertThat(kindsOnDate())
                    .as("Tour, then the transfer into town, then check-in")
                    .containsExactly(EntryKind.PRIVATE_EVENT, EntryKind.GROUND_TRANSFER, EntryKind.LODGING);
        }

        @Test
        void aTrainArrivingInTheHotelsCityAfterCheckInTimeComesBeforeTheCheckIn() {
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(trainTo(sanFrancisco, TrainTripId.random(), 16, 30))));

            assertThat(kindsOnDate())
                    .as("Arrives at 16:30, then checks in")
                    .containsExactly(EntryKind.TRAIN, EntryKind.LODGING);
        }

        @Test
        void aFlightArrivingInTheHotelsCityAfterCheckInTimeComesBeforeTheCheckIn() {
            // SFO is matched to its city through the curated table, as for check-out.
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(flightIntoSfo(FlightId.random(), 17, 16))));

            assertThat(kindsOnDate())
                    .as("Lands at 17:16, then checks in")
                    .containsExactly(EntryKind.FLIGHT, EntryKind.LODGING);
        }

        @Test
        void theCheckInFollowsTheLastLegIntoTheCityNotTheFirst() {
            // Lands at SFO at 16:00, then the 17:00 transfer from the airport into town.
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(flightIntoSfo(FlightId.random(), 16, 0)),
                    stored(transferIntoSanFrancisco(GroundTransferId.random(), 17, 0))));

            assertThat(kindsOnDate())
                    .as("Flight, transfer, then check-in")
                    .containsExactly(EntryKind.FLIGHT, EntryKind.GROUND_TRANSFER, EntryKind.LODGING);
        }

        @Test
        void anEventInTheSameCityDoesNotMoveTheCheckIn() {
            // He may drop his bags before dinner; only a leg into town says he was not there yet.
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(new GatheringPlanned(GatheringId.random(), "Meetup", "Cafe",
                            new Address("1 Market St", "San Francisco", "CA", "94105", "US", null),
                            zt(DATE.atTime(18, 0)), zt(DATE.atTime(20, 0)), false, ""))));

            assertThat(kindsOnDate())
                    .as("Clock order: check-in at 15:00, meetup at 18:00")
                    .containsExactly(EntryKind.LODGING, EntryKind.GATHERING);
        }

        @Test
        void aLegArrivingInAnotherCityDoesNotMoveTheCheckIn() {
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(trainTo(oakland, TrainTripId.random(), 16, 30))));

            assertThat(kindsOnDate())
                    .as("The Oakland train says nothing about reaching San Francisco")
                    .containsExactly(EntryKind.LODGING, EntryKind.TRAIN);
        }

        @Test
        void aLegArrivingBeforeCheckInTimeNeverPullsTheCheckInEarlier() {
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(tourFrom(13, 0, 14, 0)),
                    stored(trainTo(sanFrancisco, TrainTripId.random(), 11, 0))));

            assertThat(kindsOnDate())
                    .as("An earlier arrival only ever moves the check-in down, never up")
                    .containsExactly(EntryKind.TRAIN, EntryKind.PRIVATE_EVENT, EntryKind.LODGING);
        }

        @Test
        void aLegArrivingTheNextDayDoesNotMoveTheCheckIn() {
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(new FlightBooked(FlightId.random(), "UA", "UA1",
                            AirportCode.of("JFK"), zt(DATE.atTime(23, 0)),
                            AirportCode.of("SFO"), zt(DATE.plusDays(1).atTime(2, 0))))));

            assertThat(kindsOnDate())
                    .as("Tomorrow's landing is not today's arrival")
                    .containsExactly(EntryKind.LODGING, EntryKind.FLIGHT);
        }

        @Test
        void aHotelWithNoCityIsNotMovedByALegIntoAnAirportTheTableDoesNotKnow() {
            // Both places are blank, and a blank place matches a blank place.
            projector.handle(Stream.of(
                    stored(new HotelBooked(HotelBookingId.random(), "Nowhere Inn",
                            new Address("1 Main St", "", "", "", "US", null),
                            zt(DATE.atTime(15, 0)), zt(DATE.plusDays(2).atTime(11, 0)),
                            BookingIntent.FINAL, null, null)),
                    stored(new FlightBooked(FlightId.random(), "UA", "UA1",
                            AirportCode.of("JFK"), zt(DATE.atTime(9, 0)),
                            AirportCode.of("XQZ"), zt(DATE.atTime(17, 0)))),
                    stored(tourFrom(16, 0, 17, 0))));

            assertThat(kindsOnDate())
                    .as("No city to match, so check-in stays at 15:00, above the 16:00 tour")
                    .containsExactly(EntryKind.FLIGHT, EntryKind.LODGING, EntryKind.PRIVATE_EVENT);
        }

        @Test
        void aCancelledLegStopsMovingTheCheckIn() {
            TrainTripId tripId = TrainTripId.random();
            FlightId flightId = FlightId.random();
            GroundTransferId transferId = GroundTransferId.random();
            projector.handle(Stream.of(stored(checkingInAtThree()),
                    stored(tourFrom(16, 0, 17, 0)),
                    stored(trainTo(sanFrancisco, tripId, 18, 0)),
                    stored(new TrainCancelled(tripId, "")),
                    stored(flightIntoSfo(flightId, 18, 0)),
                    stored(new FlightCancelled(flightId, "", Instant.now(), null)),
                    stored(transferIntoSanFrancisco(transferId, 18, 0)),
                    stored(new GroundTransferCancelled(transferId))));

            assertThat(kindsOnDate())
                    .as("Nothing arrives any more, so clock order")
                    .containsExactly(EntryKind.LODGING, EntryKind.PRIVATE_EVENT);
        }

        private List<EntryKind> kindsOnDate() {
            return projector.entriesForDate(DATE).stream().map(ItineraryEntry::kind).toList();
        }

        private static HotelBooked checkingInAtThree() {
            return hotelBooked(HotelBookingId.random(), "Hotel Zetta", DATE, DATE.plusDays(2));
        }

        /** Departs an hour before it arrives, at {@code hour:minute}. */
        private TrainBooked trainTo(TrainStationAddress to, TrainTripId tripId, int hour, int minute) {
            return new TrainBooked(tripId, sanJose, zt(DATE.atTime(hour - 1, minute)),
                    to, zt(DATE.atTime(hour, minute)), "");
        }

        /** Lands at {@code hour:minute}, having left JFK six hours earlier on the clock. */
        private static FlightBooked flightIntoSfo(FlightId flightId, int hour, int minute) {
            return new FlightBooked(flightId, "UA", "UA1",
                    AirportCode.of("JFK"), zt(DATE.atTime(hour - 6, minute)),
                    AirportCode.of("SFO"), zt(DATE.atTime(hour, minute)));
        }

        /** Departs at {@code hour:minute} and lands an hour later. */
        private static GroundTransferPlanned transferIntoSanFrancisco(GroundTransferId id, int hour, int minute) {
            return new GroundTransferPlanned(id, "", "Strathmere",
                    new Address("", "San Jose", "CA", "", "US", null),
                    "", "Hotel Zetta", new Address("1 Market St", "San Francisco", "CA", "94105", "US", null),
                    zt(DATE.atTime(hour, minute)), zt(DATE.atTime(hour + 1, minute)), "Rental car");
        }

        private static PrivateEventPlanned tourFrom(int startHour, int startMinute, int endHour, int endMinute) {
            return new PrivateEventPlanned(PrivateEventId.random(), "Palace tour", "Sanssouci",
                    new Address("", "San Jose", "CA", "", "US", null),
                    zt(DATE.atTime(startHour, startMinute)), zt(DATE.atTime(endHour, endMinute)));
        }
    }

    private static HotelBooked hotelBooked(HotelBookingId id, String hotelName,
                                           LocalDate checkIn, LocalDate checkOut) {
        return new HotelBooked(id, hotelName,
                new Address("742 Evergreen Terrace", "San Francisco", "CA", "94103", "US", null),
                zt(checkIn.atTime(15, 0)), zt(checkOut.atTime(11, 0)), BookingIntent.FINAL, null, null);
    }

    private static GroundTransferPlanned groundTransfer() {
        return new GroundTransferPlanned(GroundTransferId.random(),
                "DEN", "", new Address("", "Denver", "", "", "", "Denver"),
                "", "Marriott Lone Tree",
                new Address("10345 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree"),
                ukTime(DATE, LocalTime.of(12, 0)), ukTime(DATE, LocalTime.of(12, 45)),
                "A16 hotel shuttle");
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static ZonedTimestamp ukTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), ZoneId.of("Europe/London"));
    }

    // --- Conference commitment and speaking, folded through ConferenceState ---
    //
    // Until 2026-09-09 this projector read three conference events and ignored six, so /itinerary
    // showed family neither the commitment chip nor the speaking badge that an anonymous visitor
    // could already see on /calendar, and — worse — kept a conference Ted had been rejected from.

    @Test
    void aPlannedConferenceStartsOutMerelyWatched() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(Stream.of(stored(conferencePlanned(ConferenceId.random()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.WATCHING, false);
    }

    @Test
    void confirmingAttendanceOnASpeakingBasisMakesEveryDayGoingAndSpeaking() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new ConferenceAttendanceConfirmed(
                        conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, Instant.now()))));

        assertThat(List.of(conferenceOn(projector, DATE), conferenceOn(projector, DATE.plusDays(1))))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(tuple(AttendanceCommitment.GOING, true),
                                 tuple(AttendanceCommitment.GOING, true));
    }

    /**
     * A bought ticket is attending, not speaking — the partition {@code AttendanceBasis} exists for.
     */
    @Test
    void confirmingOnABoughtTicketIsGoingWithoutSpeaking() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new ConferenceAttendanceConfirmed(
                        conferenceId, AttendanceBasis.TICKET_PURCHASED, Instant.now()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.GOING, false);
    }

    /** An acceptance commits attendance on its own — submitting the talk was already the opt-in. */
    @Test
    void anAcceptedTalkCommitsAttendanceWithNoConfirmationEvent() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new TalkSubmitted(conferenceId, Instant.now())),
                stored(new TalkAccepted(conferenceId, Instant.now()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.GOING, true);
    }

    /**
     * An invitation is an offer, so it commits nothing — and an uncommitted conference never wears
     * the speaking badge, whatever the stream says about the talk. This is the pair that must not
     * co-occur: "he was asked to speak somewhere he has not decided about".
     */
    @Test
    void anUnansweredInvitationCommitsNothingAndDoesNotSayHeIsSpeaking() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new InvitedToSpeak(conferenceId, Instant.now()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.WATCHING, false);
    }

    /**
     * <strong>The bug this change was written for.</strong> Where acceptance was the way in, a
     * rejection drops the conference — it already left both calendars, and it stayed on the family
     * itinerary, so family were planning around a trip that was not happening.
     */
    @Test
    void aRejectionDropsAConferenceThatNeededAcceptanceToGetIn() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId, ConferenceFormat.ACCEPTANCE_REQUIRED)),
                stored(new TalkSubmitted(conferenceId, Instant.now())),
                stored(new TalkRejected(conferenceId, Instant.now()))));

        assertThat(projector.entriesForDate(DATE))
                .as("a conference whose only way in was acceptance must leave the itinerary")
                .isEmpty();
    }

    /** Everywhere else the same rejection leaves the conference merely watched, still on the list. */
    @Test
    void aRejectionElsewhereLeavesTheConferenceWatchedRatherThanDropped() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS)),
                stored(new TalkSubmitted(conferenceId, Instant.now())),
                stored(new TalkRejected(conferenceId, Instant.now()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.WATCHING, false);
    }

    /** Pulling a talk moves one axis only: a conference Ted committed to stays committed. */
    @Test
    void withdrawingATalkKeepsTheConferenceButStopsSayingHeSpeaks() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new TalkSubmitted(conferenceId, Instant.now())),
                stored(new TalkAccepted(conferenceId, Instant.now())),
                stored(new TalkWithdrawn(conferenceId, Instant.now()))));

        assertThat(conferenceOn(projector, DATE))
                .extracting(ConferenceItineraryEntry::commitment, ConferenceItineraryEntry::speaking)
                .containsExactly(AttendanceCommitment.GOING, false);
    }

    @Test
    void decliningRemovesTheConferenceEntirely() {
        ItineraryProjector projector = new ItineraryProjector();
        ConferenceId conferenceId = ConferenceId.random();

        projector.handle(Stream.of(
                stored(conferencePlanned(conferenceId)),
                stored(new ConferenceAttendanceDeclined(
                        conferenceId, "clashes with SoCraTes", Instant.now()))));

        assertThat(projector.entriesForDate(DATE)).isEmpty();
    }

    /** A talk event for a conference this projector never saw planned changes nothing. */
    @Test
    void anEventForAnUnknownConferenceIsIgnored() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(Stream.of(stored(new TalkAccepted(ConferenceId.random(), Instant.now()))));

        assertThat(projector.entriesForDate(DATE)).isEmpty();
    }

    private static ConferenceItineraryEntry conferenceOn(ItineraryProjector projector, LocalDate date) {
        return (ConferenceItineraryEntry) projector.entriesForDate(date).getFirst();
    }

    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId) {
        return conferencePlanned(conferenceId, ConferenceFormat.CALL_FOR_PAPERS);
    }

    /** Two days, so the per-day rebuild is exercised rather than a single entry. */
    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId,
                                                       ConferenceFormat format) {
        return new ConferencePlanned(
                conferenceId, "JitterConf 2026",
                zt(DATE.atStartOfDay()), zt(DATE.plusDays(1).atStartOfDay()),
                "Moscone Center",
                new Address("747 Howard St", "San Francisco", "CA", "94103", "US", null),
                format);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
