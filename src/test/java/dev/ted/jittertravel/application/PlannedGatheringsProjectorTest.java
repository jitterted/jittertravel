package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringChanged;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedGatheringsProjectorTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate DATE_JUN_20 = LocalDate.of(2026, 6, 20);
    private static final LocalDate DATE_JUN_15 = LocalDate.of(2026, 6, 15);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    // ALL ignores now; any instant works for those cases.
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

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
                london(DATE_JUN_20, START),
                london(DATE_JUN_20, END),
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
        assertThat(view.street()).isEqualTo("1 Example St");
        assertThat(view.city()).isEqualTo("London");
        assertThat(view.region()).isEqualTo("");
        assertThat(view.postalCode()).isEqualTo("EC1A 1BB");
        assertThat(view.country()).isEqualTo("GB");
        assertThat(view.startsAt()).isEqualTo(london(DATE_JUN_20, START));
        assertThat(view.endsAt()).isEqualTo(london(DATE_JUN_20, END));
        assertThat(view.speaking()).isTrue();
        assertThat(view.infoUrl()).isEqualTo("https://meetup.com/ljc/events/123");
    }

    @Test
    void multipleGatheringsAreSortedByStart() {
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
        Instant now = Instant.parse("2026-06-18T12:00:00Z");
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

    @Test
    void gatheringStillRunningInItsOwnZoneStaysUnderFutureEvenPastMidnightUtc() {
        // A Los Angeles meetup running 18:00–21:00 PDT ends at 04:00Z the next day. Two hours
        // earlier it is still going, though its wall-clock date is already "yesterday" in UTC —
        // the case that made the old server-zone comparison drop live entries off the list.
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        GatheringPlanned meetup = new GatheringPlanned(
                GatheringId.random(), "LA JUG", "Some Venue",
                new Address("1 Street", "Los Angeles", "CA", "90001", "USA", null),
                ZonedTimestamp.fromLocal(DATE_JUN_20.atTime(START), losAngeles),
                ZonedTimestamp.fromLocal(DATE_JUN_20.atTime(END), losAngeles),
                false, "");

        projector.handle(Stream.of(stored(meetup)));

        assertThat(projector.views(TimeView.FUTURE, Instant.parse("2026-06-21T02:00:00Z")))
                .as("still in progress in Los Angeles, so still upcoming")
                .extracting(PlannedGatheringView::title)
                .containsExactly("LA JUG");
        assertThat(projector.views(TimeView.FUTURE, Instant.parse("2026-06-21T05:00:00Z")))
                .as("an hour after it ended in Los Angeles, so no longer upcoming")
                .isEmpty();
    }

    @Test
    void gatheringChangedOverwritesThePlannedView() {
        PlannedGatheringsProjector projector = new PlannedGatheringsProjector();
        GatheringId gatheringId = GatheringId.random();
        GatheringPlanned planned = new GatheringPlanned(
                gatheringId, "Old Title", "Skills Matter",
                new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null),
                london(DATE_JUN_15, START), london(DATE_JUN_15, END), true, "https://old.example.com");
        GatheringChanged changed = new GatheringChanged(
                gatheringId, "New Title", "Federation House",
                new Address("2 New St", "Manchester", "Greater Manchester", "M1 1AA", "GB", null),
                london(DATE_JUN_20, LocalTime.of(17, 30)),
                london(DATE_JUN_20, LocalTime.of(20, 0)),
                false, "https://new.example.com");

        projector.handle(Stream.of(stored(planned), stored(changed)));

        List<PlannedGatheringView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(1);
        PlannedGatheringView view = views.getFirst();
        assertThat(view.gatheringId()).isEqualTo(gatheringId);
        assertThat(view.title()).isEqualTo("New Title");
        assertThat(view.venueName()).isEqualTo("Federation House");
        assertThat(view.street()).isEqualTo("2 New St");
        assertThat(view.city()).isEqualTo("Manchester");
        assertThat(view.region()).isEqualTo("Greater Manchester");
        assertThat(view.postalCode()).isEqualTo("M1 1AA");
        assertThat(view.startsAt()).isEqualTo(london(DATE_JUN_20, LocalTime.of(17, 30)));
        assertThat(view.endsAt()).isEqualTo(london(DATE_JUN_20, LocalTime.of(20, 0)));
        assertThat(view.speaking())
                .as("speaking flag should be overwritten by the change")
                .isFalse();
        assertThat(view.infoUrl()).isEqualTo("https://new.example.com");
    }

    private static ZonedTimestamp london(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), LONDON);
    }

    private static GatheringPlanned gathering(GatheringId id, String title, LocalDate date) {
        return new GatheringPlanned(
                id, title, "Some Venue",
                new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                london(date, START), london(date, END), false, ""
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
