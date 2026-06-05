package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.domain.GatheringDateNotInFuture;
import dev.ted.jittertravel.domain.InvalidGatheringTimeRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Controller
public class PlanGatheringController {
    private final GatheringPlanning gatheringPlanning;
    private final Clock clock;

    public PlanGatheringController(GatheringPlanning gatheringPlanning, Clock clock) {
        this.gatheringPlanning = gatheringPlanning;
        this.clock = clock;
    }

    @GetMapping("/plan-gathering")
    public String planGatheringForm(Model model) {
        PlanGatheringRequest request = new PlanGatheringRequest();
        request.setGatheringId(UUID.randomUUID().toString());
        request.setDate(LocalDate.now(clock).plusWeeks(1));
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(21, 0));
        request.setSpeaking(true);
        model.addAttribute("planGathering", request);
        return "plan-gathering";
    }

    @PostMapping("/plan-gathering")
    public String planGatheringSubmit(@ModelAttribute("planGathering") PlanGatheringRequest request,
                                      BindingResult bindingResult) {
        try {
            gatheringPlanning.planGathering(request);
        } catch (GatheringDateNotInFuture e) {
            bindingResult.rejectValue("date", "future", e.getMessage());
        } catch (InvalidGatheringTimeRange e) {
            bindingResult.rejectValue("endTime", "afterStartTime", e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "plan-gathering";
        }

        return "redirect:/planned-gatherings";
    }
}
