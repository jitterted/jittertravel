package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.PlanTentativeConferenceCommand;
import dev.ted.jittertravel.infrastructure.InMemoryEventStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class ConferenceController {

    private final InMemoryEventStore eventStore;
    private final TentativeConferenceProjector projector;

    public ConferenceController(InMemoryEventStore eventStore,
                                TentativeConferenceProjector projector) {
        this.eventStore = eventStore;
        this.projector = projector;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/plan-conference")
    public String planConferenceForm(Model model) {
        PlanTentativeConferenceRequest command = new PlanTentativeConferenceRequest();
        command.setConferenceId(UUID.randomUUID().toString());
        model.addAttribute("planTentativeConference", command);
        return "plan-conference";
    }

    @PostMapping("/plan-conference")
    public String planConferenceSubmit(@ModelAttribute PlanTentativeConferenceRequest command, BindingResult bindingResult) {
        LocalDateTime now = LocalDateTime.now();

        PlanTentativeConferenceCommand commandObj = new PlanTentativeConferenceCommand();
        try {
            eventStore.append(commandObj.execute(command, now));
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "plan-conference";
        }

        return "redirect:/tentative-conferences";
    }

    @GetMapping("/tentative-conferences")
    public String tentativeConferences(Model model) {
        model.addAttribute("conferences", projector.views());
        return "tentative-conferences";
    }

}
