package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidEnteredLocation;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
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
        // checkIn wins over date when both are present: it is the more specific statement of the
        // same thing, and only the fix link sends it.
        LocalDate arrival = firstPresent(checkIn, date, LocalDate.now(clock).plusWeeks(2));
        // The gap's own checkout when the fix link supplies one, otherwise one night.
        LocalDate departure = checkOut != null && checkOut.isAfter(arrival)
                ? checkOut
                : arrival.plusDays(1);
        BookHotelRequest request = new BookHotelRequest(
                UUID.randomUUID().toString(), null,
                null, blankToNull(city), null, null, null, null, null, null,
                arrival.atTime(15, 0), departure.atTime(11, 0), null, null);
        model.addAttribute("bookHotel", request);
        return "book-hotel";
    }

    private static LocalDate firstPresent(LocalDate preferred, LocalDate fallback, LocalDate absent) {
        if (preferred != null) {
            return preferred;
        }
        return fallback != null ? fallback : absent;
    }

    /** A prefill parameter that arrived blank seeds nothing, exactly as the setter skipped it. */
    private static String blankToNull(String prefill) {
        return prefill == null || prefill.isBlank() ? null : prefill;
    }

    @PostMapping("/book-hotel")
    public String bookHotelSubmit(@ModelAttribute("bookHotel") BookHotelRequest request,
                                  BindingResult bindingResult,
                                  @RequestParam(value = "from", required = false) String from) {
        HotelFormErrors errors = new HotelFormErrors(bindingResult);
        // Binding already failed: a date left blank, or one that would not parse. Those values are
        // null on the request, so calling the service would only reach the write path with them.
        if (bindingResult.hasErrors()) {
            errors.summarize();
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
            return "book-hotel";
        }

        return returnTo(from, "/booked-hotels");
    }

    /**
     * Where to land after a successful action: back at the report when Ted arrived from a fix link,
     * otherwise this controller's own default. Only the <em>success</em> path takes it — a
     * read-only refusal or a stale-link miss has not fixed anything, so it still goes where it
     * always did.
     */
    private static String returnTo(String from, String fallback) {
        return "redirect:" + FixOrigin.returnTo(from).orElse(fallback);
    }

}
