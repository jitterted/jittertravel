package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle guard: every read model must react to a cancelled flight, one case per projector — the
 * sibling of {@link TrainCancellationPropagationTest}, and for the same reason the only thing that
 * catches a missed branch: {@link FlightCancelled} carries only an id, so
 * {@code LocatedEventsReachScheduleProblemsTest} cannot see it.
 * <p>
 * The last case asserts the <em>opposite</em>: see
 * {@link #theZoneAuditStillReportsTheCancelledFlightsAirports()}.
 */
class FlightCancellationPropagationTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final AirportCode SFO = AirportCode.of("SFO");
    private static final AirportCode LAX = AirportCode.of("LAX");
    private static final Instant CANCELLED_ON = Instant.parse("2026-05-20T18:00:00Z");

    private final FlightId flightId = FlightId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void theBookedFlightsListHidesTheCancelledFlightByDefault() {
        BookedFlightsProjector projector = new BookedFlightsProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.views(TimeView.ALL, Instant.EPOCH))
                .isEmpty();
    }

    /**
     * The one read model that keeps a cancelled flight — as a record behind ?cancelled=show, for
     * looking up a credit or refund later. See {@link FlightCancelled}.
     */
    @Test
    void theBookedFlightsListKeepsTheCancelledFlightAsARecordWhenAskedToShowIt() {
        BookedFlightsProjector projector = new BookedFlightsProjector();

        projector.handle(bookThenCancel());

        List<BookedFlightView> shown = projector.views(TimeView.ALL, CancelledView.SHOW, Instant.EPOCH);
        assertThat(shown)
                .as("the cancelled flight is kept")
                .hasSize(1);
        BookedFlightView view = shown.getFirst();
        assertThat(view.flightId())
                .isEqualTo(flightId);
        assertThat(view.cancelled())
                .as("marked cancelled")
                .isTrue();
        assertThat(view.cancellationReason())
                .isEqualTo("Rebooked on UA58");
        assertThat(view.cancelledOn())
                .as("the payload's time, never the store's envelope (R11)")
                .isEqualTo(CANCELLED_ON);
        // A live flight beside it, so the count has to tell the two apart: with one flight a count
        // that forgot to filter would read 1 as well.
        projector.handle(Stream.of(stored(new FlightBooked(FlightId.random(), "United Airlines",
                "UA58", LAX, at(14, 0), SFO, at(15, 30)))));
        assertThat(projector.cancelledCount(TimeView.ALL, Instant.EPOCH))
                .as("counted even while hidden, so the switch can say how many it leaves out")
                .isEqualTo(1);
    }

    @Test
    void theOwnersCalendarDropsTheCancelledFlight() {
        FlightCalendarProjector projector = new FlightCalendarProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theAnonymousCalendarDropsTheCancelledFlight() {
        // A leftover is a stale disclosure: it tells a stranger Ted flies on a day he does not.
        PublicCalendarProjector projector = new PublicCalendarProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theItineraryDropsTheCancelledFlight() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entriesForDate(DATE))
                .isEmpty();
    }

    @Test
    void theCancelAndEditPagesDropTheCancelledFlight() {
        // Also what makes a second cancel a not-found, and a cancelled flight unchangeable:
        // ChangeFlight reads existence from this projector.
        FlightDetailsViewProjector projector = new FlightDetailsViewProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.findById(flightId))
                .isEmpty();
    }

    @Test
    void theGroundTransferEndpointsDropBothEndsOfTheCancelledFlight() {
        TransferEndpointProjector projector = new TransferEndpointProjector(new StaticAirportCityResolver());

        projector.handle(bookThenCancel());

        assertThat(projector.rowsFor(TransferEnd.FLIGHT_DEPARTURE))
                .isEmpty();
        assertThat(projector.rowsFor(TransferEnd.FLIGHT_ARRIVAL))
                .isEmpty();
    }

    @Test
    void scheduleProblemsStopTreatingTheCancelledFlightAsTravel() {
        ScheduleGapProjector withFlight = new ScheduleGapProjector(new StaticAirportCityResolver());
        withFlight.handle(Stream.of(stored(booked())));
        assertThat(withFlight.context())
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
    void cancellingOneOfTwoOverlappingFlightsClearsTheProblem() {
        FlightId duplicate = FlightId.random();
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
        projector.handle(Stream.of(stored(booked()),
                                   stored(new FlightBooked(duplicate, "United Airlines", "UA2093",
                                                           SFO, at(9, 30), LAX, at(11, 0)))));

        assertThat(projector.problems())
                .as("two flights carrying Ted at once is a problem cancel can now fix")
                .anyMatch(ScheduleProblem.OverlappingTravel.class::isInstance);

        projector.handle(Stream.of(stored(new FlightCancelled(duplicate, "entered twice", CANCELLED_ON))));

        assertThat(projector.problems())
                .as("with one leg gone the other is an ordinary journey, not a clash")
                .noneMatch(ScheduleProblem.OverlappingTravel.class::isInstance);
    }

    @Test
    void theZoneAuditStillReportsTheCancelledFlightsAirports() {
        // The one projector that must NOT react: FlightBooked stays in the log forever and
        // FlightTimeZoneUpcaster resolves its airports' zones on every replay, so dropping them
        // here would hide exactly the unresolvable airport that breaks startup.
        LocationAuditProjector projector = new LocationAuditProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.airports())
                .extracting(LocationAuditProjector.AuditedAirport::airport)
                .contains(SFO, LAX);
    }

    private Stream<StoredEvent> bookThenCancel() {
        return Stream.of(stored(booked()), stored(cancelled()));
    }

    private FlightBooked booked() {
        return new FlightBooked(flightId, "United Airlines", "UA2091", SFO, at(9, 0), LAX, at(10, 30));
    }

    private FlightCancelled cancelled() {
        return new FlightCancelled(flightId, "Rebooked on UA58", CANCELLED_ON);
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(DATE, LocalTime.of(hour, minute)), LOS_ANGELES);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
