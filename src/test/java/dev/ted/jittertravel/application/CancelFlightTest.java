package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelFlightRequest;
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
 * decide whether a live flight exists, so a second cancel of the same flight is refused as
 * not-found rather than emitting a duplicate event.
 */
class CancelFlightTest {

    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final Instant NOW = Instant.parse("2026-09-29T17:00:00Z");

    @Test
    void bookedFlightCancelsAndEmitsTheCancelledEventStampedWithNow() {
        FlightId flightId = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(flightId))));
        CancelFlight service = new CancelFlight(executor);

        service.cancelFlight(UUID.randomUUID(),
                new CancelFlightRequest(flightId.id(), "Rebooked on UA58"), NOW);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new FlightCancelled(flightId, "Rebooked on UA58", NOW));
    }

    @Test
    void unknownFlightIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of());
        CancelFlight service = new CancelFlight(executor);

        assertThatExceptionOfType(FlightNotFound.class)
                .isThrownBy(() -> service.cancelFlight(UUID.randomUUID(),
                        new CancelFlightRequest(UUID.randomUUID(), ""), NOW));
    }

    @Test
    void flightAlreadyCancelledIsRefused() {
        FlightId flightId = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(flightId)),
                storedEvent(2, new FlightCancelled(flightId, "First time", NOW))));
        CancelFlight service = new CancelFlight(executor);

        assertThatExceptionOfType(FlightNotFound.class)
                .isThrownBy(() -> service.cancelFlight(UUID.randomUUID(),
                        new CancelFlightRequest(flightId.id(), "again"), NOW));
    }

    @Test
    void anotherFlightsCancellationDoesNotClearThisOne() {
        FlightId flightId = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(flightId)),
                storedEvent(2, new FlightCancelled(FlightId.random(), "someone else's", NOW))));
        CancelFlight service = new CancelFlight(executor);

        service.cancelFlight(UUID.randomUUID(), new CancelFlightRequest(flightId.id(), ""), NOW);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new FlightCancelled(flightId, "", NOW));
    }

    @Test
    void aChangeCannotResurrectACancelledFlight() {
        FlightId flightId = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, booked(flightId)),
                storedEvent(2, new FlightCancelled(flightId, "First time", NOW)),
                storedEvent(3, changed(flightId))));
        CancelFlight service = new CancelFlight(executor);

        assertThatExceptionOfType(FlightNotFound.class)
                .isThrownBy(() -> service.cancelFlight(UUID.randomUUID(),
                        new CancelFlightRequest(flightId.id(), ""), NOW));
    }

    @Test
    void aFlightThatAlreadyDepartedIsStillCancellable() {
        // No time gate, unlike ChangeFlight: `now` is years after departure and is only recorded.
        FlightId flightId = FlightId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, new FlightBooked(flightId, "United Airlines", "UA2091",
                        AirportCode.of("SFO"), longAgo(6), AirportCode.of("LAX"), longAgo(8)))));
        CancelFlight service = new CancelFlight(executor);

        service.cancelFlight(UUID.randomUUID(), new CancelFlightRequest(flightId.id(), "never flew"), NOW);

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new FlightCancelled(flightId, "never flew", NOW));
    }

    private static FlightBooked booked(FlightId flightId) {
        return new FlightBooked(flightId, "United Airlines", "UA2091",
                AirportCode.of("SFO"), at(6), AirportCode.of("LAX"), at(8));
    }

    private static FlightChanged changed(FlightId flightId) {
        return new FlightChanged(flightId, "United Airlines", "UA2093",
                AirportCode.of("SFO"), at(7), AirportCode.of("LAX"), at(9), "");
    }

    private static ZonedTimestamp at(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, hour, 0), LOS_ANGELES);
    }

    private static ZonedTimestamp longAgo(int hour) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2019, 3, 4, hour, 0), LOS_ANGELES);
    }

    private static StoredEvent storedEvent(long sequence, Event payload) {
        return new StoredEvent(sequence, payload.getClass(), UUID.randomUUID(),
                Instant.now(), payload, UUID.randomUUID());
    }

    /** A spy: records what the service emitted, running the command against the folded context. */
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
