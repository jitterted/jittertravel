package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.application.TentativeConferenceView;
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

class TentativeConferenceProjectorTest {

    @Test
    void projectorCreatesViewFromEvents() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        Address address = new Address("123 Venue Street", "Venue City", "Venue State", "Venue Postal Code", "Venue Country", null);
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                conferenceId,
                "Conference Name",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 3, 17, 0),
                "Venue",
                address
        );
        StoredEvent storedEvent = new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());

        projector.handle(Stream.of(storedEvent));

        assertThat(projector.views())
                .hasSize(1);
        assertThat(projector.views().getFirst().conferenceId())
                .isEqualTo(conferenceId);
        assertThat(projector.views().getFirst().name())
                .isEqualTo("Conference Name");
        assertThat(projector.views().getFirst().city())
                .isEqualTo("Venue City");
    }

    @Test
    void projectedViewsAreSortedAscendingByStartDate() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal Code", "Country", null);

        ConferenceTentativelyPlanned laterEvent = new ConferenceTentativelyPlanned(
                ConferenceId.random(),
                "Later Conference",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 3, 17, 0),
                "Later Venue",
                address
        );
        ConferenceTentativelyPlanned earlierEvent = new ConferenceTentativelyPlanned(
                ConferenceId.random(),
                "Earlier Conference",
                LocalDateTime.of(2026, 6, 28, 9, 0),
                LocalDateTime.of(2026, 6, 30, 17, 0),
                "Earlier Venue",
                address
        );

        projector.handle(Stream.of(
                new StoredEvent(1, laterEvent.getClass(), UUID.randomUUID(), Instant.now(), laterEvent, UUID.randomUUID()),
                new StoredEvent(2, earlierEvent.getClass(), UUID.randomUUID(), Instant.now(), earlierEvent, UUID.randomUUID())
        ));

        assertThat(projector.views())
                .hasSize(2)
                .extracting(TentativeConferenceView::name)
                .containsExactly("Earlier Conference", "Later Conference");
    }

    @Test
    void migratableViewsExcludesMultiDayConferences() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "Single-day", LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 17, 0), address);
        handle(projector, 2, "Multi-day",  LocalDateTime.of(2026, 6, 2, 9, 0), LocalDateTime.of(2026, 6, 4, 17, 0), address);

        assertThat(projector.migratableViews())
                .hasSize(1)
                .extracting(TentativeConferenceView::name)
                .containsExactly("Single-day");
    }

    @Test
    void migratableViewsIncludesSingleDayConferences() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "All Day", LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 6, 1, 23, 59), address);

        assertThat(projector.migratableViews())
                .hasSize(1)
                .extracting(TentativeConferenceView::name)
                .containsExactly("All Day");
    }

    @Test
    void migratableViewsAreSortedAscendingByStartDate() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        Address address = new Address("Street", "City", "State", "Postal", "Country", null);

        handle(projector, 1, "Later Single-day",   LocalDateTime.of(2026, 7, 5, 9, 0), LocalDateTime.of(2026, 7, 5, 17, 0), address);
        handle(projector, 2, "Multi-day (skipped)", LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 3, 17, 0), address);
        handle(projector, 3, "Earlier Single-day",  LocalDateTime.of(2026, 6, 10, 9, 0), LocalDateTime.of(2026, 6, 10, 17, 0), address);

        assertThat(projector.migratableViews())
                .extracting(TentativeConferenceView::name)
                .containsExactly("Earlier Single-day", "Later Single-day");
    }

    private static void handle(TentativeConferenceProjector projector, long seq, String name,
                                LocalDateTime start, LocalDateTime end, Address address) {
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                ConferenceId.random(), name, start, end, "Venue", address);
        projector.handle(Stream.of(
                new StoredEvent(seq, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID())));
    }
}
