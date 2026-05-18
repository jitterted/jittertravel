package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.UUID;

@Controller
public class ConferenceController {

    private final ConferencePlanning applicationService;
    private final TentativeConferenceProjector projector;

    public ConferenceController(ConferencePlanning applicationService,
                                TentativeConferenceProjector projector) {
        this.applicationService = applicationService;
        this.projector = projector;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/read-only")
    public String readOnly() {
        return "read-only";
    }

    @GetMapping("/plan-conference")
    public String planConferenceForm(Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }
        PlanTentativeConferenceRequest command = new PlanTentativeConferenceRequest();
        command.setConferenceId(UUID.randomUUID().toString());
        model.addAttribute("planTentativeConference", command);
        return "plan-conference";
    }

    @PostMapping("/plan-conference")
    public String planConferenceSubmit(@ModelAttribute PlanTentativeConferenceRequest command, BindingResult bindingResult) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        try {
            applicationService.planConference(command);
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        } catch (IllegalStateException e) {
            return "redirect:/read-only";
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
