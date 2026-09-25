package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ChangeConferenceDates;
import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Records that a conference's organizers moved it: GET renders the form, POST applies it.
 * <p>
 * Reached from the pencil in the detail page's When block, and returns there. OWNER-only through
 * its own {@code /conferences/*}{@code /dates} matcher in {@code SecurityConfig} — the detail page's
 * {@code /conferences/*} matches one segment only, so without it this form would be public.
 * <p>
 * No typed confirmation word and no red: appending a correction destroys nothing, and the undo is
 * typing the old dates back in (CLAUDE.md, "Destructive actions").
 */
@Controller
public class ChangeConferenceDatesController {

    private static final Logger log = LoggerFactory.getLogger(ChangeConferenceDatesController.class);

    private final ChangeConferenceDates changeConferenceDates;
    private final ConferenceProjector projector;

    public ChangeConferenceDatesController(ChangeConferenceDates changeConferenceDates,
                                           ConferenceProjector projector) {
        this.changeConferenceDates = changeConferenceDates;
        this.projector = projector;
    }

    @GetMapping("/conferences/{conferenceId}/dates")
    public String changeDatesForm(@PathVariable("conferenceId") String conferenceIdString, Model model) {
        Optional<ConferenceDetailView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return "redirect:/conferences";
        }
        ConferenceDetailView conference = maybe.get();
        if (dropped(conference)) {
            return "redirect:/conferences/" + conference.conferenceId().id();
        }
        // Prefilled with the dates in force — a later move starts from the last one, not the plan.
        model.addAttribute("conference", conference);
        model.addAttribute("changeDates", new ChangeConferenceDatesRequest(
                conference.startDate().localDateTime(), conference.endDate().localDateTime()));
        return "change-conference-dates";
    }

    @PostMapping("/conferences/{conferenceId}/dates")
    public String changeDates(@PathVariable("conferenceId") String conferenceIdString,
                              @ModelAttribute("changeDates") ChangeConferenceDatesRequest request,
                              BindingResult bindingResult,
                              Model model) {
        Optional<ConferenceDetailView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return "redirect:/conferences";
        }
        ConferenceDetailView conference = maybe.get();
        if (dropped(conference)) {
            return "redirect:/conferences/" + conference.conferenceId().id();
        }
        model.addAttribute("conference", conference);

        // A date left blank, or one that would not parse, is null on the request.
        if (bindingResult.hasErrors()) {
            return "change-conference-dates";
        }

        try {
            // commandId is the nondeterministic input, captured here at the boundary. The
            // conference comes from the path, and the zone from the stream — neither is on the form.
            changeConferenceDates.changeDates(UUID.randomUUID(), conference.conferenceId().id(), request);
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("endDate", "afterStartDate", e.getMessage());
            return "change-conference-dates";
        } catch (ConferenceNotFound e) {
            // Cancelled or declined in another tab between the lookup above and the write. Said on
            // the form, with the typed dates still in it: a redirect here reads as a save.
            bindingResult.reject("conferenceGone", "Not saved: this conference was cancelled or declined");
            return "change-conference-dates";
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to change a conference's dates while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/conferences/" + conference.conferenceId().id();
    }

    /**
     * The same test the detail page uses to leave its pencil off: a conference Ted declined, or one
     * a rejection dropped, is not offered this form. Stricter than the write path, which refuses
     * only a declined one — a stale tab or a bookmark must not reach an action the page withholds.
     * The detail page is where it goes instead, because that page says why.
     */
    private static boolean dropped(ConferenceDetailView conference) {
        return conference.commitment() == AttendanceCommitment.NOT_GOING;
    }

    private Optional<ConferenceDetailView> lookup(String conferenceIdString) {
        ConferenceId conferenceId;
        try {
            conferenceId = ConferenceId.of(UUID.fromString(conferenceIdString));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
        return projector.detailById(conferenceId);
    }
}
