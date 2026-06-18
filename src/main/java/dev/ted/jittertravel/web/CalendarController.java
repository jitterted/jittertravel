package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class CalendarController {

    private final CalendarAggregator calendarAggregator;

    public CalendarController(CalendarAggregator calendarAggregator) {
        this.calendarAggregator = calendarAggregator;
    }

    @GetMapping("/calendar")
    public ResponseEntity<String> getCalendar(HttpServletRequest request) {
        boolean isPublicUser = request.getRemoteUser() == null;
        boolean isOwner = request.isUserInRole("OWNER");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(ConfirmedCalendarRenderer.render(calendarAggregator.allEntries(), LocalDate.now(), isPublicUser, isOwner));
    }
}
