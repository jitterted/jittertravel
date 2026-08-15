package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@Controller
public class ClearConflictController {

    private static final Logger log = LoggerFactory.getLogger(ClearConflictController.class);

    private final GatheringPlanning gatheringPlanning;

    public ClearConflictController(GatheringPlanning gatheringPlanning) {
        this.gatheringPlanning = gatheringPlanning;
    }

    @GetMapping("/clear-conflict")
    public String clearConflictForm(
            @RequestParam UUID gatheringId,
            @RequestParam UUID conferenceId,
            @RequestParam String gatheringName,
            @RequestParam String gatheringCity,
            @RequestParam String conferenceName,
            @RequestParam String conferenceCity,
            @RequestParam String date,
            Model model) {
        ClearConflictRequest request = new ClearConflictRequest();
        request.setGatheringId(gatheringId.toString());
        request.setConferenceId(conferenceId.toString());
        request.setGatheringName(gatheringName);
        request.setGatheringCity(gatheringCity);
        request.setConferenceName(conferenceName);
        request.setConferenceCity(conferenceCity);
        request.setDate(LocalDate.parse(date));
        model.addAttribute("clearConflictRequest", request);
        return "clear-conflict";
    }

    // The name must match the GET's model attribute and the template's th:object: without it Spring
    // derives the same name from the type here, but naming it keeps the two ends explicitly tied,
    // so a re-render after a rejected submit finds its object instead of throwing.
    @PostMapping("/clear-conflict")
    public String clearConflictSubmit(@ModelAttribute("clearConflictRequest") ClearConflictRequest request,
                                      BindingResult bindingResult) {
        try {
            // commandId is captured here at the boundary; the service generates no UUIDs of its own.
            gatheringPlanning.clearConflict(
                    GatheringId.of(UUID.fromString(request.getGatheringId())),
                    ConferenceId.of(UUID.fromString(request.getConferenceId())),
                    request.getReason() != null ? request.getReason() : "",
                    UUID.randomUUID());
        } catch (IllegalArgumentException malformedId) {
            // The hidden ids came back unparseable (hand-edited URL, truncated form). Report it on
            // the form itself — never by redirecting to the view-only /schedule-problems page,
            // which has nowhere to render a message.
            bindingResult.reject("malformedId",
                    "This conflict could not be identified — please reopen it from Schedule Problems.");
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to clear a city conflict while in read-only mode", e);
            return "redirect:/read-only";
        } catch (RuntimeException e) {
            // Anything else (the append failed, the database went away) still has to land on the
            // form rather than as a blank 500.
            log.error("Failed to clear city conflict", e);
            bindingResult.reject("clearFailed",
                    "Could not clear this conflict: " + e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "clear-conflict";
        }

        return "redirect:/schedule-problems";
    }
}
