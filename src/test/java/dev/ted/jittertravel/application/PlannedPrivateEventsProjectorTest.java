package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
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

class PlannedPrivateEventsProjectorTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate DATE_JUN_20 = LocalDate.of(2026, 6, 20);
    private static final LocalDate DATE_JUN_15 = LocalDate.of(2026, 6, 15);
    private static final LocalTime START = LocalTime.of(19, 0);
    private static final LocalTime END = LocalTime.of(22, 0);
    // ALL ignores now; any instant works for those cases.
    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

    @Test
    void noEventsProducesEmptyList() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();

        projector.handle(Stream.empty());

        assertThat(projector.views(TimeView.ALL, NOW)).isEmpty();
    }

    @Test
    void privateEventPlannedCreatesViewCarryingTheWholeAddress() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();
        PrivateEventId privateEventId = PrivateEventId.random();
        PrivateEventPlanned event = new PrivateEventPlanned(
                privateEventId,
                "Dinner with the Harrisons",
                "Barrafina",
                new Address("26 Dean St", "London", "Greater London", "W1D 3LL", "GB", null),
                london(DATE_JUN_20, START),
                london(DATE_JUN_20, END)
        );

        projector.handle(Stream.of(stored(event)));

        List<PlannedPrivateEventView> views = projector.views(TimeView.ALL, NOW);
        assertThat(views).hasSize(1);
        PlannedPrivateEventView view = views.getFirst();
        assertThat(view.privateEventId()).isEqualTo(privateEventId);
        assertThat(view.title()).isEqualTo("Dinner with the Harrisons");
        assertThat(view.venueName()).isEqualTo("Barrafina");
        // The three components no other read model carries — the reason this list exists.
        assertThat(view.street()).isEqualTo("26 Dean St");
        assertThat(view.region()).isEqualTo("Greater London");
        assertThat(view.postalCode()).isEqualTo("W1D 3LL");
        assertThat(view.city()).isEqualTo("London");
        assertThat(view.country()).isEqualTo("GB");
        assertThat(view.startsAt()).isEqualTo(london(DATE_JUN_20, START));
        assertThat(view.endsAt()).isEqualTo(london(DATE_JUN_20, END));
    }

    @Test
    void cancelledPrivateEventLeavesTheListEntirely() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();
        PrivateEventId privateEventId = PrivateEventId.random();

        projector.handle(Stream.of(
                stored(privateEvent(privateEventId, "Dinner with the Harrisons", DATE_JUN_20)),
                stored(new PrivateEventCancelled(privateEventId, "Rescheduled to Friday"))));

        assertThat(projector.views(TimeView.ALL, NOW))
                .as("cancellation is a hard removal, not a greyed-out row")
                .isEmpty();
    }

    @Test
    void multiplePrivateEventsAreSortedByStart() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();
        PrivateEventPlanned later = privateEvent(PrivateEventId.random(), "Later Dinner", DATE_JUN_20);
        PrivateEventPlanned earlier = privateEvent(PrivateEventId.random(), "Earlier Dinner", DATE_JUN_15);

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.views(TimeView.ALL, NOW))
                .extracting(PlannedPrivateEventView::title)
                .containsExactly("Earlier Dinner", "Later Dinner");
    }

    @Test
    void futureFilterExcludesPrivateEventsThatEndedBeforeNow() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();
        Instant now = Instant.parse("2026-06-18T12:00:00Z");
        PrivateEventPlanned past = privateEvent(PrivateEventId.random(), "Past Dinner", DATE_JUN_15);
        PrivateEventPlanned upcoming = privateEvent(PrivateEventId.random(), "Upcoming Dinner", DATE_JUN_20);

        projector.handle(Stream.of(stored(past), stored(upcoming)));

        assertThat(projector.views(TimeView.FUTURE, now))
                .extracting(PlannedPrivateEventView::title)
                .containsExactly("Upcoming Dinner");
        assertThat(projector.views(TimeView.ALL, now))
                .extracting(PlannedPrivateEventView::title)
                .containsExactly("Past Dinner", "Upcoming Dinner");
    }

    @Test
    void dinnerStillRunningInItsOwnZoneStaysUnderFutureEvenPastMidnightUtc() {
        // A Los Angeles dinner running 19:00–22:00 PDT ends at 05:00Z the next day. Two hours
        // earlier it is still going, though its wall-clock date is already "yesterday" in UTC.
        // relevantUntil() is the END instant, which is what makes this right.
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        PrivateEventPlanned dinner = new PrivateEventPlanned(
                PrivateEventId.random(), "Dinner with Susan", "Some Restaurant",
                new Address("1 Street", "Los Angeles", "CA", "90001", "USA", null),
                ZonedTimestamp.fromLocal(DATE_JUN_20.atTime(START), losAngeles),
                ZonedTimestamp.fromLocal(DATE_JUN_20.atTime(END), losAngeles));

        projector.handle(Stream.of(stored(dinner)));

        assertThat(projector.views(TimeView.FUTURE, Instant.parse("2026-06-21T03:00:00Z")))
                .as("still in progress in Los Angeles, so still upcoming")
                .extracting(PlannedPrivateEventView::title)
                .containsExactly("Dinner with Susan");
        assertThat(projector.views(TimeView.FUTURE, Instant.parse("2026-06-21T06:00:00Z")))
                .as("an hour after it ended in Los Angeles, so no longer upcoming")
                .isEmpty();
    }

    private static ZonedTimestamp london(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), LONDON);
    }

    private static PrivateEventPlanned privateEvent(PrivateEventId id, String title, LocalDate date) {
        return new PrivateEventPlanned(
                id, title, "Some Restaurant",
                new Address("1 Street", "London", "", "EC1A 1BB", "GB", null),
                london(date, START), london(date, END)
        );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}
