package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin/pending-commands")
class PendingCommandsController {

    private final PostgresPersister persister;

    PendingCommandsController(PostgresPersister persister) {
        this.persister = persister;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("commands", persister.findPendingCommands());
        return "admin-pending-commands";
    }

    @PostMapping("/{commandId}/abandon")
    public String abandon(@PathVariable UUID commandId, RedirectAttributes redirectAttributes) {
        persister.abandonCommand(commandId);
        redirectAttributes.addFlashAttribute("message", "Command " + commandId + " marked as abandoned.");
        return "redirect:/admin/pending-commands";
    }

    @PostMapping("/{commandId}/keep")
    public String keep(@PathVariable UUID commandId, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", "Command " + commandId + " kept as pending.");
        return "redirect:/admin/pending-commands";
    }
}
