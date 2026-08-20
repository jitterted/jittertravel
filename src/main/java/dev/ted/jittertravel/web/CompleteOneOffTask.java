package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.OneOffTaskCompleted;

import java.time.Instant;
import java.util.stream.Stream;

/**
 * Command record for ticking a post-deploy task off the list. A typed record with its own
 * {@link #events()}, like {@link MigrateConferenceToGathering}: it is the durable representation of
 * the action, and the single source of the event it emits, applied through
 * {@code CommandExecutor.appendEvents} — the internal-action path, since ticking off a chore is not
 * a travel decision and has nothing to refuse.
 */
public record CompleteOneOffTask(
        String taskId,
        Instant completedOn
) {
    public Stream<OneOffTaskCompleted> events() {
        return Stream.of(new OneOffTaskCompleted(taskId, completedOn));
    }
}
