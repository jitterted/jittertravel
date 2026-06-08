package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class GeneralController {

    private final PostgresPersister persister;

    GeneralController(PostgresPersister persister) {
        this.persister = persister;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pendingCount", persister.countPendingCommands());
        return "index";
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

}
