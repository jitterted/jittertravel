package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects the conference events into the OWNER-only {@code /conferences} list.
 * <p>
 * Folds attendance commitment the same way {@link ConferenceCalendarProjector} does — planned means
 * {@link AttendanceCommitment#WATCHING}, confirmed means {@link AttendanceCommitment#GOING},
 * declined or organizer-cancelled means gone. The two folds are deliberately written twice rather
 * than shared: each builds a different view record, and the shared part is a two-arm switch.
 */
public class ConferenceProjector implements EventStreamConsumer {
    private final Map<ConferenceId, ConferenceView> conferences = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferencePlanned event -> conferences.put(event.conferenceId(),
                        new ConferenceView(
                                event.conferenceId(),
                                event.name(),
                                event.venueName(),
                                event.venueAddress(),
                                event.startDate(),
                                event.endDate(),
                                AttendanceCommitment.WATCHING
                        ));
                // The basis is read and discarded: this page shows *whether* Ted is going, and why
                // he is going stays OWNER-private even from the owner's own list until slice 4
                // gives it somewhere to render.
                case ConferenceAttendanceConfirmed event ->
                        conferences.computeIfPresent(event.conferenceId(),
                                (id, view) -> going(view));
                case ConferenceCancelled event -> conferences.remove(event.conferenceId());
                case ConferenceAttendanceDeclined event -> conferences.remove(event.conferenceId());
                default -> {}
            }
        });
    }

    private ConferenceView going(ConferenceView view) {
        return new ConferenceView(
                view.conferenceId(), view.name(), view.venueName(), view.venueAddress(),
                view.startDate(), view.endDate(), AttendanceCommitment.GOING
        );
    }

    public List<ConferenceView> views(TimeView timeView, Instant now) {
        return conferences.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(view -> view.startDate().utc()))
                .toList();
    }

    public Optional<ConferenceView> findById(ConferenceId conferenceId) {
        return Optional.ofNullable(conferences.get(conferenceId));
    }
}
