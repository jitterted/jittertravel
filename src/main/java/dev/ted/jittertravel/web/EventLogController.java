package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
class EventLogController {

    static final int PAGE_SIZE = 50;

    private final PostgresPersister persister;

    EventLogController(PostgresPersister persister) {
        this.persister = persister;
    }

    @GetMapping("/admin/eventlog")
    public String eventLog(@RequestParam(defaultValue = "0") int page, Model model) {
        int safePage = Math.max(page, 0);
        int totalEvents = persister.countEvents();
        int totalPages = Math.max(1, (int) Math.ceil(totalEvents / (double) PAGE_SIZE));
        if (safePage >= totalPages) {
            safePage = totalPages - 1;
        }

        int offset = safePage * PAGE_SIZE;
        List<PostgresPersister.EventLogRow> events = persister.loadEventPage(offset, PAGE_SIZE);

        model.addAttribute("events", events);
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", safePage > 0);
        model.addAttribute("hasNext", safePage < totalPages - 1);
        return "admin-eventlog";
    }
}
