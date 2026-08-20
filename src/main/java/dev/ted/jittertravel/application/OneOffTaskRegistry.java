package dev.ted.jittertravel.application;

import java.time.LocalDate;
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

    private static List<OneOffTask> defaultTasks() {
        return List.of(
                new OneOffTask(
                        "normalize-event-log-type",
                        "Run the event_log.type normalization",
                        """
                        Renaming ConferenceTentativelyPlanned to ConferencePlanned left stored rows \
                        holding both spellings. EventTypes aliases the old ids so everything \
                        resolves, but the log stays mixed until the eager migration rewrites it. \
                        Back up first — this rewrites rows.""",
                        "/admin/migrate-legacy-events",
                        "Open the migration page",
                        LocalDate.of(2026, 8, 19)),
                new OneOffTask(
                        "backfill-conference-attendance",
                        "Backfill conference attendance",
                        """
                        Conferences planned before the commitment slice have no \
                        ConferenceAttendanceConfirmed, so they all read "Maybe" on the public \
                        calendar. Confirm dev2next, ExploreDDD and SoCraTes through the real UI, \
                        recording each one's end state. J-Fall waits for CfpOpened in slice 3.""",
                        "/conferences",
                        "Open the conference list",
                        LocalDate.of(2026, 8, 19)));
    }

    public List<OneOffTask> declaredTasks() {
        return declaredTasks;
    }
}
