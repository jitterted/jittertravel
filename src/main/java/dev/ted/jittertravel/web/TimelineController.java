package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.TimelineEntry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
class TimelineController {

    static final int PAGE_SIZE = 50;

    private final PostgresPersister persister;

    TimelineController(PostgresPersister persister) {
        this.persister = persister;
    }

    @GetMapping("/admin/commandlog")
    public String commandTimeline(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "true") boolean reverse,
                                  @RequestParam(defaultValue = "") String status,
                                  Model model) {
        int safePage = Math.max(page, 0);
        int totalCommands = persister.countCommands(status);
        int totalPages = Math.max(1, (int) Math.ceil(totalCommands / (double) PAGE_SIZE));
        if (safePage >= totalPages) {
            safePage = totalPages - 1;
        }

        int offset = safePage * PAGE_SIZE;
        List<TimelineEntry> entries = persister.loadTimelinePage(offset, PAGE_SIZE, status);

        // Divergence is computed in canonical oldest-first order; reverse only for display.
        if (reverse) {
            List<TimelineEntry> reversed = new ArrayList<>(entries);
            Collections.reverse(reversed);
            entries = reversed;
        }

        model.addAttribute("entries", entries);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCommands", totalCommands);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("reverse", reverse);
        model.addAttribute("status", status);
        model.addAttribute("hasPrev", safePage > 0);
        model.addAttribute("hasNext", safePage < totalPages - 1);
        return "timeline-commands";
    }

    @PostMapping("/admin/commandlog/{commandId}/delete")
    public String deleteCommand(@PathVariable UUID commandId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "true") boolean reverse,
                                @RequestParam(defaultValue = "") String status,
                                RedirectAttributes redirectAttributes) {
        persister.deleteCommand(commandId);
        redirectAttributes.addFlashAttribute("commandDeleted", true);
        return "redirect:/admin/commandlog?page=" + page + "&reverse=" + reverse + "&status=" + status;
    }
}
