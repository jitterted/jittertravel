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
    private static final HotelBookingId BOOKING = HotelBookingId.random();
    private static final Address HOTEL_ADDRESS = new Address(
            "10345 Park Meadows Dr", "Lone Tree", "CO", "80124", "US", "Lone Tree");

    private final HotelDetailsViewProjector hotelDetails = new HotelDetailsViewProjector();
    private final PlanGroundTransferHandler handler = new PlanGroundTransferHandler(
            new GroundTransferEndpointResolver(hotelDetails, new StaticAirportCityResolver(),
                    new AirportZoneResolver(), new LocationZoneResolver()));

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

    private PlanGroundTransferRequest request(String origin, String destination, StoredEvent... history) {
        hotelDetails.handle(Stream.of(history));
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

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
