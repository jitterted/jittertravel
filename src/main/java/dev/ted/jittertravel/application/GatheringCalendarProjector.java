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

public class GatheringCalendarProjector implements EventStreamConsumer {

    private final Map<GatheringId, CalendarEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            if (storedEvent.payload() instanceof GatheringPlanned event) {
                String infoUrl = event.infoUrl().isBlank() ? null : event.infoUrl();
                entries.put(event.gatheringId(), new CalendarEntry(
                        EntryKind.GATHERING,
                        event.date().atTime(event.startTime()),
                        event.date().atTime(event.endTime()),
                        event.title(),
                        buildSubTitle(event),
                        null,
                        null,
                        infoUrl
                ));
            }
        });
    }

    private static List<String> buildSubTitle(GatheringPlanned event) {
        String cityCountry = event.location().city()
                + (event.location().country().isBlank() ? "" : ", " + event.location().country());
        return event.venueName().isBlank()
                ? List.of(cityCountry)
                : List.of(event.venueName(), cityCountry);
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
