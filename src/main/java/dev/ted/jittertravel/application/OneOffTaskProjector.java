package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.StoredEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Folds {@link OneOffTaskCompleted} into "which post-deploy tasks are done, and when".
 * <p>
 * It knows nothing about which tasks exist — that is {@link OneOffTaskRegistry}, in code — so a
 * completion for an id nobody declares any more is kept rather than dropped. That is the normal
 * end state: the declaration gets deleted once the job is done, and the event outlives it.
 */
public class OneOffTaskProjector implements EventStreamConsumer {

    private final Map<String, Instant> completedOn = new ConcurrentHashMap<>();

    @Override
    public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(storedEvent -> {
            switch (storedEvent.payload()) {
                // A one-way latch: the first completion wins, so re-running a replay or importing a
                // backup twice cannot move the date around.
                case OneOffTaskCompleted event ->
                        completedOn.putIfAbsent(event.taskId(), event.completedOn());
                default -> {}
            }
        });
    }

    public Optional<Instant> completedOn(String taskId) {
        return Optional.ofNullable(completedOn.get(taskId));
    }
}
