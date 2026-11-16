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
import org.springframework.web.bind.annotation.RequestParam;

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

    private final CancelGroundTransfer cancelGroundTransfer;
    private final GroundTransferDetailsViewProjector detailsProjector;

    public CancelGroundTransferController(CancelGroundTransfer cancelGroundTransfer,
                                          GroundTransferDetailsViewProjector detailsProjector) {
        this.cancelGroundTransfer = cancelGroundTransfer;
        this.detailsProjector = detailsProjector;
    }

    @GetMapping("/ground-transfers/{groundTransferId}/cancel")
    public String cancelGroundTransferForm(@PathVariable("groundTransferId") String groundTransferIdString,
                                           @RequestParam(value = "from", required = false) String from,
                                           @RequestParam(value = "day", required = false) String day,
                                           Model model) {
        Optional<GroundTransferDetailsView> maybe = lookup(groundTransferIdString);
        if (maybe.isEmpty()) {
            // A stale link for a transfer that is already gone: the itinerary is a j2html view that
            // cannot render a flash, so navigate there silently rather than attach a dropped message.
            return "redirect:/itinerary";
        }
        model.addAttribute("transfer", maybe.get());
        model.addAttribute("wayBack", LinkOrigin.wayBack(from, day, toItsDay(maybe.get())));
        return "cancel-ground-transfer";
    }

    @PostMapping("/ground-transfers/{groundTransferId}/cancel")
    public String cancelGroundTransfer(@PathVariable("groundTransferId") String groundTransferIdString,
                                       @RequestParam(value = "from", required = false) String from,
                                       @RequestParam(value = "day", required = false) String day) {
        Optional<GroundTransferDetailsView> maybe = lookup(groundTransferIdString);
        if (maybe.isEmpty()) {
            return "redirect:/itinerary";
        }
        // Resolved before the transfer is cancelled, while its day is still known.
        WayBack wayBack = LinkOrigin.wayBack(from, day, toItsDay(maybe.get()));

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            cancelGroundTransfer.cancelGroundTransfer(UUID.randomUUID(),
                    new CancelGroundTransferRequest(maybe.get().groundTransferId().id()));
        } catch (GroundTransferNotFound e) {
            // Already cancelled in another tab: there is nothing left to cancel, and nothing to
            // tell the itinerary either.
            return "redirect:/itinerary";
        }

        // Only the success path goes back where the link was clicked: a stale link or an
        // already-cancelled miss has fixed nothing.
        return "redirect:" + wayBack.href();
    }

    /**
     * A transfer has no list of its own, so with no origin both ways out land on the itinerary day
     * it leaves — which is what shows the hop gone from that day.
     */
    private static WayBack toItsDay(GroundTransferDetailsView transfer) {
        return new WayBack("/itinerary?date=" + transfer.departsAt().toLocalDate(), "the itinerary");
    }

    private Optional<GroundTransferDetailsView> lookup(String groundTransferIdString) {
        try {
            return detailsProjector.findById(GroundTransferId.of(UUID.fromString(groundTransferIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

}
