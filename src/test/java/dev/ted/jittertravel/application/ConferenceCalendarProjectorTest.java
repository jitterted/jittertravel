package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConferenceCalendarProjectorTest {

    @Test
    void buildsCalendarEntryFromConferenceTentativelyPlanned() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                conferenceId,
                "DDD Europe 2026",
                zt(LocalDateTime.of(2026, 6, 7, 11, 0)),
                zt(LocalDateTime.of(2026, 6, 10, 17, 0)),
                "Forum",
                new Address("Street", "Frankfurt", "Hesse", "60311", "Germany", null)
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.CONFERENCE);
        assertThat(entry.mainTitle()).isEqualTo("DDD Europe 2026");
        assertThat(entry.subTitle()).isEqualTo(List.of(new SubtitleLine.Text("Frankfurt, Germany")));
        assertThat(entry.continuationTitle()).isEqualTo("DDD Europe 2026 cont'd");
        assertThat(entry.continuationSubTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("Frankfurt, Germany")));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 6, 7, 11, 0));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 6, 10, 17, 0));
    }

    @Test
    void replayingTheSameEventIsIdempotent() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceTentativelyPlanned event = sampleConference("Conf", LocalDateTime.of(2026, 7, 1, 9, 0));

        projector.handle(Stream.of(stored(event)));
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
    }

    @Test
    void entriesAreSortedByStart() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceTentativelyPlanned later = sampleConference("Later", LocalDateTime.of(2026, 8, 1, 9, 0));
        ConferenceTentativelyPlanned earlier = sampleConference("Earlier", LocalDateTime.of(2026, 7, 1, 9, 0));

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::mainTitle)
                .containsExactly("Earlier", "Later");
    }

    @Test
    void decliningAttendanceRemovesTheConferenceFromTheCalendar() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferenceTentativelyPlanned planned = sampleConference("Devoxx Morocco", LocalDateTime.of(2026, 10, 7, 9, 0));
        // rebind planned to a known id so the decline targets it
        ConferenceTentativelyPlanned withId = new ConferenceTentativelyPlanned(
                conferenceId, planned.name(), planned.startDate(), planned.endDate(),
                planned.venueName(), planned.venueAddress());

        projector.handle(Stream.of(stored(withId)));
        assertThat(projector.entries()).hasSize(1);

        projector.handle(Stream.of(storedEvent(2,
                new ConferenceAttendanceDeclined(conferenceId, "Schedule clash",
                        Instant.parse("2026-08-16T18:30:00Z")))));

        assertThat(projector.entries())
                .as("a declined conference leaves the calendar, like a cancelled one")
                .isEmpty();
    }

    private static ConferenceTentativelyPlanned sampleConference(String name, LocalDateTime start) {
        return new ConferenceTentativelyPlanned(
                ConferenceId.random(),
                name,
                zt(start),
                zt(start.plusDays(2)),
                "Venue",
                new Address("Street", "City", "State", "00000", "Country", null)
        );
    }

    /** Calendar days are venue-local; the venue here is in Frankfurt, not the UTC test JVM. */
    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZoneId.of("Europe/Berlin"));
    }

    private static StoredEvent stored(ConferenceTentativelyPlanned event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }

    private static StoredEvent storedEvent(long sequence, Event event) {
        return new StoredEvent(sequence, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
