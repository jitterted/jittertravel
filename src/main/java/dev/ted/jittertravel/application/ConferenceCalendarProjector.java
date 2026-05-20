package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects {@link ConferenceTentativelyPlanned} events into pre-formatted
 * {@link CalendarEntry} views ready for the calendar swimlane renderer.
 * <p>
 * For now, the calendar treats tentative conferences as the only source of
 * conference entries. A future {@code ConfirmedConferenceProjector} will replace
 * (or supplement) this once the confirmation slice exists.
 */
public class ConferenceCalendarProjector implements EventStreamConsumer {
    private final Map<ConferenceId, CalendarEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            if (storedEvent.payload() instanceof ConferenceTentativelyPlanned event) {
                String location = "(" + event.venueAddress().city() + ", " + event.venueAddress().country() + ")";
                entries.put(event.conferenceId(), new CalendarEntry(
                        EntryKind.CONFERENCE,
                        event.startDate(),
                        event.endDate(),
                        event.name(),
                        location,
                        event.name() + " cont'd",
                        location
                ));
            }
        });
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
