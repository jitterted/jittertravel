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

class TrainDetailsViewProjectorTest {

    private static final TrainStationAddress EUSTON =
            new TrainStationAddress("London Euston", "London", "UK", "https://maps.example/euston");
    private static final TrainStationAddress PICCADILLY =
            new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");

    @Test
    void trainBookedProducesDetailViewKeyedByTripId() {
        TrainDetailsViewProjector projector = new TrainDetailsViewProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked event = new TrainBooked(
                tripId, EUSTON, LocalDateTime.of(2026, 7, 1, 9, 0),
                PICCADILLY, LocalDateTime.of(2026, 7, 1, 13, 0), "LNER - Azuma 1A34");

        projector.handle(Stream.of(stored(event)));

        Optional<TrainDetailsView> view = projector.findById(tripId);
        assertThat(view).isPresent();
        assertThat(view.get().departureStation()).isEqualTo(EUSTON);
        assertThat(view.get().departureDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(view.get().arrivalStation()).isEqualTo(PICCADILLY);
        assertThat(view.get().arrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 13, 0));
        assertThat(view.get().serviceId()).isEqualTo("LNER - Azuma 1A34");
    }

    @Test
    void trainChangedOverwritesPreviousDetails() {
        TrainDetailsViewProjector projector = new TrainDetailsViewProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked booked = new TrainBooked(
                tripId, EUSTON, LocalDateTime.of(2026, 7, 1, 9, 0),
                PICCADILLY, LocalDateTime.of(2026, 7, 1, 13, 0), "LNER - Azuma 1A34");
        TrainChanged changed = new TrainChanged(
                tripId, EUSTON, LocalDateTime.of(2026, 7, 5, 11, 0),
                PICCADILLY, LocalDateTime.of(2026, 7, 5, 15, 30), "Avanti - 9M12");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        TrainDetailsView view = projector.findById(tripId).orElseThrow();
        assertThat(view.departureDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 5, 11, 0));
        assertThat(view.arrivalDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 5, 15, 30));
        assertThat(view.serviceId()).isEqualTo("Avanti - 9M12");
    }

    @Test
    void findByIdReturnsEmptyWhenNoTrip() {
        TrainDetailsViewProjector projector = new TrainDetailsViewProjector();
        assertThat(projector.findById(TrainTripId.random())).isEmpty();
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}