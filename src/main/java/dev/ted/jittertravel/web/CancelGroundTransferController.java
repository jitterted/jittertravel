package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelGroundTransfer;
import dev.ted.jittertravel.application.GroundTransferDetailsView;
import dev.ted.jittertravel.application.GroundTransferDetailsViewProjector;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a planned ground transfer on its own page: GET renders the confirmation, POST performs
 * it. Mirrors {@link CancelHotelController} — a dedicated page is what gives the confirmation
 * somewhere to say what cancelling does, and a POST is not reachable by a stray click.
 * <p>
 * A plain confirm, with no typed word: removing one transfer is recoverable by entering it again,
 * so the amber half of the destructive-action rule applies (CLAUDE.md). The typed word belongs to
 * what cannot be undone from inside the app.
 * <p>
 * There is no cancel <em>reason</em> to collect, unlike a hotel: a transfer has no booking to
 * explain away, and the entry being wrong is the usual reason it is going.
 */
@Controller
public class CancelGroundTransferController {

    private final CancelGroundTransfer applicationService;
    private final GroundTransferDetailsViewProjector detailsProjector;

    public CancelGroundTransferController(CancelGroundTransfer applicationService,
                                          GroundTransferDetailsViewProjector detailsProjector) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
    }

    @GetMapping("/ground-transfers/{groundTransferId}/cancel")
    public String cancelGroundTransferForm(@PathVariable("groundTransferId") String groundTransferIdString,
                                           Model model) {
        Optional<GroundTransferDetailsView> maybe = lookup(groundTransferIdString);
        if (maybe.isEmpty()) {
            // A stale link for a transfer that is already gone: the itinerary is a j2html view that
            // cannot render a flash, so navigate there silently rather than attach a dropped message.
            return "redirect:/itinerary";
        }
        model.addAttribute("transfer", maybe.get());
        return "cancel-ground-transfer";
    }

    @PostMapping("/ground-transfers/{groundTransferId}/cancel")
    public String cancelGroundTransfer(@PathVariable("groundTransferId") String groundTransferIdString) {
        Optional<GroundTransferDetailsView> maybe = lookup(groundTransferIdString);
        if (maybe.isEmpty()) {
            return "redirect:/itinerary";
        }
        // The day the transfer was on, captured before it is cancelled: landing back on the
        // itinerary day it left is what shows that the hop is gone from that day.
        String day = maybe.get().departsAt().toLocalDate().toString();

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            applicationService.cancelGroundTransfer(UUID.randomUUID(),
                    new CancelGroundTransferRequest(maybe.get().groundTransferId().id()));
        } catch (GroundTransferNotFound e) {
            // Already cancelled in another tab: there is nothing left to cancel, and nothing to
            // tell the itinerary either.
            return "redirect:/itinerary";
        }

        return "redirect:/itinerary?date=" + day;
    }

    private Optional<GroundTransferDetailsView> lookup(String groundTransferIdString) {
        try {
            return detailsProjector.findById(GroundTransferId.of(UUID.fromString(groundTransferIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
