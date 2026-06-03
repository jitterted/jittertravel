package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryProjector;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ItineraryController {

    private final ItineraryProjector itineraryProjector;

    public ItineraryController(ItineraryProjector itineraryProjector) {
        this.itineraryProjector = itineraryProjector;
    }

    @GetMapping("/itinerary")
    public String itinerary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
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
