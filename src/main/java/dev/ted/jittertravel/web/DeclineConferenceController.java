package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.DeclineConference;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Declines a planned conference — records Ted's decision not to attend — on its own dedicated page:
 * GET renders the confirmation, POST performs it. Reached by a "Decline" link on
 * {@code /tentative-conferences}.
 * <p>
 * Its own controller rather than a branch of {@link PlanConferenceController}: one slice per
 * controller, and declining has nothing to do with the plan form's binding and validation. Modelled
 * on {@link CancelHotelController}.
 */
@Controller
public class DeclineConferenceController {

    private static final Logger log = LoggerFactory.getLogger(DeclineConferenceController.class);

    private final DeclineConference applicationService;
    private final TentativeConferenceProjector projector;
    private final Clock clock;

    public DeclineConferenceController(DeclineConference applicationService,
                                       TentativeConferenceProjector projector,
                                       Clock clock) {
        this.applicationService = applicationService;
        this.projector = projector;
        this.clock = clock;
    }

    @GetMapping("/tentative-conferences/{conferenceId}/decline")
    public String declineConferenceForm(@PathVariable("conferenceId") String conferenceIdString,
                                        Model model) {
        Optional<TentativeConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            // Stale decline link for a conference that's already gone: the view-only list can't
            // render a flash, so navigate there silently rather than attach a message that gets
            // dropped.
            return "redirect:/tentative-conferences";
        }
        model.addAttribute("conference", maybe.get());
        model.addAttribute("reason", "");
        return "decline-conference";
    }

    @PostMapping("/tentative-conferences/{conferenceId}/decline")
    public String declineConference(@PathVariable("conferenceId") String conferenceIdString,
                                    @RequestParam(value = "reason", required = false) String reason) {
        UUID conferenceId;
        try {
            conferenceId = UUID.fromString(conferenceIdString);
        } catch (IllegalArgumentException malformedUuid) {
            // Malformed id in the path: nothing to decline, and the view-only list can't render a
            // flash, so navigate there silently.
            return "redirect:/tentative-conferences";
        }

        try {
            // The commandId and declinedOn — the nondeterministic inputs — are captured here at the
            // boundary.
            applicationService.declineConference(UUID.randomUUID(),
                    new DeclineConferenceRequest(conferenceId, reason), Instant.now(clock));
        } catch (ConferenceNotFound e) {
            // The conference is already gone (declined or cancelled in another tab); there is nothing
            // left to decline, and the view-only list can't render a flash, so fall back to it
            // silently.
            return "redirect:/tentative-conferences";
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to decline conference while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/tentative-conferences";
    }

    private Optional<TentativeConferenceView> lookup(String conferenceIdString) {
        try {
            return projector.findById(ConferenceId.of(UUID.fromString(conferenceIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
