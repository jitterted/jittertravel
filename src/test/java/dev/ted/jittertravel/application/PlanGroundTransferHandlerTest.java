package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
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
    private static final Address HOTEL_ADDRESS = new Address(
            "10345 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree");

    private final HotelDetailsViewProjector hotelDetails = new HotelDetailsViewProjector();
    private final TrainDetailsViewProjector trainDetails = new TrainDetailsViewProjector();
    private final PlanGroundTransferHandler handler = new PlanGroundTransferHandler(
            new GroundTransferEndpointResolver(hotelDetails, trainDetails,
                    new StaticAirportCityResolver(), new AirportZoneResolver(),
                    new LocationZoneResolver()));

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

    private PlanGroundTransferRequest request(String origin, String destination, StoredEvent... history) {
        hotelDetails.handle(Stream.of(history));
        trainDetails.handle(Stream.of(history));
        PlanGroundTransferRequest request = new PlanGroundTransferRequest();
        request.setGroundTransferId(UUID.randomUUID().toString());
        request.setOrigin(origin);
        request.setDestination(destination);
        request.setDate(LocalDate.of(2026, 9, 14));
        request.setDepartureTime(LocalTime.of(12, 0));
        request.setArrivalTime(LocalTime.of(12, 45));
        return request;
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
