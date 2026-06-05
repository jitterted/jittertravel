package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryProjector;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Controller
public class ItineraryController {

    private final ItineraryProjector itineraryProjector;
    private final Clock clock;

    public ItineraryController(ItineraryProjector itineraryProjector, Clock clock) {
        this.itineraryProjector = itineraryProjector;
        this.clock = clock;
    }

    @GetMapping("/itinerary")
    public String itinerary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        if (date == null) {
            date = itineraryProjector.firstDateOnOrAfter(LocalDate.now(clock));
        }
        model.addAttribute("days", List.of(
                new ItineraryDay(date, itineraryProjector.entriesForDate(date)),
                new ItineraryDay(date.plusDays(1), itineraryProjector.entriesForDate(date.plusDays(1))),
                new ItineraryDay(date.plusDays(2), itineraryProjector.entriesForDate(date.plusDays(2)))
        ));
        model.addAttribute("startDate", date);
        model.addAttribute("prevDate", date.minusDays(1));
        model.addAttribute("nextDate", date.plusDays(1));
        return "itinerary";
    }
}
