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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TentativeConferenceProjectorTest {

    @Test
    void projectorCreatesViewFromEvents() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        ConferenceId conferenceId = ConferenceId.random();
        Address address = new Address("123 Venue Street", "Venue City", "Venue State", "Venue Country", "Venue Postal Code");
        ConferenceTentativelyPlanned event = new ConferenceTentativelyPlanned(
                conferenceId,
                "Conference Name",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 3, 17, 0),
                "Venue",
                address
        );
        StoredEvent storedEvent = new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event);

        projector.handle(Stream.of(storedEvent));

        List<TentativeConferenceView> views = projector.views();
        assertThat(views)
                .hasSize(1);
        TentativeConferenceView view = views.getFirst();
        assertThat(view.conferenceId())
                .isEqualTo(conferenceId);
        assertThat(view.name())
                .isEqualTo("Conference Name");
        assertThat(view.city())
                .isEqualTo("Venue City");
    }

    @Test
    void projectedViewsAreSortedAscendingByStartDate() {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        Address address = new Address("Street", "City", "State", "Country", "Postal Code");

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
                new StoredEvent(1, laterEvent.getClass(), UUID.randomUUID(), Instant.now(), laterEvent),
                new StoredEvent(2, earlierEvent.getClass(), UUID.randomUUID(), Instant.now(), earlierEvent)
        ));

        assertThat(projector.views())
                .hasSize(2)
                .extracting(TentativeConferenceView::name)
                .containsExactly("Earlier Conference", "Later Conference");
    }
}
