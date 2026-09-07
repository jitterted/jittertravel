package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelTrainRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Covers the decision fold: the service reads the authoritative event stream (not a projector) to
 * decide whether a live trip exists, so a second cancel of the same trip is refused as not-found
 * rather than emitting a duplicate event.
 */
class CancelTrainTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final TrainStationAddress HAMBURG =
            new TrainStationAddress("Hamburg Hbf", "Hamburg", "Germany", "");
    private static final TrainStationAddress BERLIN_HBF =
            new TrainStationAddress("Berlin Hbf", "Berlin", "Germany", "");

    @Test
    void bookedTripCancelsAndEmitsTheCancelledEvent() {
        TrainTripId tripId = TrainTripId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(tripId))));
        CancelTrain service = new CancelTrain(executor);

        service.cancelTrain(UUID.randomUUID(),
                new CancelTrainRequest(tripId.id(), "Rebooked for the 17th"));

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new TrainCancelled(tripId, "Rebooked for the 17th"));
    }

    @Test
    void unknownTripIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of());
        CancelTrain service = new CancelTrain(executor);

        assertThatExceptionOfType(TrainNotFound.class)
                .isThrownBy(() -> service.cancelTrain(UUID.randomUUID(),
                        new CancelTrainRequest(UUID.randomUUID(), "")));
    }

    @Test
    void tripAlreadyCancelledIsRefused() {
        TrainTripId tripId = TrainTripId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(tripId)),
                storedEvent(2, new TrainCancelled(tripId, "First time"))));
        CancelTrain service = new CancelTrain(executor);

        assertThatExceptionOfType(TrainNotFound.class)
                .isThrownBy(() -> service.cancelTrain(UUID.randomUUID(),
                        new CancelTrainRequest(tripId.id(), "again")));
    }

    @Test
    void anotherTripsCancellationDoesNotClearThisOne() {
        // The fold keys on the id: cancelling one trip must not make a second one uncancellable.
        TrainTripId tripId = TrainTripId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(tripId)),
                storedEvent(2, new TrainCancelled(TrainTripId.random(), "someone else's"))));
        CancelTrain service = new CancelTrain(executor);

        service.cancelTrain(UUID.randomUUID(), new CancelTrainRequest(tripId.id(), ""));

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new TrainCancelled(tripId, ""));
    }

    @Test
    void aChangeCannotResurrectACancelledTrip() {
        // TrainChanged is deliberately not part of the fold. A change arriving after a cancel is
        // history out of order, not a rebooking, and reading it as one would let a stale form
        // silently undo a cancellation.
        TrainTripId tripId = TrainTripId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(tripId)),
                storedEvent(2, new TrainCancelled(tripId, "First time")),
                storedEvent(3, changed(tripId))));
        CancelTrain service = new CancelTrain(executor);

        assertThatExceptionOfType(TrainNotFound.class)
                .isThrownBy(() -> service.cancelTrain(UUID.randomUUID(),
                        new CancelTrainRequest(tripId.id(), "")));
    }

    @Test
    void aTripThatAlreadyDepartedIsStillCancellable() {
        // There is no time gate, unlike ChangeTrain, and this is what says so. A trip entered
        // wrongly is worth removing whenever it is found, and a past one is the entry most worth
        // removing: it is the leg still telling ScheduleGapProjector that Ted travelled between
        // two cities he did not. Nothing here supplies a `now`, which is the point.
        TrainTripId tripId = TrainTripId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, new TrainBooked(tripId, HAMBURG, longAgo(9), BERLIN_HBF,
                                               longAgo(11), "ICE 597"))));
        CancelTrain service = new CancelTrain(executor);

        service.cancelTrain(UUID.randomUUID(), new CancelTrainRequest(tripId.id(), "never ran"));

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new TrainCancelled(tripId, "never ran"));
    }

    private static TrainBooked booked(TrainTripId tripId) {
        return new TrainBooked(tripId, HAMBURG, at(9, 0), BERLIN_HBF, at(11, 0), "ICE 597");
    }

    private static ZonedTimestamp longAgo(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2019, 3, 4, hour, 0), BERLIN);
    }

    private static TrainChanged changed(TrainTripId tripId) {
        return new TrainChanged(tripId, HAMBURG, at(10, 0), BERLIN_HBF, at(12, 0), "ICE 599");
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, minute), BERLIN);
    }

    private static StoredEvent storedEvent(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }

    /**
     * Records what the service asked the executor to do, and mimics the real executor's contract
     * closely enough for this test: it runs the command against the folded context, so a not-found
     * decision propagates the domain exception exactly as production would.
     */
    private static final class RecordingCommandExecutor extends CommandExecutor {
        private final List<StoredEvent> events;
        private List<? extends Event> emittedEvents = new ArrayList<>();

        RecordingCommandExecutor(Stream<StoredEvent> events) {
            super(null, null);
            this.events = events.toList();
        }

        @Override
        public Stream<StoredEvent> eventsForDecision() {
            return events.stream();
        }

        @Override
        public <C extends DecisionContext> void execute(UUID commandId, Object request, C context,
                                                        DomainCommand<C> command) {
            this.emittedEvents = command.execute(context).toList();
        }
    }
}
