package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.GatheringDateNotInFuture;
import dev.ted.jittertravel.domain.InvalidGatheringTimeRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
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

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/plan-gathering")
    public String planGatheringForm(Model model,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlanGatheringRequest request = new PlanGatheringRequest();
        request.setGatheringId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the gathering day; the default (one week
        // out) stands when absent so the index nav card is unaffected.
        request.setDate(date != null ? date : LocalDate.now(clock).plusWeeks(1));
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
            // now is captured at the boundary as an Instant; the venue zone is resolved inward.
            gatheringPlanning.planGathering(request, Instant.now(clock));
        } catch (GatheringDateNotInFuture e) {
            bindingResult.rejectValue("date", "future", e.getMessage());
        } catch (InvalidGatheringTimeRange e) {
            bindingResult.rejectValue("endTime", "afterStartTime", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }

        if (bindingResult.hasErrors()) {
            return "plan-gathering";
        }

        return "redirect:/planned-gatherings";
    }
}
