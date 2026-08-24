package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelChanged;
import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read model itself: what the events say, with no clock anywhere near it.
 * <p>
 * Every assertion here is about a fact of the event — which end this endpoint can serve, its place,
 * its moments, whether it still exists. What is still worth <em>offering today</em> is not in this
 * file at all, and that is the split D4 draws; {@code GroundTransferEndpointOptionsTest} owns the
 * other side of it.
 */
class TransferEndpointProjectorTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final HotelBookingId LONE_TREE = HotelBookingId.random();

    private final TransferEndpointProjector endpoints =
            new TransferEndpointProjector(new StaticAirportCityResolver());

    /** The direction rule, decided where the event is read (Ted, 2026-08-20). */
    @Test
    void aFlightYieldsAnArrivalRowAndADepartureRowAtTheRightAirports() {
        given(flight(FlightId.random(), "SFO", "DEN",
                "2026-09-14 08:00", "2026-09-14 11:30"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .singleElement()
                .extracting(TransferEndpointRow::token, TransferEndpointRow::name)
                .as("you leave from where you landed")
                .containsExactly("airport:DEN", "DEN");
        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_DEPARTURE))
                .singleElement()
                .extracting(TransferEndpointRow::token, TransferEndpointRow::name)
                .as("you travel to where you fly out from")
                .containsExactly("airport:SFO", "SFO");
    }

    @Test
    void anAirportRowCarriesTheCuratedCityAsBothItsLabelCityAndItsPlace() {
        given(flight(FlightId.random(), "SFO", "DEN",
                "2026-09-14 08:00", "2026-09-14 11:30"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .singleElement()
                .extracting(TransferEndpointRow::city, TransferEndpointRow::place)
                .containsExactly("Denver", new Place("Denver"));
    }

    /**
     * A flight's two moments are its own, and each end is offered on the strength of the one it is
     * about — which is what lets a flight still in the air offer the airport it will land at.
     */
    @Test
    void eachFlightEndIsOfferedOnItsOwnMoment() {
        given(flight(FlightId.random(), "SFO", "DEN",
                "2026-09-14 08:00", "2026-09-14 11:30"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .singleElement()
                .extracting(TransferEndpointRow::moment, TransferEndpointRow::offeredUntil)
                .containsExactly(at("2026-09-14 11:30"), at("2026-09-14 11:30"));
        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_DEPARTURE))
                .singleElement()
                .extracting(TransferEndpointRow::moment)
                .isEqualTo(at("2026-09-14 08:00"));
    }

    @Test
    void aFlightCarriesItsAirlineAndNumberAsTheLabelsParenthesis() {
        given(flight(FlightId.random(), "SFO", "DEN",
                "2026-09-14 08:00", "2026-09-14 11:30"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .singleElement()
                .extracting(TransferEndpointRow::detail)
                .isEqualTo("Airline F1");
    }

    /**
     * D3, the reason the map is keyed by occurrence and not by token: two trips through DEN are two
     * distinct choices with distinct times, and they both submit the same token because a transfer
     * is between places, not between flights.
     */
    @Test
    void twoFlightsIntoOneAirportAreTwoRowsSharingOneToken() {
        given(flight(FlightId.random(), "SFO", "DEN", "2026-09-14 08:00", "2026-09-14 11:30"),
              flight(FlightId.random(), "JFK", "DEN", "2026-10-02 09:00", "2026-10-02 11:00"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.token()).isEqualTo("airport:DEN"))
                .extracting(TransferEndpointRow::moment)
                .containsExactlyInAnyOrder(at("2026-09-14 11:30"), at("2026-10-02 11:00"));
    }

    /** A snapshot event, so the later one replaces the flight's rows rather than adding to them. */
    @Test
    void aChangedFlightSupersedesItsOwnRows() {
        FlightId rebooked = FlightId.random();
        given(flight(rebooked, "SFO", "DEN", "2026-09-14 08:00", "2026-09-14 11:30"),
              changedFlight(rebooked, "SFO", "JFK", "2026-09-15 08:00", "2026-09-15 16:30"));

        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .singleElement()
                .extracting(TransferEndpointRow::token, TransferEndpointRow::moment)
                .containsExactly("airport:JFK", at("2026-09-15 16:30"));
    }

    @Test
    void aStayYieldsACheckOutRowAndACheckInRowSharingOneToken() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-09-14", "2026-09-18"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT))
                .singleElement()
                .extracting(TransferEndpointRow::token, TransferEndpointRow::name,
                            TransferEndpointRow::moment)
                .containsExactly("hotel:" + LONE_TREE.id(), "Marriott Lone Tree",
                                 at("2026-09-18 11:00"));
        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_IN))
                .singleElement()
                .extracting(TransferEndpointRow::token, TransferEndpointRow::moment)
                .containsExactly("hotel:" + LONE_TREE.id(), at("2026-09-14 15:00"));
    }

    /**
     * The load-bearing asymmetry: a stay's check-in row is offered on the strength of its
     * <em>check-out</em>. Filter the "To" list on check-in instead and the hotel you are riding
     * toward disappears the moment you arrive — which is when the ride gets written down.
     */
    @Test
    void bothEndsOfAStayAreOfferedUntilItsCheckOut() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-09-14", "2026-09-18"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_IN))
                .singleElement()
                .extracting(TransferEndpointRow::offeredUntil)
                .isEqualTo(at("2026-09-18 11:00"));
    }

    /**
     * A stay's label city and its matching place are different fields on purpose: the words on the
     * building against the town the schedule names. Collapsing them breaks either the label or the
     * preselection, and the preselection silently.
     */
    @Test
    void aStayCarriesItsAddressCityForTheLabelAndItsMatchingLocationAsThePlace() {
        given(new HotelBooked(LONE_TREE, "SeminarZentrum Rückersbach",
                new Address("Schlossweg 1", "Rückersbach", "BY", "63867", "DE", "Johannesberg"),
                at("2026-09-09 15:00"), at("2026-09-13 11:00"), BookingIntent.FINAL, "", null));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT))
                .singleElement()
                .extracting(TransferEndpointRow::city, TransferEndpointRow::place)
                .containsExactly("Rückersbach", new Place("Johannesberg"));
    }

    @Test
    void aStayCarriesNoParenthesis() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                "2026-09-14", "2026-09-18"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT))
                .singleElement()
                .extracting(TransferEndpointRow::detail)
                .isEqualTo("");
    }

    @Test
    void aChangedStaySupersedesItsOwnRows() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                      "2026-09-14", "2026-09-18"),
              changedHotel(LONE_TREE, "Hyatt Lone Tree", "Lone Tree",
                      "2026-09-15", "2026-09-19"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT))
                .singleElement()
                .extracting(TransferEndpointRow::name, TransferEndpointRow::moment)
                .containsExactly("Hyatt Lone Tree", at("2026-09-19 11:00"));
    }

    /**
     * Absent, not flagged. {@code /booked-hotels} keeps a tombstone so the cancellation is visible;
     * this list is places Ted can be dropped off, and a cancelled booking is not one — which is why
     * the options class has no {@code cancelled} filter to get wrong.
     */
    @Test
    void aCancelledStayLeavesNoRowAtEitherEnd() {
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                      "2026-09-14", "2026-09-18"),
              new HotelBookingCancelled(LONE_TREE, "changed plans"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT)).isEmpty();
        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_IN)).isEmpty();
    }

    /** One cancellation must not empty the form of every other stay. */
    @Test
    void cancellingOneStayLeavesTheOthersAlone() {
        HotelBookingId kept = HotelBookingId.random();
        given(bookedHotel(LONE_TREE, "Marriott Lone Tree", "Lone Tree",
                      "2026-09-14", "2026-09-18"),
              bookedHotel(kept, "Hyatt Lone Tree", "Lone Tree", "2026-09-20", "2026-09-22"),
              new HotelBookingCancelled(LONE_TREE, "changed plans"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT))
                .singleElement()
                .extracting(TransferEndpointRow::token)
                .isEqualTo("hotel:" + kept.id());
    }

    @Test
    void anEventThatIsNoEndpointContributesNothing() {
        given(new HotelBookingCancelled(HotelBookingId.random(), "never booked here"));

        assertThat(endpoints.rowsFor(TransferEnd.HOTEL_CHECK_OUT)).isEmpty();
        assertThat(endpoints.rowsFor(TransferEnd.FLIGHT_ARRIVAL)).isEmpty();
    }

    private void given(Event... events) {
        endpoints.handle(Stream.of(events).map(TransferEndpointProjectorTest::stored));
    }

    private static FlightBooked flight(FlightId flightId, String from, String to,
                                       String departure, String arrival) {
        return new FlightBooked(flightId, "Airline", "F1",
                AirportCode.of(from), at(departure), AirportCode.of(to), at(arrival));
    }

    private static FlightChanged changedFlight(FlightId flightId, String from, String to,
                                               String departure, String arrival) {
        return new FlightChanged(flightId, "Airline", "F1",
                AirportCode.of(from), at(departure), AirportCode.of(to), at(arrival), "rebooked");
    }

    private static HotelBooked bookedHotel(HotelBookingId bookingId, String name, String city,
                                           String checkIn, String checkOut) {
        return new HotelBooked(bookingId, name, addressIn(city),
                at(checkIn + " 15:00"), at(checkOut + " 11:00"), BookingIntent.FINAL, "", null);
    }

    private static HotelChanged changedHotel(HotelBookingId bookingId, String name, String city,
                                             String checkIn, String checkOut) {
        return new HotelChanged(bookingId, name, addressIn(city),
                at(checkIn + " 15:00"), at(checkOut + " 11:00"), BookingIntent.FINAL, "", null);
    }

    private static Address addressIn(String city) {
        return new Address("1 Street", city, "CO", "80124", "US", city);
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
