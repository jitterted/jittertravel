package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeGathering;
import dev.ted.jittertravel.application.GatheringDetailsView;
import dev.ted.jittertravel.application.GatheringDetailsViewProjector;
import dev.ted.jittertravel.domain.GatheringDateNotInFuture;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringNotFound;
import dev.ted.jittertravel.domain.InvalidGatheringTimeRange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ChangeGatheringController {

    private final ChangeGathering applicationService;
    private final GatheringDetailsViewProjector detailsProjector;
    private final Clock clock;

    public ChangeGatheringController(ChangeGathering applicationService,
                                     GatheringDetailsViewProjector detailsProjector,
                                     Clock clock) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    @GetMapping("/planned-gatherings/{gatheringId}")
    public String changeGatheringForm(@PathVariable("gatheringId") String gatheringIdString,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        Optional<GatheringDetailsView> maybe = lookup(gatheringIdString);
        if (maybe.isEmpty()) {
            redirectAttributes.addFlashAttribute("notFoundMessage",
                    "No planned gathering found with id " + gatheringIdString);
            return "redirect:/planned-gatherings";
        }

        model.addAttribute("changeGathering", toRequest(maybe.get()));
        return "change-gathering";
    }

    @PostMapping("/planned-gatherings/{gatheringId}")
    public String changeGatheringSubmit(@PathVariable("gatheringId") String gatheringIdString,
                                        @ModelAttribute("changeGathering") ChangeGatheringRequest command,
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes) {
        // Path is the source of truth for gatheringId; it is not user-editable.
        command.setGatheringId(gatheringIdString);

        try {
            // Nondeterministic inputs (commandId, today) are captured here at the boundary.
            applicationService.changeGathering(UUID.randomUUID(), command, LocalDate.now(clock));
        } catch (GatheringNotFound e) {
            redirectAttributes.addFlashAttribute("notFoundMessage", e.getMessage());
            return "redirect:/planned-gatherings";
        } catch (GatheringDateNotInFuture e) {
            bindingResult.rejectValue("date", "future", e.getMessage());
        } catch (InvalidGatheringTimeRange e) {
            bindingResult.rejectValue("endTime", "afterStartTime", e.getMessage());
        }

        if (bindingResult.hasErrors()) {
            return "change-gathering";
        }

        return "redirect:/planned-gatherings";
    }

    private Optional<GatheringDetailsView> lookup(String gatheringIdString) {
        try {
            return detailsProjector.findById(GatheringId.of(UUID.fromString(gatheringIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    private static ChangeGatheringRequest toRequest(GatheringDetailsView view) {
        ChangeGatheringRequest request = new ChangeGatheringRequest();
        request.setGatheringId(view.gatheringId().id().toString());
        request.setTitle(view.title());
        request.setVenueName(view.venueName());
        request.setStreet(view.location().street());
        request.setCity(view.location().city());
        request.setRegion(view.location().region());
        request.setPostalCode(view.location().postalCode());
        request.setCountry(view.location().country());
        request.setLocationForMatching(view.location().locationForMatching());
        request.setDate(view.date());
        request.setStartTime(view.startTime());
        request.setEndTime(view.endTime());
        request.setSpeaking(view.speaking());
        request.setInfoUrl(view.infoUrl());
        return request;
    }
}
