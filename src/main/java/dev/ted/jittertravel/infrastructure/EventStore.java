package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class EventStore {
    private final Object transactionLock = new Object();
    private final List<StoredEvent> events = new ArrayList<>();
    private final List<EventStreamConsumer> synchronousSubscribers = new ArrayList<>();
    private final List<EventReactor> asynchronousReactors = new ArrayList<>();
    private final AtomicLong nextSequence = new AtomicLong(1);
    private final MeterRegistry meterRegistry;
    private final DistributionSummary batchSizeSummary;
    private final PostgresPersister persister;
    private final Clock clock;
    private final Executor reactorExecutor;
    private final AtomicBoolean isReadOnly = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);
    private static final Duration NOTIFICATION_WARN_THRESHOLD = Duration.ofMillis(100);

    public EventStore(MeterRegistry meterRegistry,
                      PostgresPersister persister,
                      Clock clock,
                      Executor reactorExecutor) {
        this.meterRegistry = meterRegistry;
        this.batchSizeSummary = DistributionSummary.builder("eventstore.batch.size")
                .description("Number of events per append batch")
                .register(meterRegistry);
        this.persister = persister;
        this.clock = clock;
        this.reactorExecutor = reactorExecutor;

        reload();
    }

    /**
     * Replaces the in-memory event list with whatever the durable store currently holds, and
     * re-points {@code nextSequence} at the end of it. Called once from the constructor — that is
     * the boot replay — and again whenever the stored log has changed underneath the store.
     *
     * <p><strong>Today the only such caller is a test returning to a known state</strong>
     * ({@code AbstractTestcontainerIntegrationTest}). An {@code /admin/database} truncate and a
     * restore both change the log underneath a running store and both still leave this list stale;
     * wiring them up is open work, because the projectors are stale after those too and the two
     * want deciding together — see "Test isolation: the test half is enforced, the production half
     * is not" in {@code docs/Cleanup_Tasks.md}.
     *
     * <p><strong>Why it exists at all.</strong> The list is filled at boot and otherwise only ever
     * appended to, so nothing that empties the tables reaches it. Since
     * {@code CommandExecutor.eventsForDecision()} folds every write-path decision from this list, a
     * stale entry can make the domain refuse a booking that is fine.
     *
     * <p>It deliberately does <strong>not</strong> rebuild the read models. Projectors accumulate
     * state, so replaying a shorter stream over them adds nothing and removes nothing; a projector
     * that has to forget needs rebuilding, which is a restart today.
     *
     * <p>A failed load leaves the previous list in place rather than emptying it, for the same
     * reason {@link #append} persists before it notifies: the in-memory view never gets ahead of
     * what is durable.
     *
     * <p><strong>{@code nextSequence} comes from the loaded list, not from a second query, and that
     * is not only a saved round trip.</strong> A separate {@code MAX(sequence)} read is not atomic
     * with the read of the rows: a row landing between the two leaves {@code nextSequence} pointing
     * at a sequence the list already holds, and the next {@link #append} collides on the
     * {@code event_log} primary key. {@code loadAllEvents()} is {@code ORDER BY sequence}, so the
     * last row it returns <em>is</em> the maximum — one read that cannot disagree with itself. Do
     * not re-introduce the second query.
     *
     * <p>Read-only is a <strong>one-way latch, and a later successful reload does not lift it</strong>
     * — only a restart does. That is deliberate rather than an oversight: read-only means something
     * went wrong that a person should look at, and a store that quietly healed itself on the next
     * reload would hide the failure that set it. It matters more now than at boot, because this
     * method is reachable at runtime.
     */
    public final void reload() {
        synchronized (transactionLock) {
            try {
                List<StoredEvent> existingEvents = persister.loadAllEvents();
                events.clear();
                events.addAll(existingEvents);
                nextSequence.set(existingEvents.isEmpty()
                                 ? 1
                                 : existingEvents.getLast().sequence() + 1);
                // "Loaded", not "Replayed": this fills the in-memory list and nothing else — the
                // read models are rebuilt by ProjectorBootstrapper at boot and not at all here.
                log.info("Loaded {} events from persistent store. Next sequence: {}", existingEvents.size(), nextSequence.get());
            } catch (Exception e) {
                log.error("Failed to Load and Process ALL Events from persistent store. Entering read-only mode.", e);
                isReadOnly.set(true);
            }
        }
    }

    public boolean isReadOnly() {
        return isReadOnly.get();
    }

    public void append(Stream<? extends Event> eventStream, UUID commandId) {
        synchronized (transactionLock) {
            List<StoredEvent> storedEvents = eventStream.map(payload -> new StoredEvent(
                    nextSequence.getAndIncrement(),
                    payload.getClass(),
                    UUID.randomUUID(),
                    Instant.now(clock),
                    payload,
                    commandId
            )).toList();

            batchSizeSummary.record(storedEvents.size());

            // Persist first: if this throws, subscribers are never notified and
            // in-memory state stays consistent with the durable store.
            try {
                persister.appendEvents(storedEvents, commandId);
            } catch (Exception e) {
                log.error("Failed to APPEND EVENTS to persistent store. Entering read-only mode.", e);
                isReadOnly.set(true);
                throw e;
            }

            events.addAll(storedEvents);
            notifySynchronousSubscribers(List.copyOf(synchronousSubscribers), storedEvents);
            dispatchToReactors(List.copyOf(asynchronousReactors), storedEvents);
        }
    }

    public void subscribe(EventStreamConsumer consumer) {
        synchronized (transactionLock) {
            synchronousSubscribers.add(consumer);
        }
    }

    /**
     * Registers a reactor to receive each appended batch on the injected {@code Executor}, after the
     * synchronous subscribers have been notified. Unlike {@link #subscribe}, <strong>no history is
     * replayed</strong> — a reactor only ever sees events appended after it was registered, which is
     * the at-most-once guarantee {@code docs/FamilyEmailNotificationsPlan.md} §3 depends on.
     *
     * <p>The handoff is a synchronous {@code execute} on the append thread, inside the critical
     * section; only the reactor's own work happens on the worker. A rejected task is logged,
     * counted and <strong>dropped</strong> — never run on the caller — and never fails the append,
     * because by then the events are durable and refusing would undo nothing.
     *
     * <p><strong>{@code Runnable::run} is a test-only executor.</strong> It makes delivery
     * same-thread and ordered, which is what a unit test wants, but it is {@code CallerRunsPolicy}
     * by another name and carries exactly the corruption
     * {@code EventSourcingConfig.eventReactorExecutor} describes. Safe for a recording reactor;
     * never for one that appends.
     */
    public void subscribeAsync(EventReactor reactor) {
        synchronized (transactionLock) {
            asynchronousReactors.add(reactor);
        }
    }

    public Stream<StoredEvent> findAll() {
        synchronized (transactionLock) {
            return new ArrayList<>(events).stream();
        }
    }

    /**
     * Hands the batch to each reactor on the executor. Errors are caught and counted rather than
     * propagated, for the same reason subscriber errors are: by the time this runs the events are
     * durable and the append has already succeeded, so there is nobody left to report to.
     */
    private void dispatchToReactors(List<EventReactor> reactors, List<StoredEvent> storedEvents) {
        for (EventReactor reactor : reactors) {
            String reactorName = reactor.getClass().getSimpleName();
            try {
                reactorExecutor.execute(() -> reactTo(reactor, reactorName, storedEvents));
            } catch (RejectedExecutionException ex) {
                meterRegistry.counter("eventstore.reactor.rejected",
                        "reactor", reactorName).increment();
                log.warn("Reactor {} could not be dispatched to and its batch of {} was dropped",
                        reactorName, storedEvents.size(), ex);
            }
        }
    }

    private void reactTo(EventReactor reactor, String reactorName, List<StoredEvent> storedEvents) {
        Timer.Sample reactorSample = Timer.start(meterRegistry);
        try {
            reactor.react(storedEvents);
        } catch (RuntimeException ex) {
            meterRegistry.counter("eventstore.reactor.failures",
                    "reactor", reactorName).increment();
            log.warn("Reactor {} failed", reactorName, ex);
        } finally {
            reactorSample.stop(Timer.builder("eventstore.reactor.duration")
                    .description("Per-reactor asynchronous handling duration")
                    .tag("reactor", reactorName)
                    .register(meterRegistry));
        }
    }

    private void notifySynchronousSubscribers(
            List<EventStreamConsumer> subscribers,
            List<StoredEvent> storedEvents
    ) {
        Timer.Sample totalSample = Timer.start(meterRegistry);

        for (EventStreamConsumer subscriber : subscribers) {
            String subscriberName = subscriber.getClass().getSimpleName();
            Timer.Sample subscriberSample = Timer.start(meterRegistry);
            try {
                subscriber.handle(storedEvents.stream());
            } catch (RuntimeException ex) {
                meterRegistry.counter("eventstore.subscriber.failures",
                        "subscriber", subscriberName).increment();
                log.warn("Subscriber {} failed", subscriberName, ex);
            } finally {
                subscriberSample.stop(Timer.builder("eventstore.subscriber.duration")
                        .description("Per-subscriber notification duration")
                        .tag("subscriber", subscriberName)
                        .register(meterRegistry));
            }
        }

        long totalNanos = totalSample.stop(Timer.builder("eventstore.notification.duration")
                .description("Total duration to notify all subscribers")
                .register(meterRegistry));

        if (totalNanos > NOTIFICATION_WARN_THRESHOLD.toNanos()) {
            log.warn("Slow notification: total_ms={} subscribers={} batch={}",
                    totalNanos / 1_000_000,
                    subscribers.size(),
                    storedEvents.size());
        }
    }
}