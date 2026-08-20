package dev.ted.jittertravel.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A declared task plus what the event log says about it: {@code completedOn} is {@code null} while
 * the task is still outstanding.
 * <p>
 * A completed task never appears in the banner — the banner is for outstanding work, and it
 * disappears entirely once there is none (Ted, 2026-08-19). It stays on {@code /admin/tasks},
 * greyed, because that is the reminder that its declaration is now dead code.
 */
public record OneOffTaskView(
        String id,
        String title,
        String detail,
        String actionPath,
        String actionLabel,
        LocalDate declaredOn,
        Instant completedOn
) {
    public boolean completed() {
        return completedOn != null;
    }
}
