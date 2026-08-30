package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class BookTrainController {

    private final TrainBooking trainBooking;
    private final Clock clock;

    public BookTrainController(TrainBooking trainBooking, Clock clock) {
        this.trainBooking = trainBooking;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    /**
     * {@code ?date=} comes from the calendar day-menu; {@code ?fromCity=&toCity=} come from a
     * "Book train" fix link on {@code /schedule-problems}. This is the cleanest prefill in the
     * slice: {@link BookTrainRequest} already carries city names, so the gap's own cities go
     * straight in with no resolution step. Every parameter is optional and every absent-value
     * default is unchanged.
     */
    @GetMapping("/book-train")
    public String bookTrainForm(Model model,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String fromCity,
                                @RequestParam(required = false) String toCity) {
        BookTrainRequest request = new BookTrainRequest();
        request.setTrainTripId(UUID.randomUUID().toString());
        if (fromCity != null && !fromCity.isBlank()) {
            request.setDepartureCityName(fromCity);
        }
        if (toCity != null && !toCity.isBlank()) {
            request.setArrivalCityName(toCity);
        }
        // ?date= from the calendar day-menu seeds the departure day; the default (one week
        // out) stands when absent so the index nav card is unaffected.
        LocalDate day = date != null ? date : LocalDate.now(clock).plusWeeks(1);
        LocalDateTime departure = day.atStartOfDay().plusHours(9);
        request.setDepartureDateTime(departure);
        request.setArrivalDateTime(departure.plusHours(4));
        model.addAttribute("bookTrain", request);
        return "book-train";
    }

    @PostMapping("/book-train")
    public String bookTrainSubmit(@ModelAttribute("bookTrain") BookTrainRequest request,
                                  BindingResult bindingResult) {
        try {
            trainBooking.bookTrain(request, Instant.now(clock));
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidLocationEntry e) {
            new TrainLocationError(bindingResult).reject(e);
        } catch (ZoneResolutionException e) {
            bindingResult.reject("zoneUnresolved",
                    "Could not determine the time zone for a station from its location — "
                            + "please choose the zone(s) below.");
        }

        if (bindingResult.hasErrors()) {
            return "book-train";
        }

        return "redirect:/booked-trains";
    }
}
