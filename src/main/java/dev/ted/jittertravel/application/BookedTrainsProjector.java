package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class BookedTrainsProjector implements EventStreamConsumer {

    private final Map<TrainTripId, BookedTrainView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case TrainBooked e -> viewsById.put(e.tripId(), toView(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                case TrainChanged e -> viewsById.put(e.tripId(), toView(
                        e.tripId(), e.departureStation(), e.departureDateTime(),
                        e.arrivalStation(), e.arrivalDateTime(), e.serviceId()));
                default -> { /* not a train event */ }
            }
        });
    }

    private static BookedTrainView toView(TrainTripId tripId,
                                          TrainStationAddress departureStation,
                                          ZonedTimestamp departureDateTime,
                                          TrainStationAddress arrivalStation,
                                          ZonedTimestamp arrivalDateTime,
                                          String serviceId) {
        return new BookedTrainView(
                tripId,
                serviceId,
                departureStation.name(),
                departureStation.city(),
                departureStation.mapsUrl(),
                departureDateTime,
                arrivalStation.name(),
                arrivalStation.city(),
                arrivalStation.mapsUrl(),
                arrivalDateTime
        );
    }

    public List<BookedTrainView> views(TimeView filter, Instant now) {
        return viewsById.values().stream()
                .filter(view -> filter.includes(view, now))
                .sorted(Comparator.comparing(view -> view.departureDateTime().utc()))
                .toList();
    }
}
