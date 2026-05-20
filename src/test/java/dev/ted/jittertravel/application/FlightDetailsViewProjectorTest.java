package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlightDetailsViewProjectorTest {

    @Test
    void flightBookedProducesDetailViewKeyedByFlightId() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        FlightId flightId = FlightId.random();
        FlightBooked event = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45)
        );

        projector.handle(Stream.of(stored(event)));

        Optional<FlightDetailsView> view = projector.findById(flightId);
        assertThat(view).isPresent();
        assertThat(view.get().airline()).isEqualTo("United");
        assertThat(view.get().flightNumber()).isEqualTo("UA59");
        assertThat(view.get().departureAirport().code()).isEqualTo("SFO");
        assertThat(view.get().arrivalAirport().code()).isEqualTo("FRA");
        assertThat(view.get().departureDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 6, 13, 55));
        assertThat(view.get().arrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 45));
    }

    @Test
    void flightChangedOverwritesPreviousDetails() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        FlightId flightId = FlightId.random();
        FlightBooked booked = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45)
        );
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 8, 16, 0),
                AirportCode.of("MUC"), LocalDateTime.of(2026, 6, 9, 11, 30),
                null
        );

        projector.handle(Stream.of(stored(booked), stored(changed)));

        FlightDetailsView view = projector.findById(flightId).orElseThrow();
        assertThat(view.airline()).isEqualTo("Lufthansa");
        assertThat(view.flightNumber()).isEqualTo("LH441");
        assertThat(view.arrivalAirport().code()).isEqualTo("MUC");
        assertThat(view.departureDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 8, 16, 0));
    }

    @Test
    void findByIdReturnsEmptyWhenNoFlight() {
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();
        assertThat(projector.findById(FlightId.random())).isEmpty();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
