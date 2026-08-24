package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects private-event events into the {@link PlannedPrivateEventView} rows the
 * {@code /planned-private-events} list shows, keyed by {@link PrivateEventId}. The sibling of
 * {@link PlannedGatheringsProjector}, and its shape: one read model per surface, so the list's rows
 * are shaped by the list rather than borrowed from the cancel page.
 * <p>
 * A cancelled private event is a <em>hard removal</em> — it leaves this list as it leaves every
 * other read model. An entry left behind would keep asserting an evening that is not happening.
 */
public class PlannedPrivateEventsProjector implements EventStreamConsumer {

    private final Map<PrivateEventId, PlannedPrivateEventView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                // PrivateEventPlanned is a full snapshot, so this is a put; when PrivateEventChanged
                // arrives (ChangePrivateEventPlan.md slice 2) it lands here as a second one.
                case PrivateEventPlanned e -> viewsById.put(e.privateEventId(), toView(e));
                case PrivateEventCancelled e -> viewsById.remove(e.privateEventId());
                default -> { /* not a private-event event */ }
            }
        });
    }

    private PlannedPrivateEventView toView(PrivateEventPlanned e) {
        return new PlannedPrivateEventView(
                e.privateEventId(),
                e.title(),
                e.venueName(),
                e.location().street(),
                e.location().city(),
                e.location().region(),
                e.location().postalCode(),
                e.location().country(),
                e.startsAt(),
                e.endsAt()
        );
    }

    public List<PlannedPrivateEventView> views(TimeView timeView, Instant now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(view -> view.startsAt().utc()))
                .toList();
    }
}
