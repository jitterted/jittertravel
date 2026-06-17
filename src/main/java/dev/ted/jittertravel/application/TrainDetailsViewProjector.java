package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects train events into the {@link TrainDetailsView} used by the edit screen. Single-purpose:
 * serves one view (the change-train form). Both {@link TrainBooked} and {@link TrainChanged} are
 * full snapshots, so each new event simply overwrites the entry keyed by {@link TrainTripId}.
 * Mirrors {@link FlightDetailsViewProjector}.
 */
public class TrainDetailsViewProjector implements EventStreamConsumer {

    private final Map<TrainTripId, TrainDetailsView> viewsByTrip = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case TrainBooked e -> viewsByTrip.put(e.tripId(), toView(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                case TrainChanged e -> viewsByTrip.put(e.tripId(), toView(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                default -> { /* not a train event */ }
            }
        });
    }

    private static TrainDetailsView toView(TrainTripId tripId,
                                           TrainStationAddress departureStation,
                                           LocalDateTime departureDateTime,
                                           TrainStationAddress arrivalStation,
                                           LocalDateTime arrivalDateTime,
                                           String serviceId) {
        return new TrainDetailsView(tripId, departureStation, departureDateTime,
                arrivalStation, arrivalDateTime, serviceId);
    }

    public Optional<TrainDetailsView> findById(TrainTripId tripId) {
        return Optional.ofNullable(viewsByTrip.get(tripId));
    }
}