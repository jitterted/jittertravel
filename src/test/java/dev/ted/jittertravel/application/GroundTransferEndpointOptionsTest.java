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
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

    private final BookedFlightsProjector flights = new BookedFlightsProjector();
    private final BookedHotelsProjector hotels = new BookedHotelsProjector();
    private final GroundTransferEndpointOptions options =
            new GroundTransferEndpointOptions(flights, hotels, new StaticAirportCityResolver());

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

    @Test
    void nothingBookedMeansNothingToOffer() {
        assertThat(options.choicesAt(NOW).isEmpty())
                .as("an empty form has to say so rather than silently offering two blank selects")
                .isTrue();
    }

    private void given(Event... events) {
        List<StoredEvent> stored = Stream.of(events)
                .map(GroundTransferEndpointOptionsTest::stored)
                .toList();
        flights.handle(stored.stream());
        hotels.handle(stored.stream());
    }

    private static FlightBooked flight(String from, String to, String departure, String arrival) {
        return new FlightBooked(FlightId.random(), "Airline", "F1",
                AirportCode.of(from), at(departure), AirportCode.of(to), at(arrival));
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

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
