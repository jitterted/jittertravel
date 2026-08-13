package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import dev.ted.jittertravel.domain.ZonedTimestamp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class GatheringCalendarProjector implements EventStreamConsumer {

    private final Map<GatheringId, CalendarEntry> entries = new ConcurrentHashMap<>();
    private final EventCalendarSubtitle subtitle = new EventCalendarSubtitle();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                // Both events are full snapshots, so a change overwrites the planned entry.
                case GatheringPlanned e -> entries.put(e.gatheringId(), toEntry(
                        e.title(), e.venueName(), e.location(),
                        e.startsAt(), e.endsAt(), e.infoUrl()));
                case GatheringChanged e -> entries.put(e.gatheringId(), toEntry(
                        e.title(), e.venueName(), e.location(),
                        e.startsAt(), e.endsAt(), e.infoUrl()));
                default -> { /* not a gathering event */ }
            }
        });
    }

    private CalendarEntry toEntry(String title,
                                  String venueName,
                                  Address location,
                                  ZonedTimestamp startsAt,
                                  ZonedTimestamp endsAt,
                                  String infoUrl) {
        // The calendar buckets by the day the gathering happens *at its venue*, so an evening
        // event never slides onto the neighbouring day for a viewer in another zone. The times
        // stay in the subtitle for every viewer — gatherings are public; the redactor's GATHERING
        // branch lets them through — unlike flights/trains/hotels, whose times it strips.
        return new CalendarEntry(
                EntryKind.GATHERING,
                startsAt.localDateTime(),
                endsAt.localDateTime(),
                title,
                subtitle.venueLocationAndTime(venueName, location, startsAt, endsAt),
                null,
                null,
                infoUrl.isBlank() ? null : infoUrl
        );
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
