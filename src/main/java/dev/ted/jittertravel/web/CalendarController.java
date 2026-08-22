package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.PublicCalendarProjector;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ViewerTodayZone;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import dev.ted.jittertravel.application.ZoneDisplay;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class CalendarController {

    private final CalendarAggregator calendarAggregator;
    private final PublicCalendarProjector publicCalendarProjector;
    private final ScheduleGapProjector scheduleGapProjector;
    private final ViewerZonePolicy viewerZonePolicy;
    private final Clock clock;
    private final ViewerTodayZone viewerTodayZone;

    public CalendarController(CalendarAggregator calendarAggregator,
                             PublicCalendarProjector publicCalendarProjector,
                             ScheduleGapProjector scheduleGapProjector,
                             ViewerZonePolicy viewerZonePolicy,
                             Clock clock, ViewerTodayZone viewerTodayZone) {
        this.calendarAggregator = calendarAggregator;
        this.publicCalendarProjector = publicCalendarProjector;
        this.scheduleGapProjector = scheduleGapProjector;
        this.viewerZonePolicy = viewerZonePolicy;
        this.clock = clock;
        this.viewerTodayZone = viewerTodayZone;
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,       // yyyy-MM-dd
            DateTimeFormatter.BASIC_ISO_DATE        // yyyyMMdd
    );

    @GetMapping("/calendar")
    public ResponseEntity<String> getCalendar(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String tz) {
        boolean isPublicUser = request.getRemoteUser() == null;
        boolean isOwner = request.isUserInRole("OWNER");
        ZoneDisplay zoneDisplay = viewerZonePolicy.forViewer(isOwner, request.isUserInRole("FAMILY"), tz);
        LocalDate today = LocalDate.ofInstant(clock.instant(), todayZone(request));
        // The audience picks the read model, and it is picked here — at the boundary, where the
        // viewer is known — not inside a renderer. An anonymous visitor is served the public
        // projection, which was built from events without ever holding a private value; everyone
        // else gets the owner's own entries. There is no longer a step in between that has to
        // remember to strip anything.
        List<CalendarEntry> entries = isPublicUser
                ? publicCalendarProjector.entries()
                : calendarAggregator.allEntries();
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(CalendarRenderer.render(entries, today,
                        isPublicUser, isOwner, parseDate(from), parseDate(to), zoneDisplay,
                        scheduleGapProjector.awayDays()));
    }

    private ZoneId todayZone(HttpServletRequest request) {
        Cookie zoneCookie = WebUtils.getCookie(request, ViewerTodayZone.COOKIE_NAME);
        return viewerTodayZone.resolve(zoneCookie == null ? null : zoneCookie.getValue());
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
