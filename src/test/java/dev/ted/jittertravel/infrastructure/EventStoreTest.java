package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStoreTest {

    @Test
    void appendingEventsAssignsSequencesAndNotifiesSubscribers() {
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), mockPersister());
        List<StoredEvent> receivedEvents = new ArrayList<>();
        eventStore.subscribe(eventStream -> receivedEvents.addAll(eventStream.toList()));

        Event event1 = new DummyEvent1();
        Event event2 = new DummyEvent2();
        UUID commandId = UUID.randomUUID();
        eventStore.append(Stream.of(event1, event2), commandId);

        assertThat(receivedEvents)
                .hasSize(2);

        StoredEvent firstStoredEvent = receivedEvents.getFirst();
        assertThat(firstStoredEvent.sequence())
                .isEqualTo(1);
        assertThat(firstStoredEvent.payload())
                .isEqualTo(event1);
        assertThat(firstStoredEvent.commandId())
                .isEqualTo(commandId);

        StoredEvent secondStoredEvent = receivedEvents.get(1);
        assertThat(secondStoredEvent.sequence())
                .isEqualTo(2);
        assertThat(secondStoredEvent.payload())
                .isEqualTo(event2);
        assertThat(secondStoredEvent.commandId())
                .isEqualTo(commandId);

        eventStore.append(Stream.of(new DummyEvent3()), UUID.randomUUID());
        assertThat(receivedEvents)
                .hasSize(3);
        assertThat(receivedEvents.get(2).sequence())
                .isEqualTo(3);
    }

    @Test
    void subscribersNotNotifiedWhenPersistenceFails() {
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), failingPersister());
        List<StoredEvent> receivedEvents = new ArrayList<>();
        eventStore.subscribe(eventStream -> receivedEvents.addAll(eventStream.toList()));

        assertThatThrownBy(() -> eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        assertThat(receivedEvents)
                .as("subscriber must not see events from a command that failed to persist")
                .isEmpty();
    }

    private PostgresPersister failingPersister() {
        return new PostgresPersister(null, null) {
            @Override public long getMaxSequence() { return 0; }
            @Override public List<StoredEvent> loadAllEvents() { return List.of(); }
            @Override public void appendEvents(List<StoredEvent> events, UUID commandId) {
                throw new RuntimeException("simulated DB failure");
            }
        };
    }

    private PostgresPersister mockPersister() {
        return new PostgresPersister(null, null) {
            @Override public long getMaxSequence() { return 0; }
            @Override public List<StoredEvent> loadAllEvents() { return List.of(); }
            @Override public void appendEvents(List<StoredEvent> events, UUID commandId) {}
        };
    }

    record DummyEvent1() implements Event { }

    record DummyEvent2() implements Event { }

    record DummyEvent3() implements Event { }

}
