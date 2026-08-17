package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ItineraryDay;
import dev.ted.jittertravel.application.ItineraryProjector;
import dev.ted.jittertravel.application.ViewerTodayZone;
import dev.ted.jittertravel.application.ViewerZonePolicy;
import dev.ted.jittertravel.application.ZoneDisplay;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.List;

@Controller
public class ItineraryController {

    private final ItineraryProjector itineraryProjector;
    private final Clock clock;
    private final ViewerZonePolicy viewerZonePolicy;
    private final ViewerTodayZone viewerTodayZone;

    public ItineraryController(ItineraryProjector itineraryProjector, Clock clock,
                               ViewerZonePolicy viewerZonePolicy, ViewerTodayZone viewerTodayZone) {
        this.itineraryProjector = itineraryProjector;
        this.clock = clock;
        this.viewerZonePolicy = viewerZonePolicy;
        this.viewerTodayZone = viewerTodayZone;
    }

    @GetMapping("/itinerary")
    public ResponseEntity<String> itinerary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String tz,
            HttpServletRequest request) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), todayZone(request));
        boolean isOwner = request.isUserInRole("OWNER");
        ZoneDisplay zoneDisplay = viewerZonePolicy.forViewer(isOwner, request.isUserInRole("FAMILY"), tz);
        if (date == null) {
            date = itineraryProjector.firstDateOnOrAfter(today);
        }
        List<ItineraryDay> days = List.of(
                new ItineraryDay(date, itineraryProjector.entriesForDate(date)),
                new ItineraryDay(date.plusDays(1), itineraryProjector.entriesForDate(date.plusDays(1))),
                new ItineraryDay(date.plusDays(2), itineraryProjector.entriesForDate(date.plusDays(2)))
        );
        String html = ItineraryRenderer.render(days, date.minusDays(1), date.plusDays(1), today,
                isOwner, zoneDisplay);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    private ZoneId todayZone(HttpServletRequest request) {
        Cookie zoneCookie = WebUtils.getCookie(request, ViewerTodayZone.COOKIE_NAME);
        return viewerTodayZone.resolve(zoneCookie == null ? null : zoneCookie.getValue());
    }
}
