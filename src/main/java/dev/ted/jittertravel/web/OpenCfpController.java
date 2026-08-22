package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.OpenCfp;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a conference's CFP closing deadline on its own page: GET renders the form, POST performs
 * it. Reached by a "CFP" link on {@code /conferences}.
 * <p>
 * Its own controller rather than a branch of {@link PlanConferenceController} — one slice per
 * controller — and modelled on {@link DeclineConferenceController} down to its error paths: a
 * missing or malformed conference navigates back to the view-only list silently, because that list
 * cannot render a flash.
 * <p>
 * <strong>The zone is decided here, at the boundary.</strong> A CFP deadline is stored in the
 * conference's own venue zone, taken from the dates {@code ConferencePlanned} already resolved,
 * rather than resolved a second time from the address — one conference, one zone, and a second
 * resolution could only disagree with the first.
 */
@Controller
public class OpenCfpController {

    private static final Logger log = LoggerFactory.getLogger(OpenCfpController.class);

    private final OpenCfp applicationService;
    private final ConferenceProjector projector;

    public OpenCfpController(OpenCfp applicationService, ConferenceProjector projector) {
        this.applicationService = applicationService;
        this.projector = projector;
    }

    @GetMapping("/conferences/{conferenceId}/cfp")
    public String openCfpForm(@PathVariable("conferenceId") String conferenceIdString, Model model) {
        Optional<ConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return "redirect:/conferences";
        }
        ConferenceView conference = maybe.get();
        model.addAttribute("conference", conference);
        // Prefilled with any deadline already recorded, so re-recording a moved deadline starts
        // from the old one rather than from a blank field.
        model.addAttribute("closesOn", conference.cfpClosesOn() == null
                ? null
                : conference.cfpClosesOn().localDateTime());
        return "open-cfp";
    }

    @PostMapping("/conferences/{conferenceId}/cfp")
    public String openCfp(@PathVariable("conferenceId") String conferenceIdString,
                          @RequestParam("closesOn")
                          @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime closesOn) {
        Optional<ConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return "redirect:/conferences";
        }
        ConferenceView conference = maybe.get();

        try {
            // commandId is the nondeterministic input, captured here at the boundary; the deadline's
            // zone comes from the conference's own dates rather than the clock or the resolver.
            applicationService.openCfp(UUID.randomUUID(),
                    new OpenCfpRequest(conference.conferenceId().id(), closesOn),
                    ZonedTimestamp.fromLocal(closesOn, conference.startDate().zone()));
        } catch (ConferenceNotFound e) {
            // Cancelled or declined in another tab between the lookup above and the write.
            return "redirect:/conferences";
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to record a CFP deadline while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/conferences";
    }

    private Optional<ConferenceView> lookup(String conferenceIdString) {
        try {
            return projector.findById(ConferenceId.of(UUID.fromString(conferenceIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
