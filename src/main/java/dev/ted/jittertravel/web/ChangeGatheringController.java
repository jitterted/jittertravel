package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeGathering;
import dev.ted.jittertravel.application.GatheringDetailsView;
import dev.ted.jittertravel.application.GatheringDetailsViewProjector;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
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

import java.time.Clock;
import java.time.Instant;
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

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/planned-gatherings/{gatheringId}")
    public String changeGatheringForm(@PathVariable("gatheringId") String gatheringIdString,
                                      Model model) {
        Optional<GatheringDetailsView> maybe = lookup(gatheringIdString);
        if (maybe.isEmpty()) {
            // Stale edit link for a gathering that's already gone: the view-only list can't render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/planned-gatherings";
        }

        model.addAttribute("changeGathering", toRequest(maybe.get()));
        return "change-gathering";
    }

    @PostMapping("/planned-gatherings/{gatheringId}")
    public String changeGatheringSubmit(@PathVariable("gatheringId") String gatheringIdString,
                                        @ModelAttribute("changeGathering") ChangeGatheringRequest command,
                                        BindingResult bindingResult) {
        // Path is the source of truth for gatheringId; it is not user-editable.
        command.setGatheringId(gatheringIdString);

        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.changeGathering(UUID.randomUUID(), command, Instant.now(clock));
        } catch (GatheringNotFound e) {
            // The gathering vanished between GET and POST (e.g. removed in another tab). Report it on
            // the form itself — never by redirecting to the view-only list, which drops the flash.
            bindingResult.reject("notFound", e.getMessage());
        } catch (GatheringDateNotInFuture e) {
            bindingResult.rejectValue("date", "future", e.getMessage());
        } catch (InvalidGatheringTimeRange e) {
            bindingResult.rejectValue("endTime", "afterStartTime", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
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
        // Prefill from the venue-zone wall-clock, so re-opening the form shows the time that was
        // entered rather than one shifted into the server's zone. The zone picker is left on
        // "derive from location" (as the hotel edit form does); an explicit pick is only needed
        // again if the location still doesn't resolve.
        request.setDate(view.startsAt().localDateTime().toLocalDate());
        request.setStartTime(view.startsAt().localDateTime().toLocalTime());
        request.setEndTime(view.endsAt().localDateTime().toLocalTime());
        request.setSpeaking(view.speaking());
        request.setInfoUrl(view.infoUrl());
        return request;
    }
}
