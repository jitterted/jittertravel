package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.DateRangeNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class PlanConferenceController {

    private static final Logger log = LoggerFactory.getLogger(PlanConferenceController.class);
    private final ConferencePlanning applicationService;
    private final TentativeConferenceProjector projector;
    private final Clock clock;

    public PlanConferenceController(ConferencePlanning applicationService,
                                    TentativeConferenceProjector projector,
                                    Clock clock) {
        this.applicationService = applicationService;
        this.projector = projector;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @ModelAttribute("conferenceFormats")
    public ConferenceFormat[] conferenceFormats() {
        return ConferenceFormat.values();
    }

    @GetMapping("/plan-conference")
    public String planConferenceForm(Model model,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the start day; the default (one week out)
        // stands when absent so the index nav card is unaffected.
        LocalDate day = date != null ? date : LocalDate.now(clock).plusWeeks(1);
        LocalDateTime startDateTime = day.atStartOfDay().plusHours(9);
        request.setStartDate(startDateTime);
        request.setEndDate(startDateTime.plusDays(2).plusHours(8));

        model.addAttribute("planTentativeConference", request);
        return "plan-conference";
    }

    @PostMapping("/plan-conference")
    // The name must match the GET's model attribute and the template's th:object: without it Spring
    // derives "planTentativeConferenceRequest" from the type, and every re-render after a rejected
    // field throws instead of showing the error.
    public String planConferenceSubmit(@ModelAttribute("planTentativeConference")
                                       PlanTentativeConferenceRequest command,
                                       BindingResult bindingResult) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        try {
            // now is captured at the boundary as an Instant; the zone is resolved inward.
            applicationService.planConference(command, Instant.now(clock));
        } catch (DateRangeNotInFuture e) {
            bindingResult.rejectValue("startDate", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
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
    public ResponseEntity<String> tentativeConferences(
            @RequestParam(required = false) String filter) {
        TimeView timeView = TimeView.fromParam(filter);
        Instant now = Instant.now(clock);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(TentativeConferencesRenderer.render(projector.views(timeView, now), timeView));
    }

}
