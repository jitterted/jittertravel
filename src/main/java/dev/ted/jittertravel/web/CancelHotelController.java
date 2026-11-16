package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a booked hotel stay on its own dedicated page: GET renders the confirmation, POST
 * performs it. It is deliberately separate from the edit page — a dedicated page makes the action
 * discoverable (reached by a "Cancel" link on {@code /booked-hotels}, the bin on {@code /calendar}
 * and {@code /itinerary}, and a duplicate-hotel fix link) and gives the confirmation somewhere to
 * explain what cancelling does.
 * <p>
 * Both ways out — "Keep it" and a successful cancel — go where the link was clicked, through one
 * {@link WayBack} ({@link LinkOrigin}); with no origin, to the list.
 * <p>
 * Its own controller rather than a branch of {@link ChangeHotelController}: one slice per
 * controller, and cancelling has nothing to do with the edit form's binding and validation.
 */
@Controller
public class CancelHotelController {

    private final CancelHotel cancelHotel;
    private final HotelDetailsViewProjector detailsProjector;

    public CancelHotelController(CancelHotel cancelHotel,
                                 HotelDetailsViewProjector detailsProjector) {
        this.cancelHotel = cancelHotel;
        this.detailsProjector = detailsProjector;
    }

    /** With no origin — reached from {@code /booked-hotels} or a typed URL — both ways out lead there. */
    private static final WayBack TO_THE_LIST = new WayBack("/booked-hotels", "the list");

    @GetMapping("/booked-hotels/{hotelBookingId}/cancel")
    public String cancelHotelForm(@PathVariable("hotelBookingId") String hotelBookingIdString,
                                  @RequestParam(value = "from", required = false) String from,
                                  @RequestParam(value = "day", required = false) String day,
                                  Model model) {
        Optional<HotelDetailsView> maybe = lookup(hotelBookingIdString);
        if (maybe.isEmpty()) {
            // Stale cancel link for a booking that's already gone: the view-only list can't render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/booked-hotels";
        }
        model.addAttribute("booking", maybe.get());
        model.addAttribute("reason", "");
        model.addAttribute("wayBack", LinkOrigin.wayBack(from, day, TO_THE_LIST));
        return "cancel-hotel";
    }

    @PostMapping("/booked-hotels/{hotelBookingId}/cancel")
    public String cancelHotel(@PathVariable("hotelBookingId") String hotelBookingIdString,
                              @RequestParam(value = "reason", required = false) String reason,
                              @RequestParam(value = "from", required = false) String from,
                              @RequestParam(value = "day", required = false) String day) {
        UUID hotelBookingId;
        try {
            hotelBookingId = UUID.fromString(hotelBookingIdString);
        } catch (IllegalArgumentException malformedUuid) {
            // Malformed id in the path: nothing to cancel, and the view-only list can't render a
            // flash, so navigate there silently.
            return "redirect:/booked-hotels";
        }

        try {
            // The commandId, the one nondeterministic input, is captured here at the boundary.
            cancelHotel.cancelHotel(UUID.randomUUID(),
                    new CancelHotelRequest(hotelBookingId, reason));
        } catch (HotelBookingNotFound e) {
            // The booking is already gone (e.g. cancelled in another tab); there is nothing left to
            // cancel, and the view-only list can't render a flash, so fall back to it silently.
            return "redirect:/booked-hotels";
        }

        // Only the success path goes back where the link was clicked: a stale link or an
        // already-cancelled miss has fixed nothing, so it still goes to the list.
        return "redirect:" + LinkOrigin.wayBack(from, day, TO_THE_LIST).href();
    }

    private Optional<HotelDetailsView> lookup(String hotelBookingIdString) {
        try {
            return detailsProjector.findById(HotelBookingId.of(UUID.fromString(hotelBookingIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

}
