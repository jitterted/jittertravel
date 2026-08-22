package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Projects the conference events into pre-formatted {@link CalendarEntry} views ready for the
 * calendar swimlane renderer.
 * <p>
 * Attendance commitment is folded here rather than in a separate {@code ConfirmedConferenceProjector},
 * since commitment is a property of one conference and not a second source of conferences: a
 * {@link ConferencePlanned} lands as {@link AttendanceCommitment#WATCHING}, and a
 * {@link ConferenceAttendanceConfirmed} rewrites that same entry as
 * {@link AttendanceCommitment#GOING}.
 * <p>
 * <strong>The collapse to a public label happens here.</strong> Only the commitment level reaches
 * the {@link CalendarEntry}; the event's {@link dev.ted.jittertravel.domain.AttendanceBasis} — why
 * Ted is going, which is submission status in disguise — is read and discarded. A field that never
 * enters the view cannot leak from it (CLAUDE.md redaction rule 1), so the redactor has nothing to
 * strip. See {@code docs/ConferenceSubmissionTrackingPlan.md}.
 */
public class ConferenceCalendarProjector implements EventStreamConsumer {
    private final Map<ConferenceId, CalendarEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                case ConferencePlanned event -> {
                    String location = event.venueAddress().city() + ", " + event.venueAddress().country();
                    List<SubtitleLine> locationLines = List.of(new SubtitleLine.Text(location));
                    // Calendar days are venue-local days (decision 7): bucket by the wall-clock
                    // the traveler will read off a clock at the venue, not by UTC.
                    entries.put(event.conferenceId(), new CalendarEntry(
                            event.startDate().localDateTime(),
                            event.endDate().localDateTime(),
                            event.name(),
                            locationLines,
                            event.name() + " cont'd",
                            locationLines,
                            // Planning a conference is putting it on the watch list, nothing more: it is
                            // speculative until an attendance confirmation says otherwise.
                            new EntryDetails.Conference(AttendanceCommitment.WATCHING)
                    ));
                }
                // Ted is going: same entry, no longer speculative. `event.basis()` is deliberately
                // not read — see the class comment.
                case ConferenceAttendanceConfirmed event ->
                        entries.computeIfPresent(event.conferenceId(),
                                (id, entry) -> going(entry));
                case ConferenceCancelled event -> entries.remove(event.conferenceId());
                case ConferenceAttendanceDeclined event -> entries.remove(event.conferenceId());
                default -> {}
            }
        });
    }

    private CalendarEntry going(CalendarEntry entry) {
        return new CalendarEntry(
                entry.start(), entry.end(),
                entry.mainTitle(), entry.subTitle(),
                entry.continuationTitle(), entry.continuationSubTitle(),
                new EntryDetails.Conference(AttendanceCommitment.GOING)
        );
    }

    public List<CalendarEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(CalendarEntry::start))
                .toList();
    }
}
