package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class TentativeConferenceProjector implements EventStreamConsumer {
    private final Map<ConferenceId, TentativeConferenceView> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            if (storedEvent.payload() instanceof ConferenceTentativelyPlanned event) {
                conferences.put(event.conferenceId(), new TentativeConferenceView(
                        event.conferenceId(),
                        event.name(),
                        event.startDate(),
                        event.endDate(),
                        event.venueAddress().city(),
                        event.venueAddress().country()
                ));
            }
        });
    }

    public List<TentativeConferenceView> views() {
        return conferences.values().stream()
                .sorted(Comparator.comparing(TentativeConferenceView::startDate))
                .toList();
    }
}
