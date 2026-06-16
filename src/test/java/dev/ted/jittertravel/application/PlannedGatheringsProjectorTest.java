package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedGatheringsProjectorTest {

    private static final LocalDate DATE_JUN_20 = LocalDate.of(2026, 6, 20);
    private static final LocalDate DATE_JUN_15 = LocalDate.of(2026, 6, 15);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Test
    void noEventsProducesEmptyList() {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();

        projector.handle(Stream.empty());

        assertThat(projector.views(TimeView.ALL, NOW)).isEmpty();
    }

    @Test
    void gatheringPlannedEventCreatesView() {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        GatheringId gatheringId = GatheringId.random();
        GatheringPlanned event = new GatheringPlanned(
                gatheringId,
                "London Java Community",
                "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                DATE_JUN_20,
                START, END,
                true,
                "https://meetup.com/ljc/events/123"
        );

        projector.handle(Stream.of(stored(event)));

        List<PlannedGatheringView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(1);
        PlannedGatheringView view = views.getFirst();
        assertThat(view.gatheringId()).isEqualTo(gatheringId);
        assertThat(view.title()).isEqualTo("London Java Community");
        assertThat(view.venueName()).isEqualTo("Skills Matter");
        assertThat(view.city()).isEqualTo("London");
        assertThat(view.country()).isEqualTo("GB");
        assertThat(view.date()).isEqualTo(DATE_JUN_20);
        assertThat(view.startTime()).isEqualTo(START);
        assertThat(view.endTime()).isEqualTo(END);
        assertThat(view.speaking()).isTrue();
        assertThat(view.infoUrl()).isEqualTo("https://meetup.com/ljc/events/123");
    }

    @Test
    void multipleGatheringsAreSortedByDate() {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        GatheringPlanned later = gathering(GatheringId.random(), "Later Meetup", DATE_JUN_20);
        GatheringPlanned earlier = gathering(GatheringId.random(), "Earlier Meetup", DATE_JUN_15);

        projector.handle(Stream.of(stored(later), stored(earlier)));

        List<PlannedGatheringView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(2);
        assertThat(views.get(0).title()).isEqualTo("Earlier Meetup");
        assertThat(views.get(1).title()).isEqualTo("Later Meetup");
    }

    @Test
    void futureFilterExcludesGatheringsThatEndedBeforeNow() {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 12, 0);
        GatheringPlanned past = gathering(GatheringId.random(), "Past Meetup", DATE_JUN_15);
        GatheringPlanned upcoming = gathering(GatheringId.random(), "Upcoming Meetup", DATE_JUN_20);

        projector.handle(Stream.of(stored(past), stored(upcoming)));

        assertThat(projector.views(TimeView.FUTURE, now))
                .extracting(PlannedGatheringView::title)
                .containsExactly("Upcoming Meetup");
        assertThat(projector.views(TimeView.ALL, now))
                .extracting(PlannedGatheringView::title)
                .containsExactly("Past Meetup", "Upcoming Meetup");
    }

    private static GatheringPlanned gathering(GatheringId id, String title, LocalDate date) {
        return new GatheringPlanned(
                id, title, "Some Venue",
                new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                date, START, END, false, ""
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
