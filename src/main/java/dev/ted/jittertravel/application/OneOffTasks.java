package dev.ted.jittertravel.application;

import dev.ted.jittertravel.web.CompleteOneOffTask;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Joins the tasks declared in {@link OneOffTaskRegistry} to the completions folded by
 * {@link OneOffTaskProjector}, and records a new completion.
 * <p>
 * Recording goes through {@code CommandExecutor.appendEvents} — the internal-action path — because
 * ticking off a chore is not a travel decision: there is no rule to enforce and nothing to refuse,
 * so there is no {@code DomainCommand} and no decision context. Read-only mode still stops it, in
 * {@code CommandExecutor} rather than here.
 */
public class OneOffTasks {

    private final OneOffTaskRegistry registry;
    private final OneOffTaskProjector projector;
    private final CommandExecutor commandExecutor;

    public OneOffTasks(OneOffTaskRegistry registry,
                       OneOffTaskProjector projector,
                       CommandExecutor commandExecutor) {
        this.registry = registry;
        this.projector = projector;
        this.commandExecutor = commandExecutor;
    }

    /** Outstanding first, oldest declaration first — the one that has been waiting longest. */
    public List<OneOffTaskView> views() {
        return registry.declaredTasks().stream()
                       .map(this::toView)
                       .sorted(Comparator.comparing(OneOffTaskView::completed)
                                         .thenComparing(OneOffTaskView::declaredOn))
                       .toList();
    }

    public List<OneOffTaskView> outstanding() {
        return views().stream()
                      .filter(view -> !view.completed())
                      .toList();
    }

    /**
     * Ticks a task off, once. A second completion is silently ignored rather than appended: the
     * latch is one-way, so a double submit or a re-opened tab has nothing to add. An id nobody
     * declares is ignored too — it can only come from a hand-edited URL, and inventing a completion
     * for it would put a fact in the log that no code can explain.
     *
     * @return whether anything was recorded, so a caller can tell a real tick-off from a no-op.
     */
    public boolean complete(UUID commandId, String taskId, Instant completedOn) {
        if (!isDeclared(taskId) || projector.completedOn(taskId).isPresent()) {
            return false;
        }
        CompleteOneOffTask command = new CompleteOneOffTask(taskId, completedOn);
        commandExecutor.appendEvents(commandId, command, command.events());
        return true;
    }

    private boolean isDeclared(String taskId) {
        return registry.declaredTasks().stream()
                       .anyMatch(task -> task.id().equals(taskId));
    }

    private OneOffTaskView toView(OneOffTask task) {
        return new OneOffTaskView(
                task.id(), task.title(), task.detail(),
                task.actionPath(), task.actionLabel(), task.declaredOn(),
                projector.completedOn(task.id()).orElse(null)
        );
    }
}
