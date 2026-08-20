package dev.ted.jittertravel.application;

import java.time.LocalDate;

/**
 * One job a deploy left behind — a migration to run, or a backfill of data that did not exist
 * before. Declared <em>in code</em> (see {@link OneOffTaskRegistry}) rather than stored, so deleting
 * the declaration is what retires the task.
 *
 * @param id          stable, hand-written, kebab-case. It is what {@code OneOffTaskCompleted}
 *                    records, so it must not change once shipped — renaming it resurrects a task
 *                    Ted has already done.
 * @param title       the one-line imperative shown in the banner and the list.
 * @param detail      why it exists and what "done" looks like, for the Ted who reads this in three
 *                    months.
 * @param actionPath  where the job is actually done — the migration page, the list to work through.
 * @param actionLabel wording for that link.
 * @param declaredOn  the day the task shipped. Not decoration: it is what the planned age check
 *                    reads to notice a declaration nobody has cleaned up (build order step 3).
 */
public record OneOffTask(
        String id,
        String title,
        String detail,
        String actionPath,
        String actionLabel,
        LocalDate declaredOn
) {
}
