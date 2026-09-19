package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventMatchingLocationChanged;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects private-event events into the {@link PrivateEventMatchingLocationView} the "match
 * location" page reads. Single-purpose and keyed by {@link PrivateEventId}, like
 * {@link PrivateEventDetailsViewProjector}, so a stale link resolves to nothing rather than to the
 * wrong evening.
 * <p>
 * It is the second read model to apply {@link PrivateEventMatchingLocationChanged} — the first
 * being {@code ScheduleGapProjector}, which is the one the override actually exists for. This one
 * applies it so the form shows the value already in force rather than the one originally typed;
 * a page that offered the stale value would quietly undo a correction on the next submit.
 * <p>
 * A cancelled private event is removed outright: there is no "cancelled" state anywhere in this
 * app, and re-matching an evening that is gone is refused on the write path anyway.
 */
public class PrivateEventMatchingLocationViewProjector implements EventStreamConsumer {

    private final Map<PrivateEventId, PrivateEventMatchingLocationView> viewsById =
            new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case PrivateEventPlanned e -> viewsById.put(e.privateEventId(), toView(e));
                // computeIfPresent, not put: an override for an evening this projector does not
                // hold (cancelled, or a replay order this class does not get to assume) must not
                // conjure a view out of one field.
                case PrivateEventMatchingLocationChanged e ->
                        viewsById.computeIfPresent(e.privateEventId(),
                                (id, view) -> matchedIn(view, e.locationForMatching()));
                case PrivateEventCancelled e -> viewsById.remove(e.privateEventId());
                default -> { /* not a private-event event */ }
            }
        });
    }

    private PrivateEventMatchingLocationView toView(PrivateEventPlanned e) {
        return new PrivateEventMatchingLocationView(
                e.privateEventId(),
                e.title(),
                e.venueName(),
                e.location().city(),
                e.location().country(),
                e.location().locationForMatching(),
                e.startsAt().localDateTime(),
                e.endsAt().localDateTime());
    }

    private PrivateEventMatchingLocationView matchedIn(PrivateEventMatchingLocationView view,
                                                       String locationForMatching) {
        return new PrivateEventMatchingLocationView(
                view.privateEventId(), view.title(), view.venueName(), view.city(), view.country(),
                locationForMatching, view.startsAt(), view.endsAt());
    }

    public Optional<PrivateEventMatchingLocationView> findById(PrivateEventId id) {
        return Optional.ofNullable(viewsById.get(id));
    }
}
