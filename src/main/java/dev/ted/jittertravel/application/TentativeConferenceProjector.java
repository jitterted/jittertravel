package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class TentativeConferenceProjector implements EventStreamConsumer {
    private final Map<ConferenceId, TentativeConferenceView> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferenceTentativelyPlanned event -> conferences.put(event.conferenceId(),
                        new TentativeConferenceView(
                                event.conferenceId(),
                                event.name(),
                                event.venueName(),
                                event.venueAddress(),
                                event.startDate(),
                                event.endDate()
                        ));
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                default -> {}
            }
        });
    }

    public List<TentativeConferenceView> views(TimeView timeView, LocalDateTime now) {
        return conferences.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(TentativeConferenceView::startDate))
                .toList();
    }

    public List<TentativeConferenceView> migratableViews() {
        return conferences.values().stream()
                .filter(v -> v.startDate().toLocalDate().equals(v.endDate().toLocalDate()))
                .sorted(Comparator.comparing(TentativeConferenceView::startDate))
                .toList();
    }

    public Optional<TentativeConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId));
    }
}
