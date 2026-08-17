package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
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
 * conference entries, and every conference renders identically whether Ted is
 * committed to it or merely holding the slot.
 * <p>
 * A planned change makes this projector fold attendance-commitment events as well and
 * stamp a commitment level onto each {@link CalendarEntry} — not a separate
 * {@code ConfirmedConferenceProjector}, since commitment is a property of one
 * conference rather than a second source of conferences. Commitment is public;
 * submission/speaking status is not. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
 */
public class ConferenceCalendarProjector implements EventStreamConsumer {
    private final Map<ConferenceId, CalendarEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferenceTentativelyPlanned event -> {
                    String location = event.venueAddress().city() + ", " + event.venueAddress().country();
                    List<SubtitleLine> locationLines = List.of(new SubtitleLine.Text(location));
                    // Calendar days are venue-local days (decision 7): bucket by the wall-clock
                    // the traveler will read off a clock at the venue, not by UTC.
                    entries.put(event.conferenceId(), new CalendarEntry(
                            EntryKind.CONFERENCE,
                            event.startDate().localDateTime(),
                            event.endDate().localDateTime(),
                            event.name(),
                            locationLines,
                            event.name() + " cont'd",
                            locationLines,
                            null
                    ));
                }
                case ConferenceCancelled event -> entries.remove(event.conferenceId());
                case ConferenceAttendanceDeclined event -> entries.remove(event.conferenceId());
                default -> {}
            }
        });
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
