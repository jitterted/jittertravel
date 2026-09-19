package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStoreTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void appendingEventsAssignsSequencesAndNotifiesSubscribers() {
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), new InMemoryPersister(), FIXED_CLOCK, Runnable::run);
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
        InMemoryPersister persister = new InMemoryPersister();
        persister.failOnAppend();
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), persister, FIXED_CLOCK, Runnable::run);
        List<StoredEvent> receivedEvents = new ArrayList<>();
        eventStore.subscribe(eventStream -> receivedEvents.addAll(eventStream.toList()));

        assertThatThrownBy(() -> eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        assertThat(receivedEvents)
                .as("subscriber must not see events from a command that failed to persist")
                .isEmpty();
    }

    @Test
    void reloadReplacesTheInMemoryEventsWithWhatTheDurableStoreNowHolds() {
        InMemoryPersister persister = new InMemoryPersister();
        persister.holds(storedEvent(1, new DummyEvent1()), storedEvent(2, new DummyEvent2()));
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), persister, FIXED_CLOCK, Runnable::run);

        assertThat(eventStore.findAll())
                .as("the constructor's boot replay reads the durable log")
                .hasSize(2);

        // Two rows, with a hole in the sequences and written in the wrong order: the next sequence
        // has to come from the HIGHEST row, which a single-row log cannot tell from the first one,
        // and the log is read back in sequence order however the rows went in.
        persister.holds(storedEvent(9, new DummyEvent3()), storedEvent(7, new DummyEvent3()));
        eventStore.reload();

        assertThat(eventStore.findAll().map(StoredEvent::payload))
                .as("reload replaces the in-memory list rather than adding to it")
                .containsExactly(new DummyEvent3(), new DummyEvent3());

        eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID());
        assertThat(eventStore.findAll().map(StoredEvent::sequence))
                .as("the next sequence continues past the highest of the reloaded log, not the log it replaced")
                .containsExactly(7L, 9L, 10L);
    }

    @Test
    void failedReloadKeepsThePreviousEventsAndLatchesReadOnly() {
        InMemoryPersister persister = new InMemoryPersister();
        persister.holds(storedEvent(1, new DummyEvent1()));
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), persister, FIXED_CLOCK, Runnable::run);

        persister.failOnLoad();
        eventStore.reload();

        assertThat(eventStore.findAll().map(StoredEvent::payload))
                .as("a reload that cannot read the log leaves the previous events in place")
                .containsExactly(new DummyEvent1());
        assertThat(eventStore.isReadOnly())
                .as("a reload that cannot read the log enters read-only mode")
                .isTrue();

        persister.loadsAgain();
        persister.holds(storedEvent(2, new DummyEvent2()));
        eventStore.reload();

        assertThat(eventStore.findAll().map(StoredEvent::payload))
                .as("the recovered reload still replaces the list")
                .containsExactly(new DummyEvent2());
        assertThat(eventStore.isReadOnly())
                .as("read-only is a one-way latch: a later successful reload does not lift it, only a restart does")
                .isTrue();
    }

    @Test
    void reactorsReceiveOneBatchPerAppendAfterTheSynchronousSubscribers() {
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), new InMemoryPersister(), FIXED_CLOCK, Runnable::run);
        List<String> order = new ArrayList<>();
        List<List<Event>> batches = new ArrayList<>();
        eventStore.subscribe(_ -> order.add("subscriber"));
        eventStore.subscribeAsync(events -> {
            order.add("reactor");
            batches.add(events.stream().map(StoredEvent::payload).toList());
        });

        eventStore.append(Stream.of(new DummyEvent1(), new DummyEvent2()), UUID.randomUUID());
        eventStore.append(Stream.of(new DummyEvent3()), UUID.randomUUID());

        assertThat(batches)
                .as("a reactor is handed the whole batch of one append, not one event at a time — "
                    + "which is what would let a multi-leg booking become a single email")
                .containsExactly(
                        List.of(new DummyEvent1(), new DummyEvent2()),
                        List.of(new DummyEvent3()));
        assertThat(order)
                .as("reactors are dispatched to after the synchronous subscribers have run, so a projector is never behind a reactor")
                .containsExactly("subscriber", "reactor", "subscriber", "reactor");
    }

    @Test
    void reactorsAreNotReplayedIntoWhenTheySubscribe() {
        InMemoryPersister persister = new InMemoryPersister();
        persister.holds(storedEvent(1, new DummyEvent1()), storedEvent(2, new DummyEvent2()));
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), persister, FIXED_CLOCK, Runnable::run);
        List<StoredEvent> received = new ArrayList<>();

        eventStore.subscribeAsync(received::addAll);

        assertThat(received)
                .as("subscribing a reactor replays no history: the first email reactor must not mail years of bookings at boot")
                .isEmpty();

        eventStore.append(Stream.of(new DummyEvent3()), UUID.randomUUID());

        assertThat(received.stream().map(StoredEvent::payload))
                .as("it sees only what is appended after it subscribed")
                .containsExactly(new DummyEvent3());
    }

    @Test
    void reactorsNotDispatchedToWhenPersistenceFails() {
        InMemoryPersister persister = new InMemoryPersister();
        persister.failOnAppend();
        EventStore eventStore = new EventStore(new SimpleMeterRegistry(), persister, FIXED_CLOCK, Runnable::run);
        List<StoredEvent> received = new ArrayList<>();
        eventStore.subscribeAsync(received::addAll);

        assertThatThrownBy(() -> eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        assertThat(received)
                .as("a reactor must not act on events from a command that failed to persist")
                .isEmpty();
    }

    @Test
    void aThrowingReactorIsCountedAndLeavesTheAppendAndTheOtherReactorsAlone() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EventStore eventStore = new EventStore(meterRegistry, new InMemoryPersister(), FIXED_CLOCK, Runnable::run);
        List<StoredEvent> received = new ArrayList<>();
        eventStore.subscribeAsync(new ThrowingReactor());
        eventStore.subscribeAsync(received::addAll);

        eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID());

        assertThat(received)
                .as("a reactor that throws does not stop the next one being dispatched to")
                .hasSize(1);
        assertThat(eventStore.findAll())
                .as("nor does it undo the append: the events are already durable when reactors run")
                .hasSize(1);
        assertThat(meterRegistry.counter("eventstore.reactor.failures", "reactor", "ThrowingReactor").count())
                .as("the failure is counted, because after append returns there is nobody to report it to")
                .isEqualTo(1.0);
    }

    @Test
    void aRejectedBatchIsDroppedAndCountedRatherThanRunOnTheAppendThread() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EventStore eventStore = new EventStore(meterRegistry, new InMemoryPersister(), FIXED_CLOCK, rejectingExecutor());
        RecordingReactor reactor = new RecordingReactor();
        eventStore.subscribeAsync(reactor);

        eventStore.append(Stream.of(new DummyEvent1()), UUID.randomUUID());

        assertThat(reactor.received)
                .as("a rejected task is dropped, never run on the caller: caller-runs would append inside another append's critical section")
                .isEmpty();
        assertThat(eventStore.findAll())
                .as("a full reactor queue must not fail the append — the events are durable and refusing would undo nothing")
                .hasSize(1);
        assertThat(meterRegistry.counter("eventstore.reactor.rejected", "reactor", "RecordingReactor").count())
                .as("the drop is counted, because it is the only record that a notification was lost")
                .isEqualTo(1.0);
    }

    private static Executor rejectingExecutor() {
        return _ -> {
            throw new RejectedExecutionException("queue full");
        };
    }

    private static final class ThrowingReactor implements EventReactor {
        @Override public void react(List<StoredEvent> events) {
            throw new RuntimeException("reactor blew up");
        }
    }

    /**
     * A named class rather than a lambda, because the metric this test reads is tagged with the
     * reactor's {@code getSimpleName()} and a lambda's is generated and unstable.
     */
    private static final class RecordingReactor implements EventReactor {
        private final List<StoredEvent> received = new ArrayList<>();

        @Override public void react(List<StoredEvent> events) {
            received.addAll(events);
        }
    }

    private StoredEvent storedEvent(long sequence, Event payload) {
        return new StoredEvent(
                sequence,
                payload.getClass(),
                UUID.randomUUID(),
                Instant.now(FIXED_CLOCK),
                payload,
                UUID.randomUUID()
        );
    }

    /**
     * Stands in for the database, and for what it can do to a store running on top of it: `holds`
     * is a truncate-and-refill of the log underneath one, `failOnLoad`/`loadsAgain` are the log
     * becoming unreadable and then readable again — the second being what the store must read and
     * still not treat as reason to leave read-only — and `failOnAppend` is a write that does not
     * land. Fresh, it is an empty readable log, which is what most cases here want.
     * <p>
     * Appending is not reflected in what a later load returns; nothing here appends and then
     * reloads, and a fake that kept the two in step would be claiming a fidelity it has not been
     * asked for.
     * <p>
     * It does however <strong>sort by sequence on load</strong>, because the real
     * {@code loadAllEvents()} is {@code ORDER BY sequence} and {@link EventStore#reload()} takes
     * its next sequence from the last row. A fake handing back insertion order would let a case
     * pass on an ordering the database never produces.
     */
    private static final class InMemoryPersister extends PostgresPersister {
        private List<StoredEvent> stored = List.of();
        private boolean loadFails;
        private boolean appendFails;

        private InMemoryPersister() {
            super(null, null, null, FIXED_CLOCK);
        }

        private void holds(StoredEvent... events) {
            stored = List.of(events);
        }

        private void failOnLoad() {
            loadFails = true;
        }

        private void loadsAgain() {
            loadFails = false;
        }

        private void failOnAppend() {
            appendFails = true;
        }

        @Override public List<StoredEvent> loadAllEvents() {
            if (loadFails) {
                throw new RuntimeException("simulated DB failure");
            }
            return stored.stream()
                    .sorted(comparingLong(StoredEvent::sequence))
                    .toList();
        }

        @Override public void appendEvents(List<StoredEvent> events, UUID commandId) {
            if (appendFails) {
                throw new RuntimeException("simulated DB failure");
            }
        }
    }

    record DummyEvent1() implements Event { }

    record DummyEvent2() implements Event { }

    record DummyEvent3() implements Event { }

}
