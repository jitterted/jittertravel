package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.EventLogFilter;
import dev.ted.jittertravel.infrastructure.EventTypes;
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
    public String eventLog(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(name = "type", defaultValue = "") List<String> typeNames,
                           @RequestParam(name = "id", defaultValue = "") String identifierText,
                           Model model) {
        EventLogFilter filter = EventLogFilter.from(typeNames, identifierText);
        int totalEvents = persister.countEvents();
        int matchingEvents = persister.countEvents(filter);

        int safePage = Math.max(page, 0);
        int totalPages = Math.max(1, (int) Math.ceil(matchingEvents / (double) PAGE_SIZE));
        if (safePage >= totalPages) {
            safePage = totalPages - 1;
        }

        int offset = safePage * PAGE_SIZE;
        List<PostgresPersister.EventLogRow> events = persister.loadEventPage(offset, PAGE_SIZE, filter);

        model.addAttribute("events", events);
        model.addAttribute("eventTypes", EventTypes.logicalNames());
        model.addAttribute("filterView", new EventLogFilterView(filter));
        model.addAttribute("page", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEvents", totalEvents);
        model.addAttribute("matchingEvents", matchingEvents);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", safePage > 0);
        model.addAttribute("hasNext", safePage < totalPages - 1);
        return "admin-eventlog";
    }
}
