package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PrivateEventPlanning;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.InvalidPrivateEventTimeRange;
import dev.ted.jittertravel.domain.PrivateEventDateNotInFuture;
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
public class PlanPrivateEventController {
    private final PrivateEventPlanning privateEventPlanning;
    private final Clock clock;

    public PlanPrivateEventController(PrivateEventPlanning privateEventPlanning, Clock clock) {
        this.privateEventPlanning = privateEventPlanning;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/plan-private-event")
    public String planPrivateEventForm(Model model,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlanPrivateEventRequest request = new PlanPrivateEventRequest();
        request.setPrivateEventId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the day; the default (one week out) stands when
        // absent so the index nav card is unaffected.
        request.setDate(date != null ? date : LocalDate.now(clock).plusWeeks(1));
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(21, 0));
        model.addAttribute("planPrivateEvent", request);
        return "plan-private-event";
    }

    @PostMapping("/plan-private-event")
    public String planPrivateEventSubmit(@ModelAttribute("planPrivateEvent") PlanPrivateEventRequest request,
                                         BindingResult bindingResult) {
        try {
            // now is captured at the boundary as an Instant; the venue zone is resolved inward.
            privateEventPlanning.planPrivateEvent(request, Instant.now(clock));
        } catch (PrivateEventDateNotInFuture e) {
            bindingResult.rejectValue("date", "future", e.getMessage());
        } catch (InvalidPrivateEventTimeRange e) {
            bindingResult.rejectValue("endTime", "afterStartTime", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }

        if (bindingResult.hasErrors()) {
            return "plan-private-event";
        }

        return "redirect:/calendar";
    }
}
