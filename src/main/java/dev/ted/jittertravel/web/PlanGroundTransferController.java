package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.GroundTransferEndpointOptions;
import dev.ted.jittertravel.application.GroundTransferPlanning;
import dev.ted.jittertravel.application.SameTransferEndpoints;
import dev.ted.jittertravel.application.UnknownTransferEndpoint;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.InvalidGroundTransferTimeRange;
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
public class PlanGroundTransferController {

    private final GroundTransferPlanning groundTransferPlanning;
    private final GroundTransferEndpointOptions endpointOptions;
    private final Clock clock;

    public PlanGroundTransferController(GroundTransferPlanning groundTransferPlanning,
                                        GroundTransferEndpointOptions endpointOptions,
                                        Clock clock) {
        this.groundTransferPlanning = groundTransferPlanning;
        this.endpointOptions = endpointOptions;
        this.clock = clock;
    }

    /**
     * The two {@code <select>}s' contents, on the GET <em>and</em> on a re-rendered POST — a form
     * that came back with an error must still offer the choices it was asking for.
     */
    @ModelAttribute("endpointChoices")
    public GroundTransferEndpointChoices endpointChoices() {
        return endpointOptions.choicesAt(Instant.now(clock));
    }

    @GetMapping("/plan-ground-transfer")
    public String planGroundTransferForm(Model model,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlanGroundTransferRequest request = new PlanGroundTransferRequest();
        request.setGroundTransferId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the day (D9); it never filters the endpoint
        // options (D10). Absent, the default is today: a transfer is normally added to a trip
        // already under way, which is exactly why it has no future-date rule.
        request.setDate(date != null ? date : LocalDate.now(clock));
        request.setDepartureTime(LocalTime.of(12, 0));
        request.setArrivalTime(LocalTime.of(12, 45));
        model.addAttribute("planGroundTransfer", request);
        return "plan-ground-transfer";
    }

    @PostMapping("/plan-ground-transfer")
    public String planGroundTransferSubmit(@ModelAttribute("planGroundTransfer") PlanGroundTransferRequest request,
                                           BindingResult bindingResult) {
        try {
            // No `now`: a ground transfer has no future-date rule (D6), so its decision context is
            // empty and there is nothing about the current moment to capture at the boundary.
            groundTransferPlanning.planGroundTransfer(request);
        } catch (SameTransferEndpoints e) {
            bindingResult.rejectValue("destination", "sameEndpoints", e.getMessage());
        } catch (InvalidGroundTransferTimeRange e) {
            bindingResult.rejectValue("arrivalTime", "afterDepartureTime", e.getMessage());
        } catch (UnknownTransferEndpoint e) {
            // Which end failed is not worth guessing at: both selects are on screen, and the
            // message names the token. A global error keeps it on the page they are looking at.
            bindingResult.reject("unknownEndpoint", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.reject("zoneUnresolved",
                    "Could not determine the time zone of one of those places — "
                    + "pick a different endpoint, or fix the hotel's address first.");
        }

        if (bindingResult.hasErrors()) {
            return "plan-ground-transfer";
        }

        return "redirect:/calendar";
    }
}
