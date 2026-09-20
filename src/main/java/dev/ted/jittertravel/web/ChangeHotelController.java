package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ChangeHotelController {

    private final ChangeHotel applicationService;
    private final HotelDetailsViewProjector detailsProjector;
    private final Clock clock;

    public ChangeHotelController(ChangeHotel applicationService,
                                 HotelDetailsViewProjector detailsProjector,
                                 Clock clock) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/booked-hotels/{hotelBookingId}")
    public String changeHotelForm(@PathVariable("hotelBookingId") String hotelBookingIdString,
                                  Model model) {
        Optional<HotelDetailsView> maybe = lookup(hotelBookingIdString);
        if (maybe.isEmpty()) {
            // Stale edit link for a booking that's already gone: the view-only list can't render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/booked-hotels";
        }

        model.addAttribute("changeHotel", toRequest(maybe.get()));
        return "change-hotel";
    }

    @PostMapping("/booked-hotels/{hotelBookingId}")
    public String changeHotelSubmit(@PathVariable("hotelBookingId") String hotelBookingIdString,
                                    @ModelAttribute("changeHotel") ChangeHotelRequest command,
                                    BindingResult bindingResult) {
        HotelFormErrors errors = new HotelFormErrors(bindingResult);
        // Binding already failed: a date left blank, or one that would not parse. Those values are
        // null on the request, so calling the service would only reach the write path with them.
        if (bindingResult.hasErrors()) {
            errors.summarize();
            return "change-hotel";
        }
        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            // The booking comes from the path, never from the form: the request has no id
            // component, so there is nothing on the page a crafted POST could re-target.
            applicationService.changeHotel(UUID.randomUUID(), hotelBookingIdString, command,
                                           Instant.now(clock));
        } catch (HotelBookingNotFound e) {
            // The booking vanished between GET and POST (e.g. cancelled in another tab). Report it
            // on the form itself — never by redirecting to the view-only list, which drops the flash.
            bindingResult.reject("notFound", e.getMessage());
        } catch (CheckInNotInFuture e) {
            bindingResult.rejectValue("checkIn", "future", e.getMessage());
        } catch (InvalidHotelDateRange e) {
            bindingResult.rejectValue("checkOut", "afterCheckIn", e.getMessage());
        } catch (InvalidCancelByDate e) {
            bindingResult.rejectValue("cancelBy", "notAfterCheckIn", e.getMessage());
        } catch (InvalidEnteredLocation e) {
            // Every problem the stay has, in one response — a blank name and a blank city are two
            // mistakes in one submit, and reporting only the first reads as the fix having done
            // nothing.
            errors.reject(e);
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }
        errors.summarize();

        if (bindingResult.hasErrors()) {
            return "change-hotel";
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

    /**
     * The stay, minus its id. Which booking this is stays path data on both legs: the template
     * reads {@code ${hotelBookingId}} for the form's action, which Thymeleaf merges in from the
     * URI template variables — so no model attribute and no hidden field has to carry it, and
     * there is nothing on the page a crafted POST could re-target.
     */
    private static ChangeHotelRequest toRequest(HotelDetailsView view) {
        return new ChangeHotelRequest(
                view.hotelName(),
                view.address().street(),
                view.address().city(),
                view.address().region(),
                view.address().country(),
                view.address().postalCode(),
                view.address().locationForMatching(),
                view.mapsUrl(),
                // No zone pick: the select reopens on "derive from location", as it did before.
                null,
                view.checkIn(),
                view.checkOut(),
                view.cancelBy(),
                view.bookingIntent());
    }
}
