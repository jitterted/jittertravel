package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelHotel;
import dev.ted.jittertravel.domain.CannotCancelAfterCheckIn;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Cancels a booked hotel stay. Its own controller rather than another branch of
 * {@link ChangeHotelController}: one slice per controller, and cancelling has nothing to do with
 * the edit form's binding and validation. The form posting here lives on the edit page, outside
 * that page's {@code <form>} (HTML forbids nested forms).
 */
@Controller
public class CancelHotelController {

    private final CancelHotel applicationService;
    private final Clock clock;

    public CancelHotelController(CancelHotel applicationService, Clock clock) {
        this.applicationService = applicationService;
        this.clock = clock;
    }

    @PostMapping("/booked-hotels/{hotelBookingId}/cancel")
    public String cancelHotel(@PathVariable("hotelBookingId") String hotelBookingIdString,
                              @RequestParam(value = "reason", required = false) String reason,
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
            redirectAttributes.addFlashAttribute("notFoundMessage", e.getMessage());
        } catch (CannotCancelAfterCheckIn e) {
            redirectAttributes.addFlashAttribute("cancelFailedMessage", e.getMessage());
        }

        return "redirect:/booked-hotels";
    }
}
