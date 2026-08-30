package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.ZoneResolutionException;
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

    /**
     * {@code ?date=} comes from the calendar day-menu; {@code ?city=&checkIn=&checkOut=} come from
     * a "Book hotel" fix link on {@code /schedule-problems}, which knows exactly which city and
     * which nights are uncovered. Every parameter is optional and every absent-value default is
     * unchanged, so the index nav card and the day-menu link behave exactly as before.
     * <p>
     * No zone is prefilled: the night sweep's location map is keyed city-only, so there is none to
     * carry (F5 in {@code docs/archived/ProblemCalendarPlan.md}).
     */
    @GetMapping("/book-hotel")
    public String bookHotelForm(Model model,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        // checkIn wins over date when both are present: it is the more specific statement of the
        // same thing, and only the fix link sends it.
        LocalDate arrival = firstPresent(checkIn, date, LocalDate.now(clock).plusWeeks(2));
        request.setCheckIn(arrival.atTime(15, 0));
        // The gap's own checkout when the fix link supplies one, otherwise one night.
        LocalDate departure = checkOut != null && checkOut.isAfter(arrival)
                ? checkOut
                : arrival.plusDays(1);
        request.setCheckOut(departure.atTime(11, 0));
        if (city != null && !city.isBlank()) {
            request.setCity(city);
        }
        model.addAttribute("bookHotel", request);
        return "book-hotel";
    }

    private static LocalDate firstPresent(LocalDate preferred, LocalDate fallback, LocalDate absent) {
        if (preferred != null) {
            return preferred;
        }
        return fallback != null ? fallback : absent;
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
        } catch (InvalidLocationEntry e) {
            // A stay has one location, so the field the domain names maps straight onto an input.
            bindingResult.rejectValue(
                    e.field() == LocationField.VENUE_NAME ? "hotelName" : "city",
                    "invalidLocation", e.getMessage());
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
