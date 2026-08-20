package dev.ted.jittertravel.domain;

import java.time.Instant;

/**
 * Ted did one of the one-off jobs a deploy left behind — ran a migration, finished a backfill.
 * The <strong>one-way latch</strong> of {@code docs/PostDeployTaskBannerPlan.md}: the task itself is
 * declared in code, and this is the only part that persists, so once it is in the log the task stops
 * being outstanding for good.
 * <p>
 * There is deliberately no "declared" counterpart. Writing one at startup would need a per-boot
 * idempotency fold, would throw in read-only mode — exactly when the banner matters most — and would
 * put a fact about a <em>deployment</em> into a log that otherwise holds facts about Ted's travel,
 * to be copied verbatim into every backup. A completion is at least a decision Ted made.
 * <p>
 * {@code taskId} is the registry id, not a UUID: it is written by hand in the code that declares the
 * task, has to survive that code being deleted, and is what a human reads in the event log.
 *
 * @param completedOn when Ted ticked it off, captured at the boundary (external-inputs rule).
 */
public record OneOffTaskCompleted(
        String taskId,
        Instant completedOn
) implements Event {

    public OneOffTaskCompleted {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException(
                    "taskId must not be blank — it is how a completion finds its declaration");
        }
    }
}
