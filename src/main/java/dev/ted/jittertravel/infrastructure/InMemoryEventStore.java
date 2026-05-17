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
import java.util.stream.Stream;

public class InMemoryEventStore {
    private final Object transactionLock = new Object();
    private final List<StoredEvent> events = new ArrayList<>();
    private final List<EventStreamConsumer> subscribers = new ArrayList<>();
    private long nextSequence = 1;
    private final MeterRegistry meterRegistry;
    private final DistributionSummary batchSizeSummary;

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventStore.class);
    private static final Duration NOTIFICATION_WARN_THRESHOLD = Duration.ofMillis(100);

    public InMemoryEventStore(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.batchSizeSummary = DistributionSummary.builder("eventstore.batch.size")
                .description("Number of events per append batch")
                .register(meterRegistry);
    }

    public void append(Stream<? extends Event> eventStream) {
        synchronized (transactionLock) {
            List<StoredEvent> storedEvents = eventStream.map(payload -> new StoredEvent(
                    nextSequence++,
                    payload.getClass(),
                    UUID.randomUUID(),
                    Instant.now(),
                    payload
            )).toList();

            events.addAll(storedEvents);
            batchSizeSummary.record(storedEvents.size());
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

        checkSlowNotification(subscribers, storedEvents, totalSample);
    }

    private void checkSlowNotification(List<EventStreamConsumer> subscribers, List<StoredEvent> storedEvents, Timer.Sample totalSample) {
        long totalNanos = totalSample.stop(Timer.builder("eventstore.notification.duration")
                .description("Total duration to notify all subscribers")
                .register(meterRegistry));

        Duration totalDuration = Duration.ofNanos(totalNanos);

        if (totalDuration.compareTo(NOTIFICATION_WARN_THRESHOLD) > 0) {
            log.warn("Slow notification: total_ms={} subscribers={} batch={}",
                    totalDuration.toMillis(),
                    subscribers.size(),
                    storedEvents.size());
        }
    }
}