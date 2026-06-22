package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class CalendarController {

    private final CalendarAggregator calendarAggregator;

    public CalendarController(CalendarAggregator calendarAggregator) {
        this.calendarAggregator = calendarAggregator;
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,       // yyyy-MM-dd
            DateTimeFormatter.BASIC_ISO_DATE        // yyyyMMdd
    );

    @GetMapping("/calendar")
    public ResponseEntity<String> getCalendar(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        boolean isPublicUser = request.getRemoteUser() == null;
        boolean isOwner = request.isUserInRole("OWNER");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(ConfirmedCalendarRenderer.render(calendarAggregator.allEntries(), LocalDate.now(), isPublicUser, isOwner, parseDate(from), parseDate(to)));
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
