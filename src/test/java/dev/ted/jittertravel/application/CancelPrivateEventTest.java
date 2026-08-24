package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventCancelled;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventNotFound;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import dev.ted.jittertravel.web.CancelPrivateEventRequest;
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
 * decide whether a live private event exists, so a second cancel of the same evening is refused as
 * not-found rather than emitting a duplicate event.
 */
class CancelPrivateEventTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    void plannedEventCancelsAndEmitsTheCancelledEvent() {
        PrivateEventId privateEventId = PrivateEventId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(privateEventId))));
        CancelPrivateEvent service = new CancelPrivateEvent(executor);

        service.cancelPrivateEvent(UUID.randomUUID(),
                new CancelPrivateEventRequest(privateEventId.id(), "Rescheduled to Friday"));

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new PrivateEventCancelled(privateEventId, "Rescheduled to Friday"));
    }

    @Test
    void unknownEventIsRefused() {
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of());
        CancelPrivateEvent service = new CancelPrivateEvent(executor);

        assertThatExceptionOfType(PrivateEventNotFound.class)
                .isThrownBy(() -> service.cancelPrivateEvent(UUID.randomUUID(),
                        new CancelPrivateEventRequest(UUID.randomUUID(), "")));
    }

    @Test
    void eventAlreadyCancelledIsRefused() {
        PrivateEventId privateEventId = PrivateEventId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(privateEventId)),
                storedEvent(2, new PrivateEventCancelled(privateEventId, "First time"))));
        CancelPrivateEvent service = new CancelPrivateEvent(executor);

        assertThatExceptionOfType(PrivateEventNotFound.class)
                .isThrownBy(() -> service.cancelPrivateEvent(UUID.randomUUID(),
                        new CancelPrivateEventRequest(privateEventId.id(), "again")));
    }

    @Test
    void anotherEventsCancellationDoesNotClearThisOne() {
        // The fold keys on the id: cancelling one dinner must not make a second one uncancellable.
        PrivateEventId privateEventId = PrivateEventId.random();
        RecordingCommandExecutor executor = new RecordingCommandExecutor(Stream.of(
                storedEvent(1, planned(privateEventId)),
                storedEvent(2, new PrivateEventCancelled(PrivateEventId.random(), "someone else's"))));
        CancelPrivateEvent service = new CancelPrivateEvent(executor);

        service.cancelPrivateEvent(UUID.randomUUID(),
                new CancelPrivateEventRequest(privateEventId.id(), ""));

        assertThat(executor.emittedEvents)
                .singleElement()
                .isEqualTo(new PrivateEventCancelled(privateEventId, ""));
    }

    private static PrivateEventPlanned planned(PrivateEventId privateEventId) {
        Address venue = new Address("1 Frith St", "London", "", "W1D 4TL", "GB", "London");
        return new PrivateEventPlanned(
                privateEventId, "Dinner with the Smiths", "Chez Moi", venue,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 19, 0), LONDON),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 22, 0), LONDON));
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
