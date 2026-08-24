package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle guard: every read model must react to a cancelled ground transfer, one case per
 * projector — the sibling of {@link HotelCancellationPropagationTest}.
 * <p>
 * A projector that handles {@code GroundTransferPlanned} but forgets {@code GroundTransferCancelled}
 * keeps showing a hop that is not happening. On {@link ScheduleGapProjector} that is worse than a
 * stale row: the transfer goes on asserting a movement, which <em>masks</em> the missing-travel gap
 * it was entered to close — the exact failure the cancel flow exists to make fixable.
 */
class GroundTransferCancellationPropagationTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final Address AIRPORT = new Address("", "Denver", "CO", "", "US", "Denver");
    private static final Address HOTEL = new Address("10345 Park Meadows Dr", "Lone Tree", "CO",
                                                     "80124", "US", "Lone Tree");
    private static final ZonedTimestamp DEPARTS = at(2026, 9, 14, 12, 0);
    private static final ZonedTimestamp ARRIVES = at(2026, 9, 14, 12, 45);

    private final GroundTransferId transferId = GroundTransferId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void theCalendarDropsTheCancelledTransfer() {
        GroundTransferCalendarProjector projector = new GroundTransferCalendarProjector();

        projector.handle(planThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void theItineraryDropsTheCancelledTransfer() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(planThenCancel());

        assertThat(projector.entriesForDate(LocalDate.of(2026, 9, 14)))
                .isEmpty();
    }

    @Test
    void theCancelPageDropsTheCancelledTransfer() {
        GroundTransferDetailsViewProjector projector = new GroundTransferDetailsViewProjector();

        projector.handle(planThenCancel());

        assertThat(projector.findById(transferId))
                .as("the page that offers cancelling must not offer it a second time")
                .isEmpty();
    }

    @Test
    void scheduleProblemsReportTheGapTheCancelledTransferUsedToClose() {
        // A flight lands at DEN, a conference sits in Lone Tree: without the taxi between them
        // there is a missing-travel gap. With the transfer recorded the gap closes; cancelling it
        // must bring the gap back, proving the movement left the projector's state rather than
        // merely being hidden from the calendar.
        ScheduleGapProjector withTransfer = new ScheduleGapProjector(new StaticAirportCityResolver());
        withTransfer.handle(Stream.of(stored(conference()), stored(flightToDenver()),
                stored(transferPlanned())));
        assertThat(missingTravel(withTransfer))
                .as("the transfer closes the airport-to-venue hop")
                .isEmpty();

        ScheduleGapProjector afterCancelling = new ScheduleGapProjector(new StaticAirportCityResolver());
        afterCancelling.handle(Stream.of(stored(conference()), stored(flightToDenver()),
                stored(transferPlanned()), stored(new GroundTransferCancelled(transferId))));

        assertThat(missingTravel(afterCancelling))
                .as("with the transfer gone, the hop is unbridged again")
                .singleElement()
                .extracting(ScheduleProblem.MissingTravel::fromCity, ScheduleProblem.MissingTravel::toCity)
                .containsExactly("Denver", "Lone Tree");
    }

    /** The conference also wants a bed those nights; this scenario is about the hop, not the bed. */
    private static List<ScheduleProblem.MissingTravel> missingTravel(ScheduleGapProjector projector) {
        return projector.problems().stream()
                .filter(ScheduleProblem.MissingTravel.class::isInstance)
                .map(ScheduleProblem.MissingTravel.class::cast)
                .toList();
    }

    private Stream<StoredEvent> planThenCancel() {
        return Stream.of(
                stored(transferPlanned()),
                stored(new GroundTransferCancelled(transferId)));
    }

    private GroundTransferPlanned transferPlanned() {
        return new GroundTransferPlanned(transferId,
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                DEPARTS, ARRIVES, "");
    }

    private static FlightBooked flightToDenver() {
        return new FlightBooked(FlightId.random(), "United", "UA 59",
                AirportCode.of("SFO"), at(2026, 9, 14, 9, 0),
                AirportCode.of("DEN"), at(2026, 9, 14, 11, 30));
    }

    private static ConferencePlanned conference() {
        return new ConferencePlanned(ConferenceId.random(), "dev2next",
                at(2026, 9, 14, 14, 0), at(2026, 9, 18, 17, 0),
                "Lone Tree Center", HOTEL);
    }

    private static ZonedTimestamp at(int year, int month, int day, int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(year, month, day, hour, minute), DENVER);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}
