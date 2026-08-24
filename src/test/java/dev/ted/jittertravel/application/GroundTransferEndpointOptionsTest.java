package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class GroundTransferEndpointOptionsTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    // 20:00Z is 2:00 PM in Denver on 1 September — mid-afternoon, with a morning already
    // behind it. That is the moment these fixtures are "now", and the point of most of them.
    private static final Instant NOW = Instant.parse("2026-09-01T20:00:00Z");
    private static final HotelBookingId LONE_TREE = HotelBookingId.random();

    private final TransferEndpointProjector endpoints =
            new TransferEndpointProjector(new StaticAirportCityResolver());
    private final GroundTransferEndpointOptions options =
            new GroundTransferEndpointOptions(endpoints);

    /**
     * The direction rule Ted set (2026-08-20): you never travel <em>from</em> an airport you are
     * departing, nor <em>to</em> one you have just landed at. So each end offers only the leg that
     * can apply to it, and the ambiguity of a plain per-airport list is gone.
     */
    @Test
    void aFlightOffersItsArrivalAirportAsAnOriginAndItsDepartureAirportAsADestination() {
        given(flight("SFO", "DEN", "2026-09-14 08:00", "2026-09-14 11:30"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.arrivals())
                .extracting(TransferEndpointOption::token)
                .as("you leave from where you landed")
                .containsExactly("airport:DEN");
        assertThat(choices.departures())
                .extracting(TransferEndpointOption::token)
                .as("you travel to where you fly out from")
                .containsExactly("airport:SFO");
    }

    @Test
    void anArrivalOptionCarriesTheFlightsLandingDateAndTimeReadyForTheInputs() {
        given(flight("SFO", "DEN", "2026-09-14 08:00", "2026-09-14 11:30"));

        assertThat(options.choicesAt(NOW).arrivals())
                .singleElement()
                .extracting(TransferEndpointOption::label,
                            TransferEndpointOption::prefillDate,
                            TransferEndpointOption::prefillTime)
                .containsExactly("DEN — Denver · arrive Mon Sep 14, 11:30 AM (Airline F1)",
                                 "2026-09-14", "11:30");
    }

    @Test
    void aDepartureOptionCarriesTheFlightsTakeoffDateAndTime() {
        given(flight("DEN", "SFO", "2026-09-18 14:00", "2026-09-18 15:40"));

        assertThat(options.choicesAt(NOW).departures())
                .singleElement()
                .extracting(TransferEndpointOption::label,
                            TransferEndpointOption::prefillDate,
                            TransferEndpointOption::prefillTime)
                .containsExactly("DEN — Denver · depart Fri Sep 18, 2:00 PM (Airline F1)",
                                 "2026-09-18", "14:00");
    }

    /**
     * The reason a leg beats an airport: two trips through the same airport are two distinct
     * choices with distinct times, not one entry that silently means either.
     */
    @Test
    void twoArrivalsAtTheSameAirportAreTwoChoicesInChronologicalOrder() {
        given(flight("SFO", "DEN", "2026-09-14 08:00", "2026-09-14 11:30"),
              flight("JFK", "DEN", "2026-10-02 09:00", "2026-10-02 11:00"));

        assertThat(options.choicesAt(NOW).arrivals())
                .extracting(TransferEndpointOption::prefillDate, TransferEndpointOption::prefillTime)
                .containsExactly(tuple("2026-09-14", "11:30"),
                                 tuple("2026-10-02", "11:00"));
    }

    /**
     * Chronological means <em>actually</em> chronological, which only shows up across zones: this
     * London landing at 09:00 BST (08:00Z) happened before this Denver landing at 08:00 MDT
     * (14:00Z), and reading the two local clocks side by side would order them the other way round.
     * The old ordering sorted flight legs on their prefill strings — local wall clock — and hotels
     * on their instants; unifying on the instant is the one thing slice 2 changed on purpose.
     */
    @Test
    void arrivalsInDifferentZonesAreOrderedByWhenTheyActuallyHappened() {
        given(new FlightBooked(FlightId.random(), "Airline", "F1",
                      AirportCode.of("JFK"), at("2026-09-14 03:00"),
                      AirportCode.of("LHR"), london("2026-09-14 09:00")),
              flight("SFO", "DEN", "2026-09-14 06:00", "2026-09-14 08:00"));

        assertThat(options.choicesAt(NOW).arrivals())
                .extracting(TransferEndpointOption::token)
                .as("08:00Z in London beats 14:00Z in Denver, whatever the two clocks read")
                .containsExactly("airport:LHR", "airport:DEN");
    }

    /**
     * Both ends offer the same stay, each naming the moment that can apply to it: you leave a hotel
     * at check-out and reach one at check-in. The date is what makes the option matchable at all —
     * against the schedule problem that sent Ted here, and against a second stay in the same city.
     */
    @Test
    void aBookedHotelIsOfferedAtBothEndsCarryingTheMomentThatAppliesToEachEnd() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-09-14", "2026-09-18"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.checkOuts()).containsExactly(new TransferEndpointOption(
                "hotel:" + LONE_TREE.id(),
                "Marriott Lone Tree — Lone Tree · check out Fri Sep 18, 11:00 AM",
                "Lone Tree", "2026-09-18", "11:00"));
        assertThat(choices.checkIns()).containsExactly(new TransferEndpointOption(
                "hotel:" + LONE_TREE.id(),
                "Marriott Lone Tree — Lone Tree · check in Mon Sep 14, 3:00 PM",
                "Lone Tree", "2026-09-14", "15:00"));
    }

    /**
     * Two stays in one city were one unreadable choice twice over — the case that forced the dates
     * on (Ted, 2026-08-21).
     */
    @Test
    void twoStaysInOneCityAreToldApartByTheirDates() {
        HotelBookingId second = HotelBookingId.random();
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree", "2026-09-14", "2026-09-18"),
              bookedHotel(second, "Hyatt Lone Tree", "Lone Tree", "2026-09-20", "2026-09-22"));

        assertThat(options.choicesAt(NOW).checkOuts())
                .extracting(TransferEndpointOption::label)
                .as("chronological, so the stay Ted is thinking about is where he expects it")
                .containsExactly("Marriott Lone Tree — Lone Tree · check out Fri Sep 18, 11:00 AM",
                                 "Hyatt Lone Tree — Lone Tree · check out Tue Sep 22, 11:00 AM");
    }

    @Test
    void aCancelledStayIsNotSomewhereTedCanBeDroppedOff() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree", "2026-09-14", "2026-09-18"),
              new HotelBookingCancelled(LONE_TREE, "changed plans"));

        GroundTransferEndpointChoices cancelled = options.choicesAt(NOW);

        assertThat(cancelled.checkOuts()).isEmpty();
        assertThat(cancelled.checkIns()).isEmpty();
    }

    /**
     * D14, and the reason it exists: a transfer is written down long after it happened. A flight
     * that landed at 08:00 is still today's flight at 2 in the afternoon, and both of its airports
     * are still places today's taxi ran between. Scoping to the instant made both vanish by lunch.
     */
    @Test
    void aFlightEarlierTodayStillOffersBothOfItsAirports() {
        // Denver is UTC-6 here: took off 03:00 local, landed 08:00 local. Both are behind NOW.
        given(flight("SFO", "DEN", "2026-09-01 03:00", "2026-09-01 08:00"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.arrivals())
                .extracting(TransferEndpointOption::token)
                .as("the taxi from the airport is normally entered that evening")
                .containsExactly("airport:DEN");
        assertThat(choices.departures())
                .extracting(TransferEndpointOption::token)
                .as("and so is the taxi that got you to it")
                .containsExactly("airport:SFO");
    }

    @Test
    void aFlightStillInTheAirOffersTheAirportItIsAboutToLandAt() {
        // Lands 23:00 local, hours after NOW — the flight's own departure-based FUTURE window has
        // already closed, but the arrival has not happened yet at all.
        given(flight("SFO", "DEN", "2026-09-01 03:00", "2026-09-01 23:00"));

        assertThat(options.choicesAt(NOW).arrivals())
                .extracting(TransferEndpointOption::token)
                .containsExactly("airport:DEN");
    }

    @Test
    void yesterdaysFlightIsGoneFromBothEnds() {
        given(flight("LHR", "JFK", "2026-08-31 08:00", "2026-08-31 11:30"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.arrivals())
                .as("today or later — yesterday still drops off")
                .isEmpty();
        assertThat(choices.departures()).isEmpty();
    }

    /** The most common transfer of all: check out at 11:00, ride to the airport, write it up later. */
    @Test
    void aStayCheckedOutOfThisMorningIsStillOffered() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-08-28", "2026-09-01"));

        assertThat(options.choicesAt(NOW).checkOuts())
                .extracting(TransferEndpointOption::label)
                .containsExactly("Marriott Lone Tree — Lone Tree · check out Tue Sep 1, 11:00 AM");
    }

    @Test
    void aStayCheckedOutOfYesterdayIsGone() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-08-28", "2026-08-31"));

        GroundTransferEndpointChoices gone = options.choicesAt(NOW);

        assertThat(gone.checkOuts()).isEmpty();
        assertThat(gone.checkIns()).isEmpty();
    }

    @Test
    void aFinishedFlightAndAFinishedStayDropOffEntirely() {
        given(flight("LHR", "JFK", "2026-08-01 08:00", "2026-08-01 11:30"),
              bookedHotel(HotelBookingId.random(), "Last Month's Hotel", "New York",
                      "2026-08-01", "2026-08-03"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.arrivals()).isEmpty();
        assertThat(choices.departures()).isEmpty();
        assertThat(choices.checkOuts()).isEmpty();
        assertThat(choices.checkIns()).isEmpty();
    }

    /**
     * Mid-stay, so its check-in is already behind us — and it is still offered at both ends: the
     * ride to a gathering and back is a transfer, and the "To" list is filtered on check-out for
     * exactly this reason. Note what the prefill then does: choosing it moves the date field back
     * to the day he arrived. That is the known weak spot of the hotel prefill (Ted, 2026-08-21),
     * which is why the label says which moment it is filling in.
     */
    @Test
    void anOngoingStayIsStillOfferedAtBothEndsThoughItsCheckInHasPassed() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-08-30", "2026-09-04"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.checkOuts())
                .extracting(TransferEndpointOption::label)
                .containsExactly("Marriott Lone Tree — Lone Tree · check out Fri Sep 4, 11:00 AM");
        assertThat(choices.checkIns())
                .extracting(TransferEndpointOption::prefillDate)
                .containsExactly("2026-08-30");
    }

    /**
     * The reported bug, from the form's side: a station is now offered at both ends, and its label
     * mirrors a flight leg's so the two lists read alike.
     */
    @Test
    void aTrainOffersItsArrivalStationAsAnOriginAndItsDepartureStationAsADestination() {
        given(train("Berlin Hbf", "Berlin", "Hamburg Hbf", "Hamburg",
                "2026-09-16 05:00", "2026-09-16 11:00", "ICE 573"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.trainArrivals())
                .singleElement()
                .extracting(TransferEndpointOption::label,
                            TransferEndpointOption::prefillDate,
                            TransferEndpointOption::prefillTime)
                .containsExactly("Hamburg Hbf — Hamburg · arrive Wed Sep 16, 11:00 AM (ICE 573)",
                                 "2026-09-16", "11:00");
        assertThat(choices.trainDepartures())
                .singleElement()
                .extracting(TransferEndpointOption::label)
                .isEqualTo("Berlin Hbf — Berlin · depart Wed Sep 16, 5:00 AM (ICE 573)");
    }

    @Test
    void aTrainWithNoServiceIdIsLabelledWithoutAnEmptyBracket() {
        given(train("Berlin Hbf", "Berlin", "Hamburg Hbf", "Hamburg",
                "2026-09-16 05:00", "2026-09-16 11:00", ""));

        assertThat(options.choicesAt(NOW).trainArrivals())
                .singleElement()
                .extracting(TransferEndpointOption::label)
                .isEqualTo("Hamburg Hbf — Hamburg · arrive Wed Sep 16, 11:00 AM");
    }

    /** Same day rule as a flight: each end is judged on its own moment, in its own zone. */
    @Test
    void yesterdaysTrainIsGoneFromBothEnds() {
        given(train("Berlin Hbf", "Berlin", "Hamburg Hbf", "Hamburg",
                "2026-08-31 05:00", "2026-08-31 11:00", "ICE 573"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.trainArrivals()).isEmpty();
        assertThat(choices.trainDepartures()).isEmpty();
    }

    @Test
    void aTrainEarlierTodayStillOffersBothOfItsStations() {
        given(train("Berlin Hbf", "Berlin", "Hamburg Hbf", "Hamburg",
                "2026-09-01 05:00", "2026-09-01 11:00", "ICE 573"));

        GroundTransferEndpointChoices choices = options.choicesAt(NOW);

        assertThat(choices.trainArrivals())
                .as("the taxi from the station is normally entered that evening")
                .hasSize(1);
        assertThat(choices.trainDepartures()).hasSize(1);
    }

    @Test
    void nothingBookedMeansNothingToOffer() {
        assertThat(options.choicesAt(NOW).isEmpty())
                .as("an empty form has to say so rather than silently offering two blank selects")
                .isTrue();
    }

    private void given(Event... events) {
        endpoints.handle(Stream.of(events).map(GroundTransferEndpointOptionsTest::stored));
    }

    private static FlightBooked flight(String from, String to, String departure, String arrival) {
        return new FlightBooked(FlightId.random(), "Airline", "F1",
                AirportCode.of(from), at(departure), AirportCode.of(to), at(arrival));
    }

    private static TrainBooked train(String fromStation, String fromCity,
                                     String toStation, String toCity,
                                     String departure, String arrival, String serviceId) {
        return new TrainBooked(TrainTripId.random(),
                new TrainStationAddress(fromStation, fromCity, "DE", ""), at(departure),
                new TrainStationAddress(toStation, toCity, "DE", ""), at(arrival),
                serviceId);
    }

    private static HotelBooked bookedHotel(HotelBookingId bookingId, String name, String city,
                                           String checkIn, String checkOut) {
        return new HotelBooked(bookingId, name,
                new Address("1 Street", city, "CO", "80124", "US", city),
                at(checkIn + " 15:00"), at(checkOut + " 11:00"), BookingIntent.FINAL, "", null);
    }

    private static ZonedTimestamp at(String dayAndTime) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.parse(dayAndTime.replace(' ', 'T')), DENVER);
    }

    private static ZonedTimestamp london(String dayAndTime) {
        return ZonedTimestamp.fromLocal(
                LocalDateTime.parse(dayAndTime.replace(' ', 'T')), ZoneId.of("Europe/London"));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
