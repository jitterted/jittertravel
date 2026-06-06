package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class PlanConferenceController {

    private static final Logger log = LoggerFactory.getLogger(PlanConferenceController.class);
    private final ConferencePlanning applicationService;
    private final TentativeConferenceProjector projector;

    public PlanConferenceController(ConferencePlanning applicationService,
                                    TentativeConferenceProjector projector) {
        this.applicationService = applicationService;
        this.projector = projector;
    }

    @GetMapping("/plan-conference")
    public String planConferenceForm(Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(UUID.randomUUID().toString());
        LocalDateTime startDateTime = LocalDate.now().plusWeeks(1).atStartOfDay().plusHours(9);
        request.setStartDate(startDateTime);
        request.setEndDate(startDateTime.plusDays(2).plusHours(8));

        model.addAttribute("planTentativeConference", request);
        return "plan-conference";
    }

    @PostMapping("/plan-conference")
    public String planConferenceSubmit(@ModelAttribute PlanTentativeConferenceRequest command,
                                       BindingResult bindingResult) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        try {
            applicationService.planConference(command);
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to plan conference while in read-only mode", e);
            return "redirect:/read-only";
        }

        if (bindingResult.hasErrors()) {
            return "plan-conference";
        }

        return "redirect:/tentative-conferences";
    }

    @GetMapping("/tentative-conferences")
    public ResponseEntity<String> tentativeConferences() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(TentativeConferencesRenderer.render(projector.views()));
    }

}
