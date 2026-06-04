package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
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
                LocalDateTime.of(2026, 6, 7, 11, 0),
                LocalDateTime.of(2026, 6, 10, 17, 0),
                "Forum",
                new Address("Street", "Frankfurt", "Hesse", "60311", "Germany", null)
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.CONFERENCE);
        assertThat(entry.mainTitle()).isEqualTo("DDD Europe 2026");
        assertThat(entry.subTitle()).isEqualTo("(Frankfurt, Germany)");
        assertThat(entry.continuationTitle()).isEqualTo("DDD Europe 2026 cont'd");
        assertThat(entry.continuationSubTitle()).isEqualTo("(Frankfurt, Germany)");
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

    private static ConferenceTentativelyPlanned sampleConference(String name, LocalDateTime start) {
        return new ConferenceTentativelyPlanned(
                ConferenceId.random(),
                name,
                start,
                start.plusDays(2),
                "Venue",
                new Address("Street", "City", "State", "00000", "Country", null)
        );
    }

    private static StoredEvent stored(ConferenceTentativelyPlanned event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
