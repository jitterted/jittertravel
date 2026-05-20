package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceProjector;
import dev.ted.jittertravel.application.TentativeConferenceView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class CalendarController {

    private final TentativeConferenceProjector tentativeConferenceProjector;

    @Autowired
    public CalendarController(TentativeConferenceProjector tentativeConferenceProjector) {
        this.tentativeConferenceProjector = tentativeConferenceProjector;
    }

    @GetMapping("/calendar")
    public String getTemplatedCalendar(Model model) {
        List<TentativeConferenceView> conferences = tentativeConferenceProjector.views();
        LocalDate rangeStart = conferences.getFirst().startDate().toLocalDate().minusDays(5);
        LocalDate rangeEnd = conferences.getLast().endDate().toLocalDate().plusDays(5);

        String calendarHtml = CalendarViewBuilder.render(conferences, rangeStart, rangeEnd);
        model.addAttribute("calendarMarkup", calendarHtml);

        return "confirmed-calendar";
    }

}