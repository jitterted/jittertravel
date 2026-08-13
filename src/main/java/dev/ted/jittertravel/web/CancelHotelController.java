package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.domain.CannotCancelAfterCheckIn;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a booked hotel stay on its own dedicated page: GET renders the confirmation, POST
 * performs it. It is deliberately separate from the edit page — a dedicated page makes the action
 * discoverable (reached by a "Cancel" link on {@code /booked-hotels} and on the edit page) and,
 * crucially, lets a failed cancel (check-in already passed) re-render <em>this</em> page with the
 * error at the top instead of bouncing to the view-only list, which cannot show a message.
 * <p>
 * Its own controller rather than a branch of {@link ChangeHotelController}: one slice per
 * controller, and cancelling has nothing to do with the edit form's binding and validation.
 */
@Controller
public class CancelHotelController {

    private final CancelHotel applicationService;
    private final HotelDetailsViewProjector detailsProjector;
    private final Clock clock;

    public CancelHotelController(CancelHotel applicationService,
                                 HotelDetailsViewProjector detailsProjector,
                                 Clock clock) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    @GetMapping("/booked-hotels/{hotelBookingId}/cancel")
    public String cancelHotelForm(@PathVariable("hotelBookingId") String hotelBookingIdString,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Optional<HotelDetailsView> maybe = lookup(hotelBookingIdString);
        if (maybe.isEmpty()) {
            redirectAttributes.addFlashAttribute("notFoundMessage",
                    "No booked hotel found with id " + hotelBookingIdString);
            return "redirect:/booked-hotels";
        }
        model.addAttribute("booking", maybe.get());
        model.addAttribute("reason", "");
        return "cancel-hotel";
    }

    @PostMapping("/booked-hotels/{hotelBookingId}/cancel")
    public String cancelHotel(@PathVariable("hotelBookingId") String hotelBookingIdString,
                              @RequestParam(value = "reason", required = false) String reason,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        UUID hotelBookingId;
        try {
            hotelBookingId = UUID.fromString(hotelBookingIdString);
        } catch (IllegalArgumentException malformedUuid) {
            redirectAttributes.addFlashAttribute("notFoundMessage",
                    "No booked hotel found with id " + hotelBookingIdString);
            return "redirect:/booked-hotels";
        }

        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.cancelHotel(UUID.randomUUID(),
                    new CancelHotelRequest(hotelBookingId, reason), Instant.now(clock));
        } catch (HotelBookingNotFound e) {
            // The booking is already gone (e.g. cancelled in another tab); there is nothing left to
            // render a cancel page for, so fall back to the list.
            redirectAttributes.addFlashAttribute("notFoundMessage", e.getMessage());
            return "redirect:/booked-hotels";
        } catch (CannotCancelAfterCheckIn e) {
            // Re-render the cancel page itself with the error at the top — never redirect the
            // failure to the view-only list, which cannot show a message.
            Optional<HotelDetailsView> maybe = detailsProjector.findById(HotelBookingId.of(hotelBookingId));
            if (maybe.isEmpty()) {
                return "redirect:/booked-hotels";
            }
            model.addAttribute("booking", maybe.get());
            model.addAttribute("reason", reason == null ? "" : reason);
            model.addAttribute("cancelError", e.getMessage());
            return "cancel-hotel";
        }

        return "redirect:/booked-hotels";
    }

    private Optional<HotelDetailsView> lookup(String hotelBookingIdString) {
        try {
            return detailsProjector.findById(HotelBookingId.of(UUID.fromString(hotelBookingIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
