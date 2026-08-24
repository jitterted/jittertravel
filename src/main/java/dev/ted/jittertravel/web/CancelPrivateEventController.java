package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelPrivateEvent;
import dev.ted.jittertravel.application.PrivateEventDetailsView;
import dev.ted.jittertravel.application.PrivateEventDetailsViewProjector;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a planned private social event on its own page: GET renders the confirmation, POST
 * performs it. Mirrors {@link CancelGroundTransferController} — a dedicated page is what gives the
 * confirmation somewhere to say what cancelling does, and a POST is not reachable by a stray click.
 * <p>
 * A plain confirm, with no typed word: nothing stored is destroyed by appending a cancellation, so
 * the typed word belongs to what cannot be undone from inside the app. The button is amber for the
 * same reason — planning the evening again puts it back, which is true of the future events Ted
 * actually cancels (see {@code CancelPrivateEventCommand} on the past-event case).
 * <p>
 * Unlike a ground transfer, this one collects an optional <em>reason</em>: "rescheduled to Friday"
 * is worth having and nothing keys off it.
 */
@Controller
public class CancelPrivateEventController {

    private final CancelPrivateEvent applicationService;
    private final PrivateEventDetailsViewProjector detailsProjector;

    public CancelPrivateEventController(CancelPrivateEvent applicationService,
                                        PrivateEventDetailsViewProjector detailsProjector) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
    }

    @GetMapping("/planned-private-events/{privateEventId}/cancel")
    public String cancelPrivateEventForm(@PathVariable("privateEventId") String privateEventIdString,
                                         Model model) {
        Optional<PrivateEventDetailsView> maybe = lookup(privateEventIdString);
        if (maybe.isEmpty()) {
            // A stale link for an event that is already gone: the itinerary is a j2html view that
            // cannot render a flash, so navigate there silently rather than attach a dropped message.
            return "redirect:/itinerary";
        }
        model.addAttribute("privateEvent", maybe.get());
        model.addAttribute("reason", "");
        return "cancel-private-event";
    }

    @PostMapping("/planned-private-events/{privateEventId}/cancel")
    public String cancelPrivateEvent(@PathVariable("privateEventId") String privateEventIdString,
                                     @RequestParam(value = "reason", required = false) String reason) {
        Optional<PrivateEventDetailsView> maybe = lookup(privateEventIdString);
        if (maybe.isEmpty()) {
            return "redirect:/itinerary";
        }
        // The day the event was on, captured before it is cancelled: landing back on the itinerary
        // day it left is what shows the evening is gone from that day.
        String day = maybe.get().startsAt().toLocalDate().toString();

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            applicationService.cancelPrivateEvent(UUID.randomUUID(),
                    new CancelPrivateEventRequest(maybe.get().privateEventId().id(), reason));
        } catch (PrivateEventNotFound e) {
            // Already cancelled in another tab: there is nothing left to cancel, and nothing to
            // tell the itinerary either.
            return "redirect:/itinerary";
        }

        return "redirect:/itinerary?date=" + day;
    }

    private Optional<PrivateEventDetailsView> lookup(String privateEventIdString) {
        try {
            return detailsProjector.findById(PrivateEventId.of(UUID.fromString(privateEventIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
