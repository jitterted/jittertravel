package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class InMemoryEventStore {
    private final Object transactionLock = new Object();
    private final List<StoredEvent> events = new ArrayList<>();
    private final List<EventStreamConsumer> subscribers = new ArrayList<>();
    private final AtomicLong nextSequence = new AtomicLong(1);
    private final MeterRegistry meterRegistry;
    private final DistributionSummary batchSizeSummary;
    private final AtomicBoolean isReadOnly = new AtomicBoolean(false);
    private final BlockingQueue<Batch> persistenceQueue = new LinkedBlockingQueue<>();

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventStore.class);
    private static final Duration NOTIFICATION_WARN_THRESHOLD = Duration.ofMillis(100);

    private record Batch(List<StoredEvent> events, UUID commandId) {}

    public InMemoryEventStore(MeterRegistry meterRegistry, PostgresPersister persister) {
        this.meterRegistry = meterRegistry;
        this.batchSizeSummary = DistributionSummary.builder("eventstore.batch.size")
                .description("Number of events per append batch")
                .register(meterRegistry);
        
        try {
            long maxSeq = persister.getMaxSequence();
            nextSequence.set(maxSeq + 1);
            List<StoredEvent> existingEvents = persister.loadAllEvents();
            this.events.addAll(existingEvents);
            log.info("Replayed {} events from persistent store. Next sequence: {}", existingEvents.size(), nextSequence.get());
        } catch (Exception e) {
            log.error("Failed to load events from persistent store. System entering read-only mode.", e);
            isReadOnly.set(true);
        }

        startAsyncWorker(persister);
    }

    private void startAsyncWorker(PostgresPersister persister) {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    Batch batch = persistenceQueue.take();
                    persister.appendEvents(batch.events, batch.commandId);
                    persister.linkCommandToEvents(batch.commandId, 
                        batch.events.stream().map(StoredEvent::eventId).toList());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Persistence worker failed. Entering read-only mode.", e);
                    isReadOnly.set(true);
                }
            }
        }, "EventStore-Persistence-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isReadOnly() {
        return isReadOnly.get();
    }

    public void append(Stream<? extends Event> eventStream, UUID commandId) {
        if (isReadOnly.get()) {
            throw new IllegalStateException("System is in read-only mode.");
        }
        synchronized (transactionLock) {
            List<StoredEvent> storedEvents = eventStream.map(payload -> new StoredEvent(
                    nextSequence.getAndIncrement(),
                    payload.getClass(),
                    UUID.randomUUID(),
                    Instant.now(),
                    payload,
                    commandId
            )).toList();

            events.addAll(storedEvents);
            batchSizeSummary.record(storedEvents.size());
            persistenceQueue.offer(new Batch(storedEvents, commandId));
            notifySubscribers(List.copyOf(subscribers), storedEvents);
        }
    }

    public void subscribe(EventStreamConsumer consumer) {
        synchronized (transactionLock) {
            subscribers.add(consumer);
        }
    }

    public Stream<StoredEvent> findAll() {
        synchronized (transactionLock) {
            return new ArrayList<>(events).stream();
        }
    }

    private void notifySubscribers(
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