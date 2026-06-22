package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class PlannedGatheringsProjector implements EventStreamConsumer {

    private final Map<GatheringId, PlannedGatheringView> viewsById = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                // Both events are full snapshots, so a change overwrites the planned view.
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

    private static PlannedGatheringView toView(GatheringId gatheringId,
                                               String title,
                                               String venueName,
                                               Address location,
                                               LocalDate date,
                                               LocalTime startTime,
                                               LocalTime endTime,
                                               boolean speaking,
                                               String infoUrl) {
        return new PlannedGatheringView(
                gatheringId,
                title,
                venueName,
                location.street(),
                location.city(),
                location.region(),
                location.postalCode(),
                location.country(),
                date,
                startTime,
                endTime,
                speaking,
                infoUrl
        );
    }

    public List<PlannedGatheringView> views(TimeView timeView, Instant now) {
        return viewsById.values().stream()
                .filter(view -> timeView.includes(view, now))
                .sorted(Comparator.comparing(PlannedGatheringView::date))
                .toList();
    }
}
