package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.TimelineEntry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
class TimelineController {

    static final int PAGE_SIZE = 50;

    private final PostgresPersister persister;

    TimelineController(PostgresPersister persister) {
        this.persister = persister;
    }

    @GetMapping("/timeline/commands")
    public String commandTimeline(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "false") boolean reverse,
                                  Model model) {
        int safePage = Math.max(page, 0);
        int totalCommands = persister.countCommands();
        int totalPages = Math.max(1, (int) Math.ceil(totalCommands / (double) PAGE_SIZE));
        if (safePage >= totalPages) {
            safePage = totalPages - 1;
        }

        int offset = safePage * PAGE_SIZE;
        List<TimelineEntry> entries = persister.loadTimelinePage(offset, PAGE_SIZE);

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
        model.addAttribute("hasPrev", safePage > 0);
        model.addAttribute("hasNext", safePage < totalPages - 1);
        return "timeline-commands";
    }
}
