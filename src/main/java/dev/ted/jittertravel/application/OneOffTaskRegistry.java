package dev.ted.jittertravel.application;

import java.util.List;

/**
 * The declared post-deploy tasks, in code — the whole registry, and the only place a task is born.
 * <p>
 * <strong>Deleting an entry is how a task retires.</strong> Once Ted has ticked a task off, its
 * {@code OneOffTaskCompleted} event keeps it out of the banner for good, so the entry here is inert
 * scaffolding and should be removed on the next pass through this file. The completion event stays
 * behind as harmless history; nothing refers to the id any more.
 * <p>
 * Every task here is <em>acknowledged</em>: the app cannot tell it is done, so Ted says so. Tasks
 * the app can detect for itself (legacy rows remaining, a missing Railway secret) are step 2 of
 * {@code docs/PostDeployTaskBannerPlan.md} and will carry their own check — deliberately not built
 * yet, because there is nothing to share between one flavour and none.
 */
public class OneOffTaskRegistry {

    private final List<OneOffTask> declaredTasks;

    public OneOffTaskRegistry() {
        this(defaultTasks());
    }

    /** Lets a test declare its own tasks instead of asserting against the real, changing list. */
    public OneOffTaskRegistry(List<OneOffTask> declaredTasks) {
        this.declaredTasks = List.copyOf(declaredTasks);
    }

    /**
     * Empty, and that is a normal resting state — not a gap waiting to be filled. Both of the
     * first two tasks were run in production on 2026-08-21 and retired the same day, so the banner
     * hides itself and {@code /admin/tasks} shows "Nothing declared." Add an entry here when a
     * deploy needs a migration or a backfill; delete it once Ted has ticked it off.
     */
    private static List<OneOffTask> defaultTasks() {
        return List.of();
    }

    public List<OneOffTask> declaredTasks() {
        return declaredTasks;
    }
}
