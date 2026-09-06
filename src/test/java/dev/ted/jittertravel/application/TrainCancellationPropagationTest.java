package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
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
 * Lifecycle guard: every read model must react to a cancelled train, one case per projector — the
 * sibling of {@link PrivateEventCancellationPropagationTest} and
 * {@link GroundTransferCancellationPropagationTest}.
 * <p>
 * <strong>This is the only thing in the tree that catches a missed branch.</strong>
 * {@code LocatedEventsReachScheduleProblemsTest} scans for events carrying a location type, and
 * {@link TrainCancelled} carries only an id — so it is invisible there, and nothing else notices a
 * projector that handles {@link TrainBooked} and forgets this.
 * <p>
 * Two of the cases below are worse than a stale row. On {@link PublicCalendarProjector} the
 * leftover tells an anonymous viewer that Ted travels between two cities on a day he does not; on
 * {@link ScheduleGapProjector} the leg goes on asserting he made the journey, and its arrival
 * becomes the walk's last word on where he is — which is the reason cancel was built.
 * <p>
 * The last case is the odd one out and asserts the <em>opposite</em>: see
 * {@link #theZoneAuditStillReportsTheCancelledTripsStations()}.
 */
class TrainCancellationPropagationTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final TrainStationAddress HAMBURG =
            new TrainStationAddress("Hamburg Hbf", "Hamburg", "Germany", "");
    private static final TrainStationAddress BERLIN_HBF =
            new TrainStationAddress("Berlin Hbf", "Berlin", "Germany", "");

    private final TrainTripId tripId = TrainTripId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void theBookedTrainsListDropsTheCancelledTrip() {
        // Hard removal, not a tombstone: the entry most often cancelled is a duplicate, and a
        // greyed row would preserve it on the one screen it is in the way on.
        BookedTrainsProjector projector = new BookedTrainsProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.views(TimeView.ALL, Instant.EPOCH))
                .isEmpty();
    }

    @Test
    void theOwnersCalendarDropsTheCancelledTrip() {
        TrainCalendarProjector projector = new TrainCalendarProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theAnonymousCalendarDropsTheCancelledTrip() {
        // The leftover would be a stale disclosure, not merely a stale row: it asserts Ted's
        // whereabouts on a day he is not travelling.
        PublicCalendarProjector projector = new PublicCalendarProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theItineraryDropsTheCancelledTrip() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entriesForDate(DATE))
                .isEmpty();
    }

    @Test
    void theCancelPageDropsTheCancelledTrip() {
        // Which is also what makes a second cancel a not-found rather than a duplicate event, and
        // what retires the trip as a `train:` ground-transfer endpoint token.
        TrainDetailsViewProjector projector = new TrainDetailsViewProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.findById(tripId))
                .isEmpty();
    }

    @Test
    void theGroundTransferEndpointsDropBothEndsOfTheCancelledTrip() {
        // A list of places Ted can be picked up; a cancelled trip stops at none of them.
        TransferEndpointProjector projector = new TransferEndpointProjector(new StaticAirportCityResolver());

        projector.handle(bookThenCancel());

        assertThat(projector.rowsFor(TransferEnd.TRAIN_DEPARTURE))
                .isEmpty();
        assertThat(projector.rowsFor(TransferEnd.TRAIN_ARRIVAL))
                .isEmpty();
    }

    @Test
    void scheduleProblemsStopTreatingTheCancelledTripAsTravel() {
        // The leg is what makes the journey accounted for. With it gone the schedule holds a
        // Hamburg departure and a Berlin arrival that nothing carries him between, which is
        // exactly the gap the report exists to raise — so its disappearance proves the Movement
        // left the projector's state rather than merely leaving a calendar.
        ScheduleGapProjector withTrip = new ScheduleGapProjector(new StaticAirportCityResolver());
        withTrip.handle(Stream.of(stored(booked())));
        assertThat(withTrip.context())
                .as("the booked leg is part of the schedule")
                .anyMatch(ScheduleContext.Travel.class::isInstance);

        ScheduleGapProjector afterCancelling = new ScheduleGapProjector(new StaticAirportCityResolver());
        afterCancelling.handle(bookThenCancel());

        assertThat(afterCancelling.context())
                .as("a cancelled leg must not go on explaining another problem in the banner")
                .noneMatch(ScheduleContext.Travel.class::isInstance);
        assertThat(afterCancelling.awayDays())
                .as("nor place Ted anywhere: its arrival was the walk's last word on where he is")
                .isEmpty();
    }

    @Test
    void theZoneAuditStillReportsTheCancelledTripsStations() {
        // The one projector that must NOT react, and the case exists so nobody "fixes" it into
        // consistency with the seven above. TrainBooked stays in the log forever and the
        // read-time upcaster resolves its zone on every replay, so dropping the location here
        // would hide exactly the unresolvable location that breaks startup.
        LocationAuditProjector projector = new LocationAuditProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.cities())
                .extracting(audited -> audited.location().city())
                .contains("Hamburg", "Berlin");
    }

    private Stream<StoredEvent> bookThenCancel() {
        return Stream.of(stored(booked()), stored(cancelled()));
    }

    private TrainBooked booked() {
        return new TrainBooked(tripId, HAMBURG, at(9, 0), BERLIN_HBF, at(11, 0), "ICE 597");
    }

    private TrainCancelled cancelled() {
        return new TrainCancelled(tripId, "Rebooked for the 17th");
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(DATE, LocalTime.of(hour, minute)), BERLIN);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
