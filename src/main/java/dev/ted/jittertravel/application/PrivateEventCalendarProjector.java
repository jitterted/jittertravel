package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects {@link PrivateEventPlanned} into the owner's full {@link CalendarEntry}
 * (title, venue, city, time range). Anonymous viewers never see this form, and it is not reduced
 * for them either: {@link PublicCalendarProjector} builds "Busy" + time + city from the same event,
 * without ever holding the title or the venue. See docs/archived/PrivateSocialEventPlan.md.
 */
public class PrivateEventCalendarProjector implements EventStreamConsumer {

    private final Map<PrivateEventId, CalendarEntry> entries = new ConcurrentHashMap<>();
    private final EventCalendarSubtitle subtitle = new EventCalendarSubtitle();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case PrivateEventPlanned e -> entries.put(e.privateEventId(), toEntry(e));
                default -> { /* not a private-event event */ }
            }
        });
    }

    private CalendarEntry toEntry(PrivateEventPlanned e) {
        // Bucketed on the event-zone local day, like a gathering. Uses the shared owner-subtitle
        // builder ([venue?, city, Range]); the redactor derives the anonymous view from it.
        return new CalendarEntry(
                e.startsAt().localDateTime(),
                e.endsAt().localDateTime(),
                e.title(),
                subtitle.venueLocationAndTime(e.venueName(), e.location(), e.startsAt(), e.endsAt()),
                new EntryDetails.PrivateEvent()
        );
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
