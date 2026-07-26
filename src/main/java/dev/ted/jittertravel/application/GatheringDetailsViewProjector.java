package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects gathering events into the {@link GatheringDetailsView} used by the edit screen.
 * Single-purpose: serves one view (the change-gathering form). Both {@link GatheringPlanned} and
 * {@link GatheringChanged} are full snapshots, so each new event simply overwrites the entry keyed
 * by {@link GatheringId}. Mirrors {@link TrainDetailsViewProjector}.
 */
public class GatheringDetailsViewProjector implements EventStreamConsumer {

    private final Map<GatheringId, GatheringDetailsView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case GatheringPlanned e -> viewsById.put(e.gatheringId(), toView(
                        e.gatheringId(), e.title(), e.venueName(), e.location(),
                        e.date(), e.startTime(), e.endTime(), e.speaking(), e.infoUrl()));
                case GatheringChanged e -> viewsById.put(e.gatheringId(), toView(
                        e.gatheringId(), e.title(), e.venueName(), e.location(),
                        e.date(), e.startTime(), e.endTime(), e.speaking(), e.infoUrl()));
                default -> { /* not a gathering event */ }
            }
        });
    }

    private static GatheringDetailsView toView(GatheringId gatheringId,
                                               String title,
                                               String venueName,
                                               Address location,
                                               LocalDate date,
                                               LocalTime startTime,
                                               LocalTime endTime,
                                               boolean speaking,
                                               String infoUrl) {
        return new GatheringDetailsView(gatheringId, title, venueName, location,
                date, startTime, endTime, speaking, infoUrl);
    }

    public Optional<GatheringDetailsView> findById(GatheringId gatheringId) {
        return Optional.ofNullable(viewsById.get(gatheringId));
    }
}
