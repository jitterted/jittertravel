package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

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
            if (stored.payload() instanceof GatheringPlanned e) {
                viewsById.put(e.gatheringId(), new PlannedGatheringView(
                        e.gatheringId(),
                        e.title(),
                        e.venueName(),
                        e.location().city(),
                        e.location().country(),
                        e.date(),
                        e.startTime(),
                        e.endTime(),
                        e.speaking(),
                        e.infoUrl()
                ));
            }
        });
    }

    public List<PlannedGatheringView> views() {
        return viewsById.values().stream()
                .sorted(Comparator.comparing(PlannedGatheringView::date))
                .toList();
    }
}
