package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GroundTransferEndpointChoices;
import dev.ted.jittertravel.application.GroundTransferEndpointOptions;
import dev.ted.jittertravel.application.GroundTransferPlanning;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.SameTransferEndpoints;
import dev.ted.jittertravel.application.UnknownTransferEndpoint;
import dev.ted.jittertravel.domain.InvalidGroundTransferTimeRange;
import dev.ted.jittertravel.domain.ZoneResolutionException;
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
import java.util.Optional;
import java.util.UUID;

@Controller
public class PlanGroundTransferController {

    private final GroundTransferPlanning groundTransferPlanning;
    private final GroundTransferEndpointOptions endpointOptions;
    private final ScheduleGapProjector scheduleGapProjector;
    private final Clock clock;

    public PlanGroundTransferController(GroundTransferPlanning groundTransferPlanning,
                                        GroundTransferEndpointOptions endpointOptions,
                                        ScheduleGapProjector scheduleGapProjector,
                                        Clock clock) {
        this.groundTransferPlanning = groundTransferPlanning;
        this.endpointOptions = endpointOptions;
        this.scheduleGapProjector = scheduleGapProjector;
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

    /**
     * {@code ?date=} from the calendar day-menu seeds the day (D9); it never filters the endpoint
     * options (D10). {@code ?problem=} comes from a "Ground transfer" fix link on
     * {@code /schedule-problems} and does more: the gap it names says which two places this hop is
     * between, so the selects can open already on them (D16, Ted 2026-08-21). Both are optional and
     * both defaults are unchanged, so the index nav card and the day-menu link behave as before.
     * <p>
     * {@code endpointChoices} is taken as a parameter rather than rebuilt: it is the very list the
     * page is about to render, so a preselected token is guaranteed to be an option that is there.
     */
    @GetMapping("/plan-ground-transfer")
    public String planGroundTransferForm(Model model,
                                         @ModelAttribute("endpointChoices") GroundTransferEndpointChoices endpointChoices,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(required = false) String problem) {
        PlanGroundTransferRequest request = new PlanGroundTransferRequest();
        request.setGroundTransferId(UUID.randomUUID().toString());
        // Absent a date, the default is today: a transfer is normally added to a trip already under
        // way, which is exactly why it has no future-date rule.
        request.setDate(date != null ? date : LocalDate.now(clock));
        request.setDepartureTime(LocalTime.of(12, 0));
        request.setArrivalTime(LocalTime.of(12, 45));
        // now is captured here at the boundary; the gap is read from the same report the banner
        // above the form is read from, so the two cannot describe different problems.
        gapNamedBy(problem, clock.instant())
                .ifPresent(gap -> new GroundTransferPreselection(endpointChoices, gap).applyTo(request));
        model.addAttribute("planGroundTransfer", request);
        return "plan-ground-transfer";
    }

    /**
     * The missing-travel gap {@code problem} names, if it names one that is still open. Any other
     * kind of problem, and a key matching nothing at all, preselects nothing — the same silence the
     * banner keeps.
     */
    private Optional<ScheduleProblem.MissingTravel> gapNamedBy(String problem, Instant now) {
        if (problem == null || problem.isBlank()) {
            return Optional.empty();
        }
        return new ProblemRef(problem).findIn(scheduleGapProjector.problems(now))
                .filter(ScheduleProblem.MissingTravel.class::isInstance)
                .map(ScheduleProblem.MissingTravel.class::cast);
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
