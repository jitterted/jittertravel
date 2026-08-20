package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.application.ViewerTodayZone;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Controller
public class ScheduleProblemsController {

    private final ScheduleGapProjector scheduleGapProjector;
    private final Clock clock;
    private final ViewerTodayZone viewerTodayZone;

    public ScheduleProblemsController(ScheduleGapProjector scheduleGapProjector,
                                      Clock clock,
                                      ViewerTodayZone viewerTodayZone) {
        this.scheduleGapProjector = scheduleGapProjector;
        this.clock = clock;
        this.viewerTodayZone = viewerTodayZone;
    }

    /**
     * Both views of the report live here, chosen by {@code ?view=}: the route is already
     * OWNER-only in {@code SecurityConfig}, and a query parameter cannot escape a path matcher, so
     * the calendar inherits that gate exactly rather than needing one of its own.
     */
    @GetMapping("/schedule-problems")
    public ResponseEntity<String> scheduleProblems(HttpServletRequest request,
                                                   @RequestParam(required = false) String view) {
        // now is captured here at the boundary; past problems are dropped as no longer actionable.
        Instant now = clock.instant();
        List<ScheduleProblem> problems = scheduleGapProjector.problems(now);
        String body = switch (ProblemView.fromParam(view)) {
            case LIST -> ScheduleProblemsRenderer.render(problems);
            case CALENDAR -> ProblemCalendarRenderer.render(problems, scheduleGapProjector.context(),
                    LocalDate.ofInstant(now, todayZone(request)));
        };
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(body);
    }

    private ZoneId todayZone(HttpServletRequest request) {
        Cookie zoneCookie = WebUtils.getCookie(request, ViewerTodayZone.COOKIE_NAME);
        return viewerTodayZone.resolve(zoneCookie == null ? null : zoneCookie.getValue());
    }
}
