package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStoreTest {

    @Test
    void appendingEventsAssignsSequencesAndNotifiesSubscribers() {
        InMemoryEventStore eventStore = new InMemoryEventStore(new SimpleMeterRegistry());
        List<StoredEvent> receivedEvents = new ArrayList<>();
        eventStore.subscribe(eventStream -> receivedEvents.addAll(eventStream.toList()));

        Event event1 = new DummyEvent1();
        Event event2 = new DummyEvent2();
        eventStore.append(Stream.of(event1, event2));

        assertThat(receivedEvents).hasSize(2);
        assertThat(receivedEvents.get(0).sequence())
                .isEqualTo(1);
        assertThat(receivedEvents.get(0).payload())
                .isEqualTo(event1);
        assertThat(receivedEvents.get(1).sequence())
                .isEqualTo(2);
        assertThat(receivedEvents.get(1).payload())
                .isEqualTo(event2);

        eventStore.append(Stream.of(new DummyEvent3()));
        assertThat(receivedEvents)
                .hasSize(3);
        assertThat(receivedEvents.get(2).sequence())
                .isEqualTo(3);
    }

    record DummyEvent1() implements Event { }

    record DummyEvent2() implements Event { }

    record DummyEvent3() implements Event { }

}
