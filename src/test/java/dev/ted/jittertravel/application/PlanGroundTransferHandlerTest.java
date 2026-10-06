package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.TransferEndpointWindow;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.PlanGroundTransferCommand;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.PlanGroundTransferRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanGroundTransferHandlerTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZoneId HAMBURG = ZoneId.of("Europe/Berlin");
    private static final HotelBookingId BOOKING = HotelBookingId.random();
    private static final TrainTripId TRIP = TrainTripId.random();
    private static final GatheringId GATHERING = GatheringId.random();
    private static final Address GATHERING_ADDRESS = new Address(
            "4242 Wynkoop St", "Denver", "CO", "80216", "US", "Denver");
    private static final Address HOTEL_ADDRESS = new Address(
            "10345 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree");

    private final HotelDetailsViewProjector hotelDetails = new HotelDetailsViewProjector();
    private final TrainDetailsViewProjector trainDetails = new TrainDetailsViewProjector();
    private final GatheringDetailsViewProjector gatheringDetails = new GatheringDetailsViewProjector();
    private final TransferEndpointProjector transferEndpoints =
            new TransferEndpointProjector(new StaticAirportCityResolver());
    private final PlanGroundTransferHandler handler = new PlanGroundTransferHandler(
            new GroundTransferEndpointResolver(hotelDetails, trainDetails, gatheringDetails,
                    new StaticAirportCityResolver(), new AirportZoneResolver(),
                    new LocationZoneResolver(), transferEndpoints));

    @Test
    void anAirportTokenResolvesToItsCodeAndItsCityAsTheMatchLocation() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel()));

        assertThat(command.originAirportCode())
                .isEqualTo("DEN");
        assertThat(command.originName())
                .as("an airport end has no private name to carry")
                .isEmpty();
        assertThat(command.origin())
                .isEqualTo(new Address("", "Denver", "", "", "", "Denver"));
    }

    @Test
    void aHotelTokenCopiesTheAddressVerbatimIncludingLocationForMatching() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel()));

        assertThat(command.destinationName())
                .isEqualTo("Marriott Lone Tree");
        assertThat(command.destinationAirportCode())
                .as("a hotel end publishes a city, never an airport code")
                .isEmpty();
        assertThat(command.destination())
                .isEqualTo(HOTEL_ADDRESS);
        assertThat(command.destination().locationForMatching())
                .isEqualTo("Lone Tree");
    }

    /** The one field on this form Ted types rather than picks — it reaches the command in his words. */
    @Test
    void theTypedModeReachesTheCommandInTedsOwnWords() {
        assertThat(handler.handle(requestWithMode("A16 hotel shuttle")).mode())
                .isEqualTo("A16 hotel shuttle");
    }

    /**
     * Both ways of not recording one — the untouched input the browser posts back as "", and the
     * null a caller may leave — settle here into a single absent value, so no reader downstream
     * has to know there were two.
     */
    @Test
    void aModeOfWhitespaceOrNothingAtAllArrivesAsTheAbsentSentinel() {
        assertThat(handler.handle(requestWithMode("   ")).mode())
                .isEmpty();
        assertThat(handler.handle(requestWithMode(null)).mode())
                .as("nothing typed at all is the same absence as whitespace")
                .isEmpty();
    }

    @Test
    void aModeKeepsItsOwnWordsButLosesTheSpaceAroundThem() {
        assertThat(handler.handle(requestWithMode("  U3, then Susan drives  ")).mode())
                .isEqualTo("U3, then Susan drives");
    }

    private PlanGroundTransferRequest requestWithMode(String mode) {
        PlanGroundTransferRequest request = request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel());
        return new PlanGroundTransferRequest(request.groundTransferId(), request.origin(),
                                             request.destination(), mode, request.date(),
                                             request.departureTime(), request.arrivalTime());
    }

    @Test
    void bothTimestampsTakeTheOriginsZone() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel()));

        assertThat(command.departsAt())
                .isEqualTo(ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 12, 0), DENVER));
        assertThat(command.arrivesAt())
                .isEqualTo(ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 12, 45), DENVER));
    }

    @Test
    void aTokenThatIsNeitherAnAirportNorAHotelIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("venue:Some Conference Center", "airport:DEN", bookedHotel())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessageContaining("venue:Some Conference Center");
    }

    /**
     * D12: there is no free-text fallback to quietly absorb a token the form never offered, so an
     * unrecognized end must fail rather than be recorded as an unmatched string.
     */
    @Test
    void aBookingIdThatNoLongerResolvesIsRejected() {
        StoredEvent cancellation = stored(new HotelBookingCancelled(BOOKING, "changed plans"));

        assertThatThrownBy(() -> handler.handle(
                request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel(), cancellation)))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void anAirportCodeTheTableDoesNotKnowIsRejectedAsAZoneFailure() {
        assertThatThrownBy(() -> handler.handle(
                request("airport:ZZZ", "hotel:" + BOOKING.id(), bookedHotel())))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void identicalOriginAndDestinationTokensAreRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("airport:DEN", "airport:DEN", bookedHotel())))
                .isInstanceOf(SameTransferEndpoints.class);
    }

    /**
     * The leg an airport option carries is for the form, not for the write path: the place it
     * resolves to is the airport, exactly as a bare {@code airport:DEN} resolves.
     */
    @Test
    void aLegScopedAirportTokenResolvesToTheSameAirportABareOneDoes() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN:" + FlightId.random().id(), "hotel:" + BOOKING.id(),
                        bookedHotel()));

        assertThat(command.originAirportCode())
                .isEqualTo("DEN");
        assertThat(command.origin())
                .as("the flight id reaches neither the command nor the event")
                .isEqualTo(new Address("", "Denver", "", "", "", "Denver"));
    }

    /**
     * The rule the leg could have broken: landing at DEN on Monday's flight and leaving from DEN on
     * Thursday's is two tokens and one place, and a transfer between them still records no journey.
     * So "two different places" is asked of the place token, never of the submitted one.
     */
    @Test
    void twoLegsThroughOneAirportAreStillTheSamePlace() {
        assertThatThrownBy(() -> handler.handle(
                request("airport:DEN:" + FlightId.random().id(),
                        "airport:DEN:" + FlightId.random().id(),
                        bookedHotel())))
                .isInstanceOf(SameTransferEndpoints.class);
    }

    /**
     * The resolver reads {@code den} and {@code " DEN"} as DEN, so the same-place rule has to read
     * them that way too — otherwise a spelling difference is a way round it. The form never offers
     * two spellings; a hand-made POST can.
     */
    @Test
    void anAirportCodeInAnotherCaseOrWithSpacesIsStillTheSamePlace() {
        assertThatThrownBy(() -> handler.handle(
                request("airport:den", "airport: DEN :" + FlightId.random().id(), bookedHotel())))
                .isInstanceOf(SameTransferEndpoints.class);
    }

    /** A hotel id is a UUID, which parses in either case — so upper and lower name one booking. */
    @Test
    void aHotelIdInAnotherCaseIsStillTheSameBooking() {
        String bookingId = BOOKING.id().toString();
        assertThatThrownBy(() -> handler.handle(
                request("hotel:" + bookingId.toUpperCase(), "hotel:" + bookingId.toLowerCase(),
                        bookedHotel())))
                .isInstanceOf(SameTransferEndpoints.class);
    }

    /** And two airports are still two places, however the legs that offered them line up. */
    @Test
    void twoLegsThroughTwoAirportsAreAcceptedAsEver() {
        FlightId oneLeg = FlightId.random();
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN:" + oneLeg.id(), "airport:SFO:" + oneLeg.id(), bookedHotel()));

        assertThat(command.originAirportCode())
                .isEqualTo("DEN");
        assertThat(command.destinationAirportCode())
                .isEqualTo("SFO");
    }

    /**
     * D5: the station's zone comes from the trip's own {@code ZonedTimestamp}, resolved at booking
     * by {@code StationZone} — so a Hamburg arrival is stamped Europe/Berlin even though the
     * curated table is never asked, and it cannot disagree with the train leg beside it.
     */
    @Test
    void aTrainArrivalTokenResolvesToThatStationInTheZoneItsOwnBookingRecorded() {
        PlanGroundTransferCommand command = handler.handle(
                request("train:" + TRIP.id() + ":arrival", "airport:DEN", bookedTrain()));

        assertThat(command.originName())
                .as("a station name is private exactly as a hotel name is, and rides on the event")
                .isEqualTo("Hamburg Hbf");
        assertThat(command.originAirportCode())
                .as("a station end publishes a city, never an airport code")
                .isEmpty();
        assertThat(command.origin())
                .isEqualTo(new Address("", "Hamburg", "", "", "DE", "Hamburg"));
        assertThat(command.departsAt())
                .as("stamped in the trip's own zone, which is what makes the lookup unnecessary")
                .isEqualTo(ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 12, 0), HAMBURG));
    }

    /** The other end of the same trip, from the same token shape with the other suffix (D7). */
    @Test
    void aTrainDepartureTokenResolvesToTheOtherStation() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "train:" + TRIP.id() + ":departure", bookedTrain()));

        assertThat(command.destinationName())
                .isEqualTo("Berlin Hbf");
        assertThat(command.destination())
                .isEqualTo(new Address("", "Berlin", "", "", "DE", "Berlin"));
    }

    /**
     * Each end of a trip is stamped in its own zone, so a trip that crosses one has two. The origin
     * is the arrival's zone and the destination is the departure's, and swapping the choice of end
     * would stamp the transfer in the wrong country.
     */
    @Test
    void eachStationEndTakesTheZoneOfItsOwnMomentOnATripThatCrossesZones() {
        ZoneId london = ZoneId.of("Europe/London");
        StoredEvent crossZoneTrip = stored(new TrainBooked(TRIP,
                new TrainStationAddress("Berlin Hbf", "Berlin", "DE", ""),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 5, 0), HAMBURG),
                new TrainStationAddress("London St Pancras", "London", "GB", ""),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 12, 0), london),
                "Eurostar 9"));

        PlanGroundTransferCommand fromArrival = handler.handle(
                request("train:" + TRIP.id() + ":arrival", "airport:DEN", crossZoneTrip));
        PlanGroundTransferCommand toDeparture = handler.handle(
                request("airport:DEN", "train:" + TRIP.id() + ":departure", crossZoneTrip));

        assertThat(fromArrival.departsAt().zone())
                .as("an origin that is the arrival station is stamped in the arrival's zone")
                .isEqualTo(london);
        assertThat(toDeparture.originWindow())
                .as("a bare airport origin has no window, so only the station end is under test")
                .isNull();
        assertThat(toDeparture.destinationWindow().start().zone())
                .as("a destination that is the departure station carries the departure's zone")
                .isEqualTo(HAMBURG);
    }

    /**
     * Two empty selects are two nothings, not "the same place": the refusal Ted should see is the
     * one that says to pick one, and a null place token must not compare equal to another.
     */
    @Test
    void twoMissingEndsAskForAPlaceRatherThanReportingTheSamePlace() {
        assertThat(GroundTransferEndpointResolver.placeToken(null))
                .isNull();
        assertThatThrownBy(() -> handler.handle(request(null, null)))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessage("Pick a place for each end");
    }

    /** The code is what precedes the first colon, so an empty one is named as empty, not as the leg. */
    @Test
    void anAirportTokenWhoseCodeIsEmptyIsRejectedAsNamingNoAirport() {
        assertThatThrownBy(() -> handler.handle(
                request("airport::" + FlightId.random().id(), "hotel:" + BOOKING.id(), bookedHotel())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessage("Not an airport: airport:");
    }

    @Test
    void aTrainTokenWithNoEndIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("train:" + TRIP.id(), "airport:DEN", bookedTrain())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessageContaining("train:" + TRIP.id());
    }

    @Test
    void aMalformedTripIdIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("train:not-a-uuid:arrival", "airport:DEN", bookedTrain())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessageContaining("train:not-a-uuid:arrival");
    }

    @Test
    void aTripIdThatNoLongerResolvesIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("train:" + TrainTripId.random().id() + ":arrival", "airport:DEN",
                        bookedTrain())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessageContaining("no longer available");
    }

    /**
     * What the form filled in for each choice is what the date rule is later held to: a hotel
     * reached is its check-in, a train left from is its arrival.
     */
    @Test
    void eachEndCarriesTheMomentTheFormOfferedForIt() {
        PlanGroundTransferCommand command = handler.handle(
                request("train:" + TRIP.id() + ":arrival", "hotel:" + BOOKING.id(),
                        bookedTrain(), bookedHotel()));

        assertThat(command.originWindow())
                .as("origin is the train's arrival, a window of one moment")
                .isEqualTo(TransferEndpointWindow.at(
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 11, 0), HAMBURG)));
        assertThat(command.destinationWindow())
                .as("destination is the whole stay, check-in through check-out")
                .isEqualTo(new TransferEndpointWindow(
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 15, 0), DENVER),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 18, 11, 0), DENVER)));
    }

    /** A bare airport was never offered as a leg, so it has no window for the date rule to hold. */
    @Test
    void anEndWithNoOfferedMomentCarriesNone() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "hotel:" + BOOKING.id(), bookedHotel()));

        assertThat(command.originWindow())
                .isNull();
    }

    /** A token offered for the other side has no window on this one: a check-out is not an arrival. */
    @Test
    void aTokenOnTheWrongSideCarriesNoWindow() {
        PlanGroundTransferCommand command = handler.handle(
                request("airport:DEN", "train:" + TRIP.id() + ":arrival", bookedTrain()));

        assertThat(command.destinationWindow())
                .isNull();
    }

    @Test
    void aGatheringTokenResolvesToItsVenueAndAddressAndCarriesItsWindow() {
        PlanGroundTransferCommand command = handler.handle(
                request("hotel:" + BOOKING.id(), "gathering:" + GATHERING.id(),
                        bookedHotel(), plannedGathering()));

        assertThat(command.destinationName())
                .as("the transfer records the venue, not the title")
                .isEqualTo("Mission Ballroom");
        assertThat(command.destination())
                .as("the gathering's address, verbatim")
                .isEqualTo(GATHERING_ADDRESS);
        assertThat(command.arrivesAt().zone())
                .isEqualTo(DENVER);
        assertThat(command.destinationWindow())
                .as("a gathering is there from its start to its end")
                .isEqualTo(new TransferEndpointWindow(
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 19, 0), DENVER),
                        ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 22, 0), DENVER)));
    }

    @Test
    void aGatheringIdThatNoLongerResolvesIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("hotel:" + BOOKING.id(), "gathering:" + GatheringId.random().id(),
                        bookedHotel())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessage("That gathering is no longer available");
    }

    @Test
    void aMalformedGatheringIdIsRejected() {
        assertThatThrownBy(() -> handler.handle(
                request("hotel:" + BOOKING.id(), "gathering:not-a-uuid", bookedHotel())))
                .isInstanceOf(UnknownTransferEndpoint.class)
                .hasMessage("Not a gathering: gathering:not-a-uuid");
    }

    private static StoredEvent plannedGathering() {
        return stored(new GatheringPlanned(GATHERING, "Dinner with the CTO", "Mission Ballroom",
                GATHERING_ADDRESS,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 19, 0), DENVER),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 22, 0), DENVER),
                false, ""));
    }

    private PlanGroundTransferRequest request(String origin, String destination, StoredEvent... history) {
        hotelDetails.handle(Stream.of(history));
        trainDetails.handle(Stream.of(history));
        gatheringDetails.handle(Stream.of(history));
        transferEndpoints.handle(Stream.of(history));
        return new PlanGroundTransferRequest(UUID.randomUUID().toString(), origin, destination,
                                             null, LocalDate.of(2026, 9, 14),
                                             LocalTime.of(12, 0), LocalTime.of(12, 45));
    }

    private static StoredEvent bookedHotel() {
        return stored(new HotelBooked(BOOKING, "Marriott Lone Tree", HOTEL_ADDRESS,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 15, 0), DENVER),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 18, 11, 0), DENVER),
                BookingIntent.FINAL, "", null));
    }

    private static StoredEvent bookedTrain() {
        return stored(new TrainBooked(TRIP,
                new TrainStationAddress("Berlin Hbf", "Berlin", "DE", ""),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 5, 0), HAMBURG),
                new TrainStationAddress("Hamburg Hbf", "Hamburg", "DE", ""),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, 11, 0), HAMBURG),
                "ICE 573"));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
