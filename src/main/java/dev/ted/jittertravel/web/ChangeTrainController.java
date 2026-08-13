package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeTrain;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainTripId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ChangeTrainController {

    private final ChangeTrain applicationService;
    private final TrainDetailsViewProjector detailsProjector;
    private final Clock clock;

    public ChangeTrainController(ChangeTrain applicationService,
                                 TrainDetailsViewProjector detailsProjector,
                                 Clock clock) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/booked-trains/{tripId}")
    public String changeTrainForm(@PathVariable("tripId") String tripIdString,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Optional<TrainDetailsView> maybe = lookup(tripIdString);
        if (maybe.isEmpty()) {
            redirectAttributes.addFlashAttribute("notFoundMessage",
                    "No booked train found with id " + tripIdString);
            return "redirect:/booked-trains";
        }

        model.addAttribute("changeTrain", toRequest(maybe.get()));
        return "change-train";
    }

    @PostMapping("/booked-trains/{tripId}")
    public String changeTrainSubmit(@PathVariable("tripId") String tripIdString,
                                    @ModelAttribute("changeTrain") ChangeTrainRequest command,
                                    BindingResult bindingResult) {
        // Path is the source of truth for tripId; it is not user-editable.
        command.setTrainTripId(tripIdString);

        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.changeTrain(UUID.randomUUID(), command, Instant.now(clock));
        } catch (TrainNotFound e) {
            // The trip vanished between GET and POST (e.g. removed in another tab). Report it on the
            // form itself — never by redirecting to the view-only list, which drops the flash.
            bindingResult.reject("notFound", e.getMessage());
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.reject("zoneUnresolved",
                    "Could not determine the time zone for a station from its location — "
                            + "please choose the zone(s) below.");
        }

        if (bindingResult.hasErrors()) {
            return "change-train";
        }

        return "redirect:/booked-trains";
    }

    private Optional<TrainDetailsView> lookup(String tripIdString) {
        try {
            return detailsProjector.findById(TrainTripId.of(UUID.fromString(tripIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    private static ChangeTrainRequest toRequest(TrainDetailsView view) {
        ChangeTrainRequest request = new ChangeTrainRequest();
        request.setTrainTripId(view.tripId().id().toString());
        request.setServiceId(view.serviceId());
        request.setDepartureStationName(view.departureStation().name());
        request.setDepartureCityName(view.departureStation().city());
        request.setDepartureCountry(view.departureStation().country());
        request.setDepartureMapsUrl(view.departureStation().mapsUrl());
        request.setDepartureDateTime(view.departureDateTime());
        request.setArrivalStationName(view.arrivalStation().name());
        request.setArrivalCityName(view.arrivalStation().city());
        request.setArrivalCountry(view.arrivalStation().country());
        request.setArrivalMapsUrl(view.arrivalStation().mapsUrl());
        request.setArrivalDateTime(view.arrivalDateTime());
        return request;
    }
}