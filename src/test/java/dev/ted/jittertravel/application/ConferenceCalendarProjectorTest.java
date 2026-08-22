package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
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

    private static final Instant CONFIRMED_ON = Instant.parse("2026-08-19T16:45:00Z");

    @Test
    void buildsCalendarEntryFromConferencePlanned() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferencePlanned event = new ConferencePlanned(
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
        ConferencePlanned event = sampleConference("Conf", LocalDateTime.of(2026, 7, 1, 9, 0));

        projector.handle(Stream.of(stored(event)));
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
    }

    @Test
    void entriesAreSortedByStart() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferencePlanned later = sampleConference("Later", LocalDateTime.of(2026, 8, 1, 9, 0));
        ConferencePlanned earlier = sampleConference("Earlier", LocalDateTime.of(2026, 7, 1, 9, 0));

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::mainTitle)
                .containsExactly("Earlier", "Later");
    }

    @Test
    void decliningAttendanceRemovesTheConferenceFromTheCalendar() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        ConferencePlanned planned = sampleConference("Devoxx Morocco", LocalDateTime.of(2026, 10, 7, 9, 0));
        // rebind planned to a known id so the decline targets it
        ConferencePlanned withId = new ConferencePlanned(
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

    @Test
    void aPlannedConferenceStartsOutMerelyWatched() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();

        projector.handle(Stream.of(stored(sampleConference("J-Fall", LocalDateTime.of(2026, 11, 5, 9, 0)))));

        assertThat(projector.entries().getFirst().details())
                .as("planning a conference puts it on the radar, nothing more")
                .isEqualTo(new EntryDetails.Conference(AttendanceCommitment.WATCHING));
    }

    @Test
    void confirmingAttendanceTurnsTheEntryIntoGoing() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(withId(conferenceId,
                sampleConference("dev2next", LocalDateTime.of(2026, 9, 28, 9, 0))))));

        projector.handle(Stream.of(storedEvent(2, new ConferenceAttendanceConfirmed(
                conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON))));

        assertThat(projector.entries())
                .singleElement()
                .extracting(CalendarEntry::details)
                .isEqualTo(new EntryDetails.Conference(AttendanceCommitment.GOING));
    }

    @Test
    void confirmingAttendanceLeavesTheRestOfTheEntryAlone() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(withId(conferenceId,
                sampleConference("dev2next", LocalDateTime.of(2026, 9, 28, 9, 0))))));
        CalendarEntry before = projector.entries().getFirst();

        projector.handle(Stream.of(storedEvent(2, new ConferenceAttendanceConfirmed(
                conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON))));

        assertThat(projector.entries().getFirst())
                .isEqualTo(new CalendarEntry(
                        before.start(), before.end(),
                        before.mainTitle(), before.subTitle(),
                        before.continuationTitle(), before.continuationSubTitle(),
                        new EntryDetails.Conference(AttendanceCommitment.GOING)));
    }

    @Test
    void theBasisForGoingNeverReachesTheCalendarEntry() {
        // AttendanceBasis is submission status wearing a different hat: the projector reads it and
        // discards it, so redaction rule 1 is satisfied structurally rather than by the redactor.
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(withId(conferenceId,
                sampleConference("dev2next", LocalDateTime.of(2026, 9, 28, 9, 0))))));

        projector.handle(Stream.of(storedEvent(2, new ConferenceAttendanceConfirmed(
                conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON))));

        assertThat(projector.entries().getFirst().toString())
                .as("no field of the entry may carry the basis")
                .doesNotContain("SPEAKING_ACCEPTED");
    }

    @Test
    void confirmingAttendanceForAnUnknownConferenceAddsNothing() {
        // A confirmation can only ever follow a ConferencePlanned in the stream; if the conference
        // has since been declined or cancelled, this must not resurrect it.
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();

        projector.handle(Stream.of(storedEvent(1, new ConferenceAttendanceConfirmed(
                ConferenceId.random(), AttendanceBasis.TICKET_PURCHASED, CONFIRMED_ON))));

        assertThat(projector.entries()).isEmpty();
    }

    @Test
    void aConfirmedConferenceCanStillBeDeclined() {
        // Last decision wins: changing your mind after committing removes it from the calendar.
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(withId(conferenceId,
                sampleConference("dev2next", LocalDateTime.of(2026, 9, 28, 9, 0))))));
        projector.handle(Stream.of(storedEvent(2, new ConferenceAttendanceConfirmed(
                conferenceId, AttendanceBasis.SPEAKING_ACCEPTED, CONFIRMED_ON))));

        projector.handle(Stream.of(storedEvent(3, new ConferenceAttendanceDeclined(
                conferenceId, "Something came up", CONFIRMED_ON))));

        assertThat(projector.entries()).isEmpty();
    }

    private static ConferencePlanned withId(ConferenceId conferenceId, ConferencePlanned planned) {
        return new ConferencePlanned(
                conferenceId, planned.name(), planned.startDate(), planned.endDate(),
                planned.venueName(), planned.venueAddress());
    }

    private static ConferencePlanned sampleConference(String name, LocalDateTime start) {
        return new ConferencePlanned(
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

    private static StoredEvent stored(ConferencePlanned event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }

    private static StoredEvent storedEvent(long sequence, Event event) {
        return new StoredEvent(sequence, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
