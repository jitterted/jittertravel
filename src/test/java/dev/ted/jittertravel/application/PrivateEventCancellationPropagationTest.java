package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle guard: every read model must react to a cancelled private event, one case per
 * projector — the sibling of {@link GroundTransferCancellationPropagationTest}.
 * <p>
 * A projector that handles {@code PrivateEventPlanned} but forgets {@code PrivateEventCancelled}
 * keeps showing an evening that is not happening. Two of these cases are worse than a stale row:
 * on {@link PublicCalendarProjector} the leftover is a "Busy" block telling a stranger where Ted is
 * on a day he is not there, and on {@link ScheduleGapProjector} the event goes on asserting his
 * presence in that city — which is the reason cancel was built before the edit flow.
 */
class PrivateEventCancellationPropagationTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final Address VENUE = new Address("1 Frith St", "London", "", "W1D 4TL",
                                                     "GB", "London");

    private final PrivateEventId privateEventId = PrivateEventId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void theOwnersCalendarDropsTheCancelledEvent() {
        PrivateEventCalendarProjector projector = new PrivateEventCalendarProjector();

        projector.handle(planThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theAnonymousCalendarDropsTheCancelledEvent() {
        // The leftover would be a "Busy" block asserting Ted's whereabouts on a day he is not
        // there — a stale disclosure, not merely a stale row.
        PublicCalendarProjector projector = new PublicCalendarProjector();

        projector.handle(planThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theItineraryDropsTheCancelledEvent() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(planThenCancel());

        assertThat(projector.entriesForDate(DATE))
                .isEmpty();
    }

    @Test
    void theCancelPageDropsTheCancelledEvent() {
        PrivateEventDetailsViewProjector projector = new PrivateEventDetailsViewProjector();

        projector.handle(planThenCancel());

        assertThat(projector.findById(privateEventId))
                .as("the page that offers cancelling must not offer it a second time")
                .isEmpty();
    }

    @Test
    void thePlannedPrivateEventsListDropsTheCancelledEvent() {
        PlannedPrivateEventsProjector projector = new PlannedPrivateEventsProjector();

        projector.handle(planThenCancel());

        assertThat(projector.views(TimeView.ALL, Instant.EPOCH))
                .as("a list row is the one place a cancelled evening would still offer a Cancel link")
                .isEmpty();
    }

    @Test
    void scheduleProblemsStopReportingAClashWithTheCancelledEvent() {
        // A gathering and a private event at the same hour are the same impossibility as two
        // gatherings, so the pair is reported as a conflict. Cancelling the dinner must clear it,
        // proving the occupancy left the projector's state rather than merely leaving a calendar.
        ScheduleGapProjector withEvent = new ScheduleGapProjector(new StaticAirportCityResolver());
        withEvent.handle(Stream.of(stored(gathering()), stored(privateEventPlanned())));
        assertThat(withEvent.problems())
                .as("dinner and meetup at the same hour clash")
                .anyMatch(ScheduleProblem.SchedulingConflict.class::isInstance);

        ScheduleGapProjector afterCancelling = new ScheduleGapProjector(new StaticAirportCityResolver());
        afterCancelling.handle(Stream.of(stored(gathering()), stored(privateEventPlanned()),
                stored(cancelled())));

        assertThat(afterCancelling.problems())
                .as("with the dinner gone, there is nothing left to clash with")
                .noneMatch(ScheduleProblem.SchedulingConflict.class::isInstance);
    }

    @Test
    void theProblemContextForgetsTheCancelledEvent() {
        // The banner on a fix page reads this: a cancelled evening must not go on explaining why
        // some other problem exists.
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());

        projector.handle(planThenCancel());

        assertThat(projector.context())
                .noneMatch(ScheduleContext.PrivateEvent.class::isInstance);
    }

    private Stream<StoredEvent> planThenCancel() {
        return Stream.of(stored(privateEventPlanned()), stored(cancelled()));
    }

    private PrivateEventPlanned privateEventPlanned() {
        return new PrivateEventPlanned(privateEventId, "Dinner with the Smiths", "Chez Moi",
                VENUE, at(19, 0), at(22, 0));
    }

    private PrivateEventCancelled cancelled() {
        return new PrivateEventCancelled(privateEventId, "Rescheduled to Friday");
    }

    private static GatheringPlanned gathering() {
        return new GatheringPlanned(GatheringId.random(), "London Java Community", "Skills Matter",
                VENUE, at(19, 30), at(21, 30), false, "");
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(DATE, LocalTime.of(hour, minute)), LONDON);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
