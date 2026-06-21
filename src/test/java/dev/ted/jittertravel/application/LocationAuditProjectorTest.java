package dev.ted.jittertravel.application;

import dev.ted.jittertravel.application.LocationAuditProjector.AuditedLocation;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocationAuditProjectorTest {

    private static final LocalDateTime SOME_TIME = LocalDateTime.of(2026, 6, 21, 11, 0);

    private final LocationAuditProjector projector = new LocationAuditProjector();

    @Test
    void collectsDistinctCityCountryPairsAcrossEventTypes() {
        projector.handle(streamOf(
                hotelBooked(new Address("1 St", "Frankfurt", "", "", "Germany", null)),
                hotelBooked(new Address("2 St", "Frankfurt", "", "", "Germany", null)),
                trainBooked(new TrainStationAddress("Gare de Lyon", "Paris", "France", ""),
                        new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "Germany", ""))));

        assertThat(projector.cities())
                .extracting(audited -> audited.location().label())
                .as("duplicate Frankfurt/Germany is collapsed; Paris is kept")
                .containsExactlyInAnyOrder("Frankfurt, Germany", "Paris, France");
    }

    @Test
    void retainsEverySourceEventForADuplicatedLocation() {
        projector.handle(streamOf(
                hotelBooked(new Address("1 St", "Frankfurt", "", "", "Germany", null)),
                hotelBooked(new Address("2 St", "Frankfurt", "", "", "Germany", null))));

        AuditedLocation frankfurt = projector.cities().iterator().next();
        assertThat(frankfurt.sources())
                .as("both hotel events that named Frankfurt are kept for diagnosis")
                .hasSize(2)
                .allSatisfy(source -> assertThat(source.type()).isEqualTo("HotelBooked"));
    }

    @Test
    void collectsDistinctAirportCodes() {
        projector.handle(streamOf(
                flightBooked("SFO", "JFK"),
                flightBooked("JFK", "LHR")));

        assertThat(projector.airports())
                .extracting(audited -> audited.airport().code())
                .containsExactlyInAnyOrder("SFO", "JFK", "LHR");
    }

    @Test
    void ignoresEventsWithoutLocations() {
        projector.handle(streamOf(flightBooked("SFO", "JFK")));

        assertThat(projector.cities())
                .isEmpty();
    }

    private static HotelBooked hotelBooked(Address address) {
        return new HotelBooked(HotelBookingId.random(), "Some Hotel", address,
                SOME_TIME, SOME_TIME.plusDays(1), BookingIntent.TENTATIVE, null);
    }

    private static TrainBooked trainBooked(TrainStationAddress departure, TrainStationAddress arrival) {
        return new TrainBooked(TrainTripId.random(), departure, SOME_TIME, arrival, SOME_TIME.plusHours(4), "ICE");
    }

    private static FlightBooked flightBooked(String departure, String arrival) {
        return new FlightBooked(FlightId.random(), "AA", "100",
                AirportCode.of(departure), SOME_TIME, AirportCode.of(arrival), SOME_TIME.plusHours(6));
    }

    private static java.util.stream.Stream<StoredEvent> streamOf(Event... events) {
        return List.of(events).stream()
                .map(event -> new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID()));
    }
}
