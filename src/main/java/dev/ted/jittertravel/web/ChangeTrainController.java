package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeTrain;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainTripId;
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
                                  Model model) {
        Optional<TrainDetailsView> maybe = lookup(tripIdString);
        if (maybe.isEmpty()) {
            // Stale edit link for a train that's already gone: the view-only list can't render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/booked-trains";
        }

        model.addAttribute("changeTrain", toRequest(maybe.get()));
        return "change-train";
    }

    @PostMapping("/booked-trains/{tripId}")
    public String changeTrainSubmit(@PathVariable("tripId") String tripIdString,
                                    @ModelAttribute("changeTrain") ChangeTrainRequest command,
                                    BindingResult bindingResult,
                                    Model model) {
        TrainFormErrors errors = new TrainFormErrors(bindingResult);
        // Binding already failed: a date left blank, or one that would not parse. Those values are
        // null on the request, so calling the service would only reach the write path with them.
        if (bindingResult.hasErrors()) {
            errors.summarize();
            return "change-train";
        }
        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            // The trip comes from the path, never from the form: the request has no id component,
            // so there is nothing on the page a crafted POST could re-target.
            applicationService.changeTrain(UUID.randomUUID(), tripIdString, command,
                                           Instant.now(clock));
        } catch (TrainNotFound e) {
            // The trip vanished between GET and POST (e.g. removed in another tab). Report it on the
            // form itself — never by redirecting to the view-only list, which drops the flash.
            bindingResult.reject("notFound", e.getMessage());
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (OverlappingLegRefused e) {
            // On the departure time, because changing these times is the fix this form offers; the
            // other way out — dealing with the leg already booked — is the link beside it.
            bindingResult.rejectValue("departureDateTime", "overlapping",
                    OverlappingLegNotice.from(e).message());
            model.addAttribute("overlappingLeg", OverlappingLegNotice.from(e));
        } catch (InvalidTrainEntry e) {
            errors.reject(e);
        }
        errors.summarize();

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
        // The form is a datetime-local: it reads a wall clock and nothing else, so the zone the view
        // carries is narrowed away here rather than in the read model.
        return new ChangeTrainRequest(
                view.serviceId(),
                view.departureStation().name(), view.departureStation().city(),
                view.departureStation().country(), view.departureStation().mapsUrl(), null,
                view.departureDateTime().localDateTime(),
                view.arrivalStation().name(), view.arrivalStation().city(),
                view.arrivalStation().country(), view.arrivalStation().mapsUrl(), null,
                view.arrivalDateTime().localDateTime());
    }

    /**
     * The trip being changed, for the form's own action URL — path data, so it reaches the template
     * as its own model attribute rather than as a hidden input a crafted POST could re-target.
     * Declared here so it is present on the GET and on every re-render path of the POST.
     */
    @ModelAttribute("tripId")
    String tripId(@PathVariable(value = "tripId", required = false) String tripId) {
        return tripId;
    }
}