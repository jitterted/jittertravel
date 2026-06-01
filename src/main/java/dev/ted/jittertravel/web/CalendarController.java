package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.ConferenceCalendarProjector;
import dev.ted.jittertravel.application.FlightCalendarProjector;
import dev.ted.jittertravel.application.HotelCalendarProjector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class CalendarController {

    private final ConferenceCalendarProjector conferenceCalendarProjector;
    private final FlightCalendarProjector flightCalendarProjector;
    private final HotelCalendarProjector hotelCalendarProjector;

    public CalendarController(ConferenceCalendarProjector conferenceCalendarProjector,
                              FlightCalendarProjector flightCalendarProjector,
                              HotelCalendarProjector hotelCalendarProjector) {
        this.conferenceCalendarProjector = conferenceCalendarProjector;
        this.flightCalendarProjector = flightCalendarProjector;
        this.hotelCalendarProjector = hotelCalendarProjector;
    }

    @GetMapping("/calendar")
    public String getTemplatedCalendar(Model model) {
        List<CalendarEntry> combined = new ArrayList<>();
        combined.addAll(conferenceCalendarProjector.entries());
        combined.addAll(flightCalendarProjector.entries());
        combined.addAll(hotelCalendarProjector.entries());
        combined.sort(Comparator.comparing(CalendarEntry::start));

        LocalDate rangeStart;
        LocalDate rangeEnd;
        if (combined.isEmpty()) {
            LocalDate today = LocalDate.now();
            rangeStart = today.minusWeeks(2);
            rangeEnd = today.plusWeeks(2);
        } else {
            rangeStart = combined.stream()
                    .map(e -> e.start().toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElseThrow()
                    .minusDays(5);
            rangeEnd = combined.stream()
                    .map(e -> e.end().toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElseThrow()
                    .plusDays(5);
        }

        model.addAttribute("calendarMarkup", CalendarViewBuilder.render(combined, rangeStart, rangeEnd));
        return "confirmed-calendar";
    }
}
