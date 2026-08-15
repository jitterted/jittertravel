package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
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
        // Path is the source of truth for hotelBookingId; it is not user-editable.
        command.setHotelBookingId(hotelBookingIdString);

        if (bindingResult.hasErrors()) {
            return "change-hotel";
        }
        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.changeHotel(UUID.randomUUID(), command, Instant.now(clock));
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
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }

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

    private static ChangeHotelRequest toRequest(HotelDetailsView view) {
        ChangeHotelRequest request = new ChangeHotelRequest();
        request.setHotelBookingId(view.hotelBookingId().id().toString());
        request.setHotelName(view.hotelName());
        request.setStreet(view.address().street());
        request.setCity(view.address().city());
        request.setRegion(view.address().region());
        request.setCountry(view.address().country());
        request.setPostalCode(view.address().postalCode());
        request.setLocationForMatching(view.address().locationForMatching());
        request.setMapsUrl(view.mapsUrl());
        request.setCheckIn(view.checkIn());
        request.setCheckOut(view.checkOut());
        request.setCancelBy(view.cancelBy());
        request.setBookingIntent(view.bookingIntent());
        return request;
    }
}