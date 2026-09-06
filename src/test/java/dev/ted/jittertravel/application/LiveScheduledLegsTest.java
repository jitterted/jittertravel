package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.ScheduledLegs;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared fold behind all four write paths that refuse an overlapping journey. It reads the
 * authoritative event stream (R1), keys by leg so a change replaces rather than doubles, and
 * applies cancellations.
 */
class LiveScheduledLegsTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final TrainStationAddress HAMBURG =
            new TrainStationAddress("Hamburg Hbf", "Hamburg", "Germany", "");
    private static final TrainStationAddress BERLIN_HBF =
            new TrainStationAddress("Berlin Hbf", "Berlin", "Germany", "");

    @Test
    void foldsBookedFlightsAndTrainsTogether() {
        FlightId flightId = FlightId.random();
        TrainTripId tripId = TrainTripId.random();

        assertThat(fold(flightBooked(flightId, 9, 11), trainBooked(tripId, 14, 16)).legs())
                .extracting(ScheduledLeg::id)
                .containsExactly(new ScheduledLegId.Flight(flightId), new ScheduledLegId.Train(tripId));
    }

    @Test
    void aChangeReplacesTheLegItRestatesRatherThanAddingASecond() {
        // Two entries for one trip would make it collide with itself on the next edit — the
        // failure that would make every booked trip permanently uncorrectable.
        TrainTripId tripId = TrainTripId.random();

        assertThat(fold(trainBooked(tripId, 9, 11), trainChanged(tripId, 14, 16)).legs())
                .singleElement()
                .extracting(ScheduledLeg::departure)
                .isEqualTo(at(14));
    }

    @Test
    void aCancelledTrainNoLongerBlocksAnything() {
        // How Ted resolves a collision. A fold that ignored the cancellation would leave him
        // unable to re-enter the trip he had just corrected.
        TrainTripId tripId = TrainTripId.random();

        assertThat(fold(trainBooked(tripId, 9, 11), new TrainCancelled(tripId, "wrong entry")).legs())
                .isEmpty();
    }

    @Test
    void aCancellationOfAnotherTripLeavesThisOneBooked() {
        TrainTripId kept = TrainTripId.random();

        assertThat(fold(trainBooked(kept, 9, 11),
                new TrainCancelled(TrainTripId.random(), "someone else's")).legs())
                .singleElement()
                .extracting(ScheduledLeg::id)
                .isEqualTo(new ScheduledLegId.Train(kept));
    }

    @Test
    void aChangedFlightKeepsItsIdentity() {
        FlightId flightId = FlightId.random();

        assertThat(fold(flightBooked(flightId, 9, 11), flightChanged(flightId, 14, 16)).legs())
                .singleElement()
                .extracting(ScheduledLeg::arrival)
                .isEqualTo(at(16));
    }

    @Test
    void eventsThatAreNotScheduledLegsAreIgnored() {
        assertThat(fold(new TrainCancelled(TrainTripId.random(), "never booked")).legs())
                .isEmpty();
    }

    private static ScheduledLegs fold(Event... events) {
        return new LiveScheduledLegs(new StubExecutor(events)).fold();
    }

    private static FlightBooked flightBooked(FlightId id, int dep, int arr) {
        return new FlightBooked(id, "Lufthansa", "LH402",
                AirportCode.of("HAM"), at(dep), AirportCode.of("BER"), at(arr));
    }

    private static FlightChanged flightChanged(FlightId id, int dep, int arr) {
        return new FlightChanged(id, "Lufthansa", "LH402",
                AirportCode.of("HAM"), at(dep), AirportCode.of("BER"), at(arr), "");
    }

    private static TrainBooked trainBooked(TrainTripId id, int dep, int arr) {
        return new TrainBooked(id, HAMBURG, at(dep), BERLIN_HBF, at(arr), "ICE 597");
    }

    private static TrainChanged trainChanged(TrainTripId id, int dep, int arr) {
        return new TrainChanged(id, HAMBURG, at(dep), BERLIN_HBF, at(arr), "ICE 597");
    }

    private static ZonedTimestamp at(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, 0), BERLIN);
    }

    /** Serves a fixed stream as the authoritative event log, and records nothing else. */
    private static final class StubExecutor extends CommandExecutor {
        private final List<StoredEvent> events;

        StubExecutor(Event... events) {
            super(null, null);
            this.events = Stream.of(events)
                    .map(e -> new StoredEvent(1, e.getClass(), UUID.randomUUID(),
                            Instant.parse("2026-01-01T00:00:00Z"), e, UUID.randomUUID()))
                    .toList();
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return events.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
