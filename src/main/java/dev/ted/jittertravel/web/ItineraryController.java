package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryProjector;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
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
    public ResponseEntity<String> itinerary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = itineraryProjector.firstDateOnOrAfter(LocalDate.now(clock));
        }
        List<ItineraryDay> days = List.of(
                new ItineraryDay(date, itineraryProjector.entriesForDate(date)),
                new ItineraryDay(date.plusDays(1), itineraryProjector.entriesForDate(date.plusDays(1))),
                new ItineraryDay(date.plusDays(2), itineraryProjector.entriesForDate(date.plusDays(2)))
        );
        String html = ItineraryRenderer.render(days, date.minusDays(1), date.plusDays(1));
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
