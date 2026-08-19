package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ConferenceProjector implements EventStreamConsumer {
    private final Map<ConferenceId, ConferenceView> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferenceTentativelyPlanned event -> conferences.put(event.conferenceId(),
                        new ConferenceView(
                                event.conferenceId(),
                                event.name(),
                                event.venueName(),
                                event.venueAddress(),
                                event.startDate(),
                                event.endDate()
                        ));
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                case ConferenceAttendanceDeclined event -> conferences.remove(event.conferenceId());
                default -> {}
            }
        });
    }

    public List<ConferenceView> views(TimeView timeView, Instant now) {
        return conferences.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(view -> view.startDate().utc()))
                .toList();
    }

    /**
     * "One day long" is asked in the venue's own zone — a conference is single-day where it
     * happens, regardless of where the server or the viewer is.
     */
    public List<ConferenceView> migratableViews() {
        return conferences.values().stream()
                .filter(v -> v.startDate().localDateTime().toLocalDate()
                        .equals(v.endDate().localDateTime().toLocalDate()))
                .sorted(Comparator.comparing(v -> v.startDate().utc()))
                .toList();
    }

    public Optional<ConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId));
    }
}
