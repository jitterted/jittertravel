package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The acceptance suite for schedule-problem detection: three real itineraries Ted supplied, with
 * the problems he expects from each. See {@code docs/ScheduleProblemsRewritePlan.md}.
 * <p>
 * These assert against {@link ScheduleGapProjector#problems()} only — no internals — so they
 * survive the detector being rewritten underneath them, which is the point. The unit-level rules
 * live next door in {@code ScheduleTimelineTest} and {@code ScheduleGapProjectorTest}; this file
 * exists to answer one question: <em>does it get a whole trip right?</em>
 */
class ScheduleProblemsAcceptanceTest {

    /**
     * The itinerary that motivated the rewrite (Ted, 2026-08-20). The old detector reported five
     * missing-travel problems, three of them phantoms reaching across the whole trip, and found
     * only one of the three missing-hotel runs.
     */
    @Nested
    class GermanSummer {

        private List<ScheduleProblem> problems() {
            return Itinerary.homeAt("San Francisco").inZone("Europe/Berlin")
                    .flight("MUC", "2026-08-25 09:15", "HAM", "2026-08-25 10:40")
                    .hotel("Reichshof", "Hamburg", "2026-08-25", "2026-08-26")
                    .hotel("Park Hotel", "Soltau", "2026-08-26", "2026-08-31")
                    .conference("SoCraTes", "Soltau", "2026-08-27", "2026-08-30")
                    .hotel("Reichshof", "Hamburg", "2026-08-31", "2026-09-07")
                    .gathering("Aachen JUG", "Aachen", "2026-09-08 19:00", "2026-09-08 22:00")
                    .conference("JCON", "Johannesberg", "2026-09-10", "2026-09-13")
                    .flight("FRA", "2026-09-14 11:00", "SFO", "2026-09-14 14:30")
                    .problems();
        }

        @Test
        void fiveMissingTravelGapsBetweenAdjacentLocations() {
            assertThat(only(problems(), ScheduleProblem.MissingTravel.class))
                    .extracting(ScheduleProblem.MissingTravel::fromCity,
                                ScheduleProblem.MissingTravel::toCity,
                                travel -> day(travel.arrivedAt()),
                                travel -> day(travel.nextDepartureAt()))
                    .containsExactly(
                            // check out of Hamburg on the 26th, check in to Soltau the same day
                            tuple("Hamburg", "Soltau", date("2026-08-26"), date("2026-08-26")),
                            tuple("Soltau", "Hamburg", date("2026-08-31"), date("2026-08-31")),
                            // check out of Hamburg on the 7th, gathering in Aachen on the 8th
                            tuple("Hamburg", "Aachen", date("2026-09-07"), date("2026-09-08")),
                            tuple("Aachen", "Johannesberg", date("2026-09-08"), date("2026-09-10")),
                            // conference ends on the 13th, the flight home leaves Frankfurt on the 14th
                            tuple("Johannesberg", "Frankfurt", date("2026-09-13"), date("2026-09-14")));
        }

        @Test
        void threeMissingHotelRunsSplitWhereTheCityChanges() {
            assertThat(only(problems(), ScheduleProblem.MissingHotel.class))
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(
                            // last known location on the night of the 7th is Hamburg, not Aachen
                            tuple("Hamburg", date("2026-09-07"), date("2026-09-08")),
                            // the gathering places him in Aachen, and he stays there until the 10th
                            tuple("Aachen", date("2026-09-08"), date("2026-09-10")),
                            // one run through the night of the 13th: a conference ending does not
                            // split it, because it would not split the booking either
                            tuple("Johannesberg", date("2026-09-10"), date("2026-09-14")));
        }

        @Test
        void noPhantomTravelGapBetweenTheTwoBookedFlights() {
            // The old detector walked adjacent *legs*, so the August arrival in Hamburg and the
            // September departure from Frankfurt — three weeks and a whole itinerary apart — read
            // as one gap.
            assertThat(only(problems(), ScheduleProblem.MissingTravel.class))
                    .extracting(ScheduleProblem.MissingTravel::fromCity, ScheduleProblem.MissingTravel::toCity)
                    .doesNotContain(tuple("Hamburg", "Frankfurt"),
                                    tuple("Hamburg", "Johannesberg"),
                                    tuple("Soltau", "Frankfurt"));
        }

        @Test
        void noDuplicateHotels() {
            assertThat(only(problems(), ScheduleProblem.DuplicateHotel.class))
                    .isEmpty();
        }
    }

    /**
     * A complete, correctly booked trip. The most valuable case in the suite: it is the one that
     * catches phantoms, and every night, every leg and every hand-off is accounted for.
     */
    @Nested
    class EuropeanConferences {

        private List<ScheduleProblem> problems() {
            return Itinerary.homeAt("San Francisco").inZone("Europe/Berlin")
                    .flight("SFO", "2026-06-06 13:55", "FRA", "2026-06-07 09:45")
                    .train("Frankfurt", "2026-06-07 11:30", "Cologne", "2026-06-07 13:00")
                    .hotel("Lindner Hotel", "Cologne", "2026-06-07", "2026-06-08")
                    .train("Cologne", "2026-06-08 10:00", "Gembloux", "2026-06-08 12:30")
                    .hotel("Hotel Les 3 Cles", "Gembloux", "2026-06-08", "2026-06-10")
                    .gathering("BeJUG", "Gembloux", "2026-06-09 19:00", "2026-06-09 22:00")
                    .train("Gembloux", "2026-06-10 10:00", "Antwerp", "2026-06-10 11:30")
                    .conference("DDD Europe 2026", "Antwerp", "2026-06-10", "2026-06-12")
                    .hotel("Park Inn", "Antwerp", "2026-06-10", "2026-06-13")
                    .hotel("Radisson", "Antwerp", "2026-06-13", "2026-06-15")
                    .train("Antwerp", "2026-06-15 10:00", "Brussels", "2026-06-15 11:00")
                    .hotel("Aparthotel", "Brussels", "2026-06-15", "2026-06-17")
                    .train("Brussels", "2026-06-17 09:00", "London", "2026-06-17 11:00")
                    .train("London", "2026-06-17 13:00", "Oxfordshire", "2026-06-17 14:30")
                    .hotel("Milton Mill House", "Oxfordshire", "2026-06-17", "2026-06-21")
                    .conference("SoCraTes UK 2026", "Oxfordshire", "2026-06-18", "2026-06-21")
                    .train("Oxfordshire", "2026-06-21 16:00", "London", "2026-06-21 17:30")
                    .hotel("Le Mirage", "London", "2026-06-21", "2026-06-22")
                    .flight("LHR", "2026-06-22 11:30", "FRA", "2026-06-22 14:00")
                    .train("Frankfurt", "2026-06-22 16:00", "Leipzig", "2026-06-22 19:00")
                    .hotel("Staycity Aparthotels", "Leipzig", "2026-06-22", "2026-06-24")
                    .gathering("Crafter Meetup", "Leipzig", "2026-06-23 19:00", "2026-06-23 22:00")
                    .train("Leipzig", "2026-06-24 10:00", "Munich", "2026-06-24 14:00")
                    .hotel("Bold Hotel", "Munich", "2026-06-24", "2026-06-27")
                    .conference("Event Modeling Conference", "Munich", "2026-06-25", "2026-06-26")
                    .hotel("Residence Inn by Marriott", "Munich", "2026-06-27", "2026-06-30")
                    .gathering("OpenValue Gathering", "Munich", "2026-06-29", "2026-06-29 22:00")
                    .flight("MUC", "2026-06-30 11:45", "SFO", "2026-06-30 14:30")
                    .problems();
        }

        @Test
        void aCompleteItineraryHasNoProblemsAtAll() {
            assertThat(problems())
                    .as("a fully booked trip must be silent — every problem here is a false alarm")
                    .isEmpty();
        }
    }

    /**
     * Two trips with no hotel booked at all for the first, and two hotels booked for the second.
     * {@code JFK} and {@code YYZ} resolve to New York and Toronto, so getting from the airport to
     * the city is not modelled and not a problem (D4).
     */
    @Nested
    class RushTours {

        private List<ScheduleProblem> problems() {
            return Itinerary.homeAt("San Francisco").inZone("America/New_York")
                    .flight("SFO", "2026-07-27 08:55", "JFK", "2026-07-27 17:45")
                    .gathering("Rush", "New York", "2026-07-28 19:30", "2026-07-28 23:30")
                    .gathering("Rush", "New York", "2026-08-03 18:30", "2026-08-03 23:30")
                    .flight("JFK", "2026-08-04 14:55", "SFO", "2026-08-04 18:24")
                    .flight("SFO", "2026-08-08 08:10", "YYZ", "2026-08-08 16:10")
                    .hotel("Oak House", "Toronto", "2026-08-08", "2026-08-14")
                    .hotel("Doubletree by Hilton", "Toronto", "2026-08-08", "2026-08-14")
                    .gathering("Rush", "Toronto", "2026-08-09 19:30", "2026-08-09 23:30")
                    .gathering("Rush", "Toronto", "2026-08-11 19:30", "2026-08-11 23:30")
                    .gathering("Rush", "Toronto", "2026-08-13 19:30", "2026-08-13 23:30")
                    .flight("YYZ", "2026-08-14 17:16", "SFO", "2026-08-14 19:52")
                    .problems();
        }

        @Test
        void everyNewYorkNightIsOneMissingHotelRun() {
            // Two gatherings a week apart, and he is in New York the whole time between them.
            assertThat(only(problems(), ScheduleProblem.MissingHotel.class))
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(tuple("New York", date("2026-07-27"), date("2026-08-04")));
        }

        @Test
        void twoHotelsCoveringTheSameNightsAreOneDuplicateProblem() {
            assertThat(only(problems(), ScheduleProblem.DuplicateHotel.class))
                    .extracting(ScheduleProblem.DuplicateHotel::firstNight,
                                ScheduleProblem.DuplicateHotel::lastNight,
                                duplicate -> duplicate.stays().stream()
                                        .map(ScheduleProblem.DuplicateStay::hotelName)
                                        .sorted()
                                        .toList())
                    .containsExactly(tuple(date("2026-08-08"), date("2026-08-13"),
                                           List.of("Doubletree by Hilton", "Oak House")));
        }

        @Test
        void noMissingTravelBecauseAirportsResolveToTheirCity() {
            assertThat(only(problems(), ScheduleProblem.MissingTravel.class))
                    .isEmpty();
        }

        @Test
        void theDaysAtHomeBetweenTheTwoTripsNeedNoHotel() {
            assertThat(only(problems(), ScheduleProblem.MissingHotel.class))
                    .extracting(ScheduleProblem.MissingHotel::city)
                    .doesNotContain("San Francisco");
        }
    }

    /**
     * A private event is a presence fact like any other (D9). It had never reached the projector at
     * all, so a dinner between two stays was invisible: the nights around it, and both journeys to
     * and from it, went unreported.
     */
    @Nested
    class PrivateEventsAreLocationsToo {

        private List<ScheduleProblem> problems() {
            return Itinerary.homeAt("San Francisco").inZone("Europe/Berlin")
                    .hotel("Reichshof", "Hamburg", "2026-08-25", "2026-08-27")
                    .privateEvent("Dinner with friends", "Berlin", "2026-08-28 19:00", "2026-08-28 22:00")
                    .hotel("Reichshof", "Hamburg", "2026-08-30", "2026-09-01")
                    .problems();
        }

        @Test
        void aDinnerInAnotherCityOpensTheJourneysAtEachEndOfIt() {
            assertThat(only(problems(), ScheduleProblem.MissingTravel.class))
                    .extracting(ScheduleProblem.MissingTravel::fromCity,
                                ScheduleProblem.MissingTravel::toCity)
                    .containsExactly(tuple("Hamburg", "Berlin"), tuple("Berlin", "Hamburg"));
        }

        @Test
        void theNightsAroundItBelongToTheCityItIsIn() {
            // Checked out of Hamburg on the 27th, dinner in Berlin on the 28th: the night of the
            // 27th is still Hamburg, and the 28th and 29th are Berlin, until the Hamburg stay
            // reclaims him on the 30th.
            assertThat(only(problems(), ScheduleProblem.MissingHotel.class))
                    .extracting(ScheduleProblem.MissingHotel::city,
                                ScheduleProblem.MissingHotel::checkIn,
                                ScheduleProblem.MissingHotel::checkOut)
                    .containsExactly(
                            tuple("Hamburg", date("2026-08-27"), date("2026-08-28")),
                            tuple("Berlin", date("2026-08-28"), date("2026-08-30")));
        }
    }

    /**
     * Days at home are not a problem to solve, so a missing journey out of home is dated by the day
     * he has to be somewhere else — not by the day he last landed (Ted, 2026-08-20). Both cases are
     * from Ted's real schedule.
     */
    @Nested
    class GapsOutOfHome {

        @Test
        void aGapAfterFourDaysAtHomeIsDatedByTheConferenceNotByTheLanding() {
            List<ScheduleProblem> problems = Itinerary.homeAt("San Francisco", "San Jose", "Oakland")
                    .inZone("America/Los_Angeles")
                    .flight("DEN", "2026-10-15 09:00", "SJC", "2026-10-15 10:45")
                    .conference("Conf", "North Gower", "2026-10-19", "2026-10-22")
                    .problems();

            assertThat(only(problems, ScheduleProblem.MissingTravel.class))
                    .singleElement()
                    .extracting(ScheduleProblem.MissingTravel::fromCity,
                                ScheduleProblem.MissingTravel::toCity,
                                gap -> day(gap.arrivedAt()),
                                gap -> day(gap.nextDepartureAt()))
                    .containsExactly("San Jose", "North Gower",
                                     date("2026-10-19"), date("2026-10-19"));
        }

        @Test
        void elevenDaysAtHomeDoNotStretchTheGapAcrossAllOfThem() {
            List<ScheduleProblem> problems = Itinerary.homeAt("San Francisco", "San Jose", "Oakland")
                    .inZone("America/Los_Angeles")
                    .flight("YYZ", "2026-10-31 09:00", "SFO", "2026-10-31 12:30")
                    .conference("JFall", "Ede", "2026-11-11", "2026-11-12")
                    .problems();

            assertThat(only(problems, ScheduleProblem.MissingTravel.class))
                    .singleElement()
                    .extracting(gap -> day(gap.arrivedAt()), gap -> day(gap.nextDepartureAt()))
                    .containsExactly(date("2026-11-11"), date("2026-11-11"));
        }

        @Test
        void beingAwayStillStrandsHimForEveryDayOfTheGap() {
            // The counterpart: away from home every day between the two really is part of the
            // problem, and the window must still span both ends.
            List<ScheduleProblem> problems = Itinerary.homeAt("San Francisco")
                    .inZone("Europe/Berlin")
                    .hotel("Reichshof", "Hamburg", "2026-08-31", "2026-09-07")
                    .gathering("Aachen JUG", "Aachen", "2026-09-08 19:00", "2026-09-08 22:00")
                    .problems();

            assertThat(only(problems, ScheduleProblem.MissingTravel.class))
                    .singleElement()
                    .extracting(gap -> day(gap.arrivedAt()), gap -> day(gap.nextDepartureAt()))
                    .containsExactly(date("2026-09-07"), date("2026-09-08"));
        }
    }

    /**
     * The gap a ground transfer exists to close (Ted, 2026-08-20): the flight lands at DEN, the
     * conference is in Lone Tree, and the journey between them is real but was unrecordable — so
     * {@code /schedule-problems} reported missing travel that no booking could ever satisfy. See
     * {@code docs/GroundTransferPlan.md}.
     */
    @Nested
    class AirportToVenueHop {

        @Test
        void withoutATransferTheHopFromTheAirportToTheVenueIsReportedMissing() {
            List<ScheduleProblem> problems = Itinerary.homeAt("San Francisco")
                    .inZone("America/Denver")
                    .flight("SFO", "2026-09-14 08:00", "DEN", "2026-09-14 11:30")
                    .hotel("Marriott Lone Tree", "Lone Tree", "2026-09-14", "2026-09-18")
                    .conference("dev2next", "Lone Tree", "2026-09-15", "2026-09-18")
                    .problems();

            assertThat(only(problems, ScheduleProblem.MissingTravel.class))
                    .singleElement()
                    .extracting(ScheduleProblem.MissingTravel::fromCity, ScheduleProblem.MissingTravel::toCity)
                    .containsExactly("Denver", "Lone Tree");
        }

        @Test
        void aGroundTransferFromTheAirportToTheHotelClosesTheGap() {
            List<ScheduleProblem> problems = Itinerary.homeAt("San Francisco")
                    .inZone("America/Denver")
                    .flight("SFO", "2026-09-14 08:00", "DEN", "2026-09-14 11:30")
                    .groundTransfer("Denver", "2026-09-14 12:00", "Lone Tree", "2026-09-14 12:45")
                    .hotel("Marriott Lone Tree", "Lone Tree", "2026-09-14", "2026-09-18")
                    .conference("dev2next", "Lone Tree", "2026-09-15", "2026-09-18")
                    .problems();

            assertThat(only(problems, ScheduleProblem.MissingTravel.class))
                    .as("the transfer is a leg like any other, so the Denver → Lone Tree hop is covered")
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Itinerary builder — an itinerary reads like the tables in the plan doc
    // -------------------------------------------------------------------------

    /**
     * Builds the event stream for one itinerary. Times are local wall-clock in the itinerary's
     * zone: real trips cross zones, but nothing detection does turns on the offset between two
     * legs of the same trip, and one zone keeps the fixtures readable. Cases that genuinely need
     * two zones build their events directly.
     */
    private static class Itinerary {

        private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final List<Event> events = new ArrayList<>();
        private final List<String> homeCities = new ArrayList<>();
        private ZoneId zone = ZoneId.of("Europe/Berlin");

        static Itinerary homeAt(String... cities) {
            Itinerary itinerary = new Itinerary();
            itinerary.homeCities.addAll(List.of(cities));
            return itinerary;
        }

        Itinerary inZone(String zoneId) {
            this.zone = ZoneId.of(zoneId);
            return this;
        }

        Itinerary flight(String fromCode, String departure, String toCode, String arrival) {
            events.add(new FlightBooked(FlightId.random(), "Airline", "F1",
                    AirportCode.of(fromCode), at(departure),
                    AirportCode.of(toCode), at(arrival)));
            return this;
        }

        Itinerary train(String fromCity, String departure, String toCity, String arrival) {
            events.add(new TrainBooked(TrainTripId.random(),
                    new TrainStationAddress(fromCity + " Hbf", fromCity, "XX", ""), at(departure),
                    new TrainStationAddress(toCity + " Hbf", toCity, "XX", ""), at(arrival), ""));
            return this;
        }

        /** Check-in and checkout carry the clock times a hotel actually uses; nothing turns on them. */
        Itinerary hotel(String name, String city, String checkIn, String checkOut) {
            return hotel(name, city, checkIn, checkOut, BookingIntent.FINAL);
        }

        Itinerary hotel(String name, String city, String checkIn, String checkOut, BookingIntent intent) {
            events.add(new HotelBooked(HotelBookingId.random(), name, address(city),
                    at(checkIn + " 15:00"), at(checkOut + " 11:00"), intent, "", null));
            return this;
        }

        Itinerary conference(String name, String city, String start, String end) {
            events.add(new ConferencePlanned(ConferenceId.random(), name,
                    at(start + " 09:00"), at(end + " 17:00"), name + " venue", address(city)));
            return this;
        }

        Itinerary gathering(String title, String city, String start, String end) {
            events.add(new GatheringPlanned(GatheringId.random(), title, title + " venue",
                    address(city), at(start), at(end), false, ""));
            return this;
        }

        /**
         * A short hop with no booking. Both ends are already-known places (an airport or a booked
         * hotel), so the fixture takes the two match cities directly — the resolution from a form
         * token to those cities is the handler's job, not the timeline's.
         */
        Itinerary groundTransfer(String fromCity, String departure, String toCity, String arrival) {
            events.add(new GroundTransferPlanned(GroundTransferId.random(),
                    "", fromCity + " pickup", address(fromCity),
                    "", toCity + " dropoff", address(toCity),
                    at(departure), at(arrival)));
            return this;
        }

        Itinerary privateEvent(String title, String city, String start, String end) {
            events.add(new PrivateEventPlanned(PrivateEventId.random(), title, title + " venue",
                    address(city), at(start), at(end)));
            return this;
        }

        List<ScheduleProblem> problems() {
            ScheduleGapProjector projector = new ScheduleGapProjector(
                    new StaticAirportCityResolver(), new HomeCities(homeCities));
            projector.handle(events.stream().map(Itinerary::stored));
            return projector.problems();
        }

        private Address address(String city) {
            return new Address("1 Street", city, "", "00000", "XX", null);
        }

        /** Accepts {@code yyyy-MM-dd} (start of day) or {@code yyyy-MM-dd HH:mm}. */
        private ZonedTimestamp at(String dayAndOptionalTime) {
            LocalDateTime local = dayAndOptionalTime.length() == "yyyy-MM-dd".length()
                    ? LocalDate.parse(dayAndOptionalTime, DAY).atStartOfDay()
                    : LocalDateTime.parse(dayAndOptionalTime, DAY_AND_TIME);
            return ZonedTimestamp.fromLocal(local, zone);
        }

        private static StoredEvent stored(Event event) {
            return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                    Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
        }
    }

    /** The problems of one kind, in read-model order — the claim each assertion here makes. */
    private static <T extends ScheduleProblem> List<T> only(List<ScheduleProblem> problems, Class<T> kind) {
        return problems.stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    private static LocalDate date(String isoDate) {
        return LocalDate.parse(isoDate);
    }

    private static LocalDate day(ZonedTimestamp timestamp) {
        return timestamp.localDateTime().toLocalDate();
    }
}
