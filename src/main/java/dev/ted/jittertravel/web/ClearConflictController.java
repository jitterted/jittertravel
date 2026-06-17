package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@Controller
public class ClearConflictController {

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
        model.addAttribute("clearConflictRequest", request);
        model.addAttribute("gatheringName", gatheringName);
        model.addAttribute("gatheringCity", gatheringCity);
        model.addAttribute("conferenceName", conferenceName);
        model.addAttribute("conferenceCity", conferenceCity);
        model.addAttribute("date", LocalDate.parse(date));
        return "clear-conflict";
    }

    @PostMapping("/clear-conflict")
    public String clearConflictSubmit(@ModelAttribute ClearConflictRequest request) {
        gatheringPlanning.clearConflict(
                GatheringId.of(UUID.fromString(request.getGatheringId())),
                ConferenceId.of(UUID.fromString(request.getConferenceId())),
                request.getReason() != null ? request.getReason() : "",
                UUID.randomUUID());
        return "redirect:/schedule-problems";
    }
}
