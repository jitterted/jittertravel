package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Controller
public class BookHotelController {
    private final HotelBooking hotelBooking;
    private final Clock clock;

    public BookHotelController(HotelBooking hotelBooking, Clock clock) {
        this.hotelBooking = hotelBooking;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/book-hotel")
    public String bookHotelForm(Model model,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the check-in day; without it the default
        // (two weeks out) stands so the index nav card is unaffected.
        LocalDate day = date != null ? date : LocalDate.now(clock).plusWeeks(2);
        var checkIn = day.atTime(15, 0);
        request.setCheckIn(checkIn);
        request.setCheckOut(checkIn.toLocalDate().plusDays(1).atTime(11, 0));
        model.addAttribute("bookHotel", request);
        return "book-hotel";
    }

    @PostMapping("/book-hotel")
    public String bookHotelSubmit(@ModelAttribute("bookHotel") BookHotelRequest request,
                                  BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "book-hotel";
        }
        try {
            // now is captured at the boundary as an Instant; the zone is resolved inward.
            hotelBooking.bookHotel(request, Instant.now(clock));
        } catch (CheckInNotInFuture e) {
            bindingResult.rejectValue("checkIn", "future", e.getMessage());
        } catch (InvalidHotelDateRange e) {
            bindingResult.rejectValue("checkOut", "minOneDay", e.getMessage());
        } catch (InvalidCancelByDate e) {
            bindingResult.rejectValue("cancelBy", "notAfterCheckIn", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.rejectValue("zone", "zoneUnresolved",
                    "Could not determine the time zone from the location — please choose one.");
        }

        if (bindingResult.hasErrors()) {
            return "book-hotel";
        }

        return "redirect:/booked-hotels";
    }
}
