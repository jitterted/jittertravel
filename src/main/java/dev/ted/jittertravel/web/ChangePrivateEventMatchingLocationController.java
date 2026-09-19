package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangePrivateEventMatchingLocation;
import dev.ted.jittertravel.application.PrivateEventMatchingLocationView;
import dev.ted.jittertravel.application.PrivateEventMatchingLocationViewProjector;
import dev.ted.jittertravel.domain.InvalidMatchingLocation;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;
import java.util.UUID;

/**
 * Changes the place the schedule reasons about one private event in: GET renders the form, POST
 * applies it.
 * <p>
 * This is a <em>decision-support</em> page, not a recording one (CLAUDE.md, "A recording surface
 * needs no decision-support information"), so it carries the context the choice needs — which
 * evening, where the venue actually is, and what the schedule currently counts it as. That is why
 * it reads {@link PrivateEventMatchingLocationView} rather than the cancel page's
 * {@code PrivateEventDetailsView}.
 * <p>
 * No typed confirmation word and no red: appending a correction destroys nothing, and the undo is
 * typing the old value back in (CLAUDE.md, "Destructive actions: red, and gated behind a typed
 * word" — this is neither).
 */
@Controller
public class ChangePrivateEventMatchingLocationController {

    private final ChangePrivateEventMatchingLocation applicationService;
    private final PrivateEventMatchingLocationViewProjector viewProjector;

    public ChangePrivateEventMatchingLocationController(
            ChangePrivateEventMatchingLocation applicationService,
            PrivateEventMatchingLocationViewProjector viewProjector) {
        this.applicationService = applicationService;
        this.viewProjector = viewProjector;
    }

    @GetMapping("/planned-private-events/{privateEventId}/matching-location")
    public String matchingLocationForm(@PathVariable("privateEventId") String privateEventIdString,
                                       Model model) {
        Optional<PrivateEventMatchingLocationView> maybe = lookup(privateEventIdString);
        if (maybe.isEmpty()) {
            // A stale link for an evening that has since been cancelled. The list is a j2html view
            // that cannot render a flash, so navigate there silently rather than attach a dropped
            // message — the same call CancelPrivateEventController makes.
            return "redirect:/planned-private-events";
        }
        PrivateEventMatchingLocationView view = maybe.get();

        // Prefilled with the value already in force, not the one originally typed: the form must
        // not quietly undo an earlier correction when Ted submits it again.
        ChangePrivateEventMatchingLocationRequest request =
                new ChangePrivateEventMatchingLocationRequest(view.locationForMatching());

        model.addAttribute("privateEvent", view);
        model.addAttribute("matchingLocation", request);
        return "change-private-event-matching-location";
    }

    @PostMapping("/planned-private-events/{privateEventId}/matching-location")
    public String changeMatchingLocation(
            @PathVariable("privateEventId") String privateEventIdString,
            @ModelAttribute("matchingLocation") ChangePrivateEventMatchingLocationRequest request,
            BindingResult bindingResult,
            Model model) {
        Optional<PrivateEventMatchingLocationView> maybe = lookup(privateEventIdString);
        if (maybe.isEmpty()) {
            return "redirect:/planned-private-events";
        }
        PrivateEventMatchingLocationView view = maybe.get();

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            // The evening comes from the path, never from the form: the request has no id
            // component, so there is nothing on the page a crafted POST could re-target.
            applicationService.changeMatchingLocation(UUID.randomUUID(),
                                                      view.privateEventId().id(), request);
        } catch (InvalidMatchingLocation e) {
            // Field-level, under the input that fixes it — the error goes where the fix is, not
            // where the failure was raised (CLAUDE.md, "A rejected form reports everything it can
            // see, under the input that fixes each thing"). One field, so no count banner.
            bindingResult.rejectValue("locationForMatching", "blank", e.getMessage());
            model.addAttribute("privateEvent", view);
            return "change-private-event-matching-location";
        } catch (PrivateEventNotFound e) {
            // Cancelled in another tab between the lookup above and the write: there is no evening
            // left to re-match, and nothing to tell the list either.
            return "redirect:/planned-private-events";
        }

        return "redirect:/planned-private-events";
    }

    private Optional<PrivateEventMatchingLocationView> lookup(String privateEventIdString) {
        try {
            return viewProjector.findById(PrivateEventId.of(UUID.fromString(privateEventIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
