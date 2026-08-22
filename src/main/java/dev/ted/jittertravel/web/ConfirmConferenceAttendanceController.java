package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.ConfirmConferenceAttendance;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.AttendanceBasis;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Confirms that Ted is going to a conference — the commitment half of the pair whose other half is
 * {@link DeclineConferenceController} — on its own dedicated page: GET renders the form, POST
 * performs it. Reached by a "Confirm" link on {@code /conferences}, which appears only on
 * conferences that are still merely watched.
 * <p>
 * Its own controller rather than a branch of {@link PlanConferenceController}: one slice per
 * controller, and confirming has nothing to do with the plan form's binding and validation.
 * <p>
 * A missing or unrecognized basis re-renders <em>this</em> page with the error rather than
 * redirecting to the view-only {@code /conferences} list, which cannot render a flash.
 */
@Controller
public class ConfirmConferenceAttendanceController {

    private static final Logger log = LoggerFactory.getLogger(ConfirmConferenceAttendanceController.class);

    private final ConfirmConferenceAttendance applicationService;
    private final ConferenceProjector projector;
    private final Clock clock;

    public ConfirmConferenceAttendanceController(ConfirmConferenceAttendance applicationService,
                                                 ConferenceProjector projector,
                                                 Clock clock) {
        this.applicationService = applicationService;
        this.projector = projector;
        this.clock = clock;
    }

    /**
     * {@code ?basis=} arrives from the dashboard's row actions, which know why Ted is going before
     * he gets here — "Ticket Bought" and "Invitation Accepted" are two different reasons reaching
     * the same page. The radio opens already selected, so the click on this page is a confirmation
     * rather than the same decision asked twice. Bare (or with a value that is not a basis), the
     * page asks as it always did.
     */
    @GetMapping("/conferences/{conferenceId}/confirm")
    public String confirmAttendanceForm(@PathVariable("conferenceId") String conferenceIdString,
                                        @RequestParam(value = "basis", required = false) String basisParam,
                                        Model model) {
        Optional<ConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            // Stale confirm link for a conference that's already gone: the view-only list can't
            // render a flash, so navigate there silently rather than attach a message that gets
            // dropped.
            return "redirect:/conferences";
        }
        model.addAttribute("conference", maybe.get());
        model.addAttribute("chosen", parseBasis(basisParam).orElse(null));
        return "confirm-conference-attendance";
    }

    @PostMapping("/conferences/{conferenceId}/confirm")
    public String confirmAttendance(@PathVariable("conferenceId") String conferenceIdString,
                                    @RequestParam(value = "basis", required = false) String basisParam,
                                    Model model) {
        Optional<AttendanceBasis> basis = parseBasis(basisParam);
        if (basis.isEmpty()) {
            Optional<ConferenceView> maybe = lookup(conferenceIdString);
            if (maybe.isEmpty()) {
                return "redirect:/conferences";
            }
            // The error belongs on the page hosting the form — /conferences cannot show it.
            model.addAttribute("conference", maybe.get());
            model.addAttribute("chosen", null);
            model.addAttribute("error", "Choose why you are going.");
            return "confirm-conference-attendance";
        }

        UUID conferenceId;
        try {
            conferenceId = UUID.fromString(conferenceIdString);
        } catch (IllegalArgumentException malformedUuid) {
            // Malformed id in the path: nothing to confirm, and the view-only list can't render a
            // flash, so navigate there silently.
            return "redirect:/conferences";
        }

        try {
            // The commandId and confirmedOn — the nondeterministic inputs — are captured here at
            // the boundary.
            applicationService.confirmAttendance(UUID.randomUUID(),
                    new ConfirmConferenceAttendanceRequest(conferenceId, basis.get()),
                    Instant.now(clock));
        } catch (ConferenceNotFound e) {
            // The conference is already gone (declined or cancelled in another tab); there is
            // nothing left to confirm, and the view-only list can't render a flash, so fall back to
            // it silently.
            return "redirect:/conferences";
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to confirm conference attendance while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/conferences";
    }

    private Optional<AttendanceBasis> parseBasis(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AttendanceBasis.valueOf(value.trim().toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException unknownValue) {
            return Optional.empty();
        }
    }

    private Optional<ConferenceView> lookup(String conferenceIdString) {
        try {
            return projector.findById(ConferenceId.of(UUID.fromString(conferenceIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
