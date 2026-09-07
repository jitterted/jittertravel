package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelTrain;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainTripId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a booked train trip on its own page: GET renders the confirmation, POST performs it.
 * Mirrors {@link CancelPrivateEventController} — a dedicated page gives the confirmation somewhere
 * to say what cancelling does, and a POST is not reachable by a stray click.
 * <p>
 * <strong>No typed word, and a red button.</strong> The two questions are separate (CLAUDE.md).
 * Nothing stored is destroyed by appending a cancellation, so there is no word to type. But nothing
 * puts the trip back from inside the app either — there is no undo, re-booking mints a new trip id,
 * and a past trip cannot be re-booked at all — so the colour is red, as Cancel Hotel's is.
 * <p>
 * Every miss navigates to {@code /booked-trains} in silence rather than attaching a flash: the list
 * is a j2html view and cannot render one (the dead-flash pattern in {@code docs/Cleanup_Tasks.md}).
 */
@Controller
public class CancelTrainController {

    private static final Logger log = LoggerFactory.getLogger(CancelTrainController.class);

    private final CancelTrain applicationService;
    private final TrainDetailsViewProjector detailsProjector;

    public CancelTrainController(CancelTrain applicationService,
                                 TrainDetailsViewProjector detailsProjector) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
    }

    @GetMapping("/booked-trains/{tripId}/cancel")
    public String cancelTrainForm(@PathVariable("tripId") String tripIdString, Model model) {
        Optional<TrainDetailsView> maybe = lookup(tripIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-trains";
        }
        model.addAttribute("train", maybe.get());
        model.addAttribute("reason", "");
        return "cancel-train";
    }

    @PostMapping("/booked-trains/{tripId}/cancel")
    public String cancelTrain(@PathVariable("tripId") String tripIdString,
                              @RequestParam(value = "reason", required = false) String reason,
                              @RequestParam(value = "from", required = false) String from) {
        Optional<TrainDetailsView> maybe = lookup(tripIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-trains";
        }

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            applicationService.cancelTrain(UUID.randomUUID(),
                    new CancelTrainRequest(maybe.get().tripId().id(), reason));
        } catch (TrainNotFound e) {
            // Already cancelled in another tab: there is nothing left to cancel, and nothing to
            // tell the list either.
            return "redirect:/booked-trains";
        } catch (ReadOnlyModeException e) {
            // CommandExecutor refuses before writing, so the data is safe either way; this is what
            // turns the refusal into the page that explains it instead of an error page.
            log.warn("Attempted to cancel train while in read-only mode", e);
            return "redirect:/read-only";
        }

        return returnTo(from, "/booked-trains");
    }

    private Optional<TrainDetailsView> lookup(String tripIdString) {
        try {
            return detailsProjector.findById(TrainTripId.of(UUID.fromString(tripIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    /**
     * Where to land after a successful cancellation: back at the report when Ted arrived from a fix
     * link, otherwise this controller's own default. Only the <em>success</em> path takes it — a
     * stale link or an already-cancelled miss has fixed nothing, so it still goes where it did.
     */
    private static String returnTo(String from, String fallback) {
        return "redirect:" + FixOrigin.returnTo(from).orElse(fallback);
    }

}
