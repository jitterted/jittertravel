package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class BookedTrainsProjector implements EventStreamConsumer {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("M-dd-uuuu h:mma", Locale.ENGLISH);

    private final Map<TrainTripId, BookedTrainView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            if (stored.payload() instanceof TrainBooked event) {
                viewsById.put(event.tripId(), new BookedTrainView(
                        event.tripId(),
                        event.serviceId(),
                        event.departureStation().name(),
                        event.departureStation().city(),
                        event.departureStation().mapsUrl(),
                        event.departureDateTime(),
                        event.departureDateTime().format(DISPLAY),
                        event.arrivalStation().name(),
                        event.arrivalStation().city(),
                        event.arrivalStation().mapsUrl(),
                        event.arrivalDateTime(),
                        event.arrivalDateTime().format(DISPLAY)
                ));
            }
        });
    }

    public List<BookedTrainView> views() {
        return viewsById.values().stream()
                .sorted(Comparator.comparing(BookedTrainView::departureDateTime))
                .toList();
    }
}
