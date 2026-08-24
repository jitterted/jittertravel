package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects private-event events into the {@link PrivateEventDetailsView} the cancel confirmation
 * page reads. Single-purpose, like {@link GroundTransferDetailsViewProjector}: one view, keyed by
 * {@link PrivateEventId}, so a stale link resolves to nothing rather than to the wrong evening.
 * <p>
 * A cancelled private event is removed outright — the page that offers cancelling must not offer it
 * twice, and there is no "cancelled" state anywhere else in the app either.
 */
public class PrivateEventDetailsViewProjector implements EventStreamConsumer {

    private final Map<PrivateEventId, PrivateEventDetailsView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case PrivateEventPlanned e -> viewsById.put(e.privateEventId(), toView(e));
                case PrivateEventCancelled e -> viewsById.remove(e.privateEventId());
                default -> { /* not a private-event event */ }
            }
        });
    }

    private PrivateEventDetailsView toView(PrivateEventPlanned e) {
        return new PrivateEventDetailsView(
                e.privateEventId(),
                e.title(),
                e.venueName(),
                e.location().city(),
                e.location().country(),
                e.startsAt().localDateTime(),
                e.endsAt().localDateTime());
    }

    public Optional<PrivateEventDetailsView> findById(PrivateEventId id) {
        return Optional.ofNullable(viewsById.get(id));
    }
}
