package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedTrainsProjectorTest {

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 6, 9, 9, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 6, 9, 13, 0);

    @Test
    void trainBookedAddsViewWithAllFields() {
        BookedTrainsProjector projector = new BookedTrainsProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked event = new TrainBooked(
                tripId,
                new TrainStationAddress("London Euston", "London", "UK", "https://maps.google.com/euston"),
                DEPARTURE,
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                ARRIVAL,
                "LNER - Azuma 1A34"
        );

        projector.handle(Stream.of(stored(event)));

        List<BookedTrainView> views = projector.views();
        assertThat(views)
                .hasSize(1);
        BookedTrainView view = views.getFirst();
        assertThat(view.tripId())
                .isEqualTo(tripId);
        assertThat(view.serviceId())
                .isEqualTo("LNER - Azuma 1A34");
        assertThat(view.departureStationName())
                .isEqualTo("London Euston");
        assertThat(view.departureCity())
                .isEqualTo("London");
        assertThat(view.departureMapsUrl())
                .isEqualTo("https://maps.google.com/euston");
        assertThat(view.departureDateTime())
                .isEqualTo(DEPARTURE);
        assertThat(view.arrivalStationName())
                .isEqualTo("Manchester Piccadilly");
        assertThat(view.arrivalCity())
                .isEqualTo("Manchester");
        assertThat(view.arrivalMapsUrl())
                .isEmpty();
        assertThat(view.arrivalDateTime())
                .isEqualTo(ARRIVAL);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
