package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class CalendarController {

    private final CalendarAggregator calendarAggregator;

    public CalendarController(CalendarAggregator calendarAggregator) {
        this.calendarAggregator = calendarAggregator;
    }

    @GetMapping("/calendar")
    public ResponseEntity<String> getCalendar() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(ConfirmedCalendarRenderer.render(calendarAggregator.allEntries(), isPublicUser()));
    }

    private static boolean isPublicUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof AnonymousAuthenticationToken;
    }
}
