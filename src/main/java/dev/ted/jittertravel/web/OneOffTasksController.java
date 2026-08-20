package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.OneOffTasks;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The post-deploy task list: what a deploy left for Ted to do by hand, and a way to say it is done.
 * OWNER-only via the existing {@code /admin/**} rule in {@code SecurityConfig}.
 * <p>
 * Thymeleaf rather than j2html because the page posts (the split in CLAUDE.md), and its own
 * controller rather than a branch of {@code AdminController}: this is a slice, not another admin
 * report.
 */
@Controller
public class OneOffTasksController {

    private static final Logger log = LoggerFactory.getLogger(OneOffTasksController.class);

    private final OneOffTasks oneOffTasks;
    private final Clock clock;

    public OneOffTasksController(OneOffTasks oneOffTasks, Clock clock) {
        this.oneOffTasks = oneOffTasks;
        this.clock = clock;
    }

    @GetMapping("/admin/tasks")
    public String tasks(Model model) {
        model.addAttribute("tasks", oneOffTasks.views());
        return "admin-tasks";
    }

    @PostMapping("/admin/tasks/{taskId}/complete")
    public String complete(@PathVariable("taskId") String taskId) {
        try {
            // commandId and completedOn — the nondeterministic inputs — are captured here at the
            // boundary. An unknown or already-completed id returns false and writes nothing; the
            // page it redirects to shows the truth either way, so there is no error to report.
            oneOffTasks.complete(UUID.randomUUID(), taskId, Instant.now(clock));
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to complete a post-deploy task while in read-only mode", e);
            return "redirect:/read-only";
        }
        return "redirect:/admin/tasks";
    }
}
