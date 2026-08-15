package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

@Controller
public class ScheduleProblemsController {

    private final ScheduleGapProjector scheduleGapProjector;
    private final Clock clock;

    public ScheduleProblemsController(ScheduleGapProjector scheduleGapProjector, Clock clock) {
        this.scheduleGapProjector = scheduleGapProjector;
        this.clock = clock;
    }

    @GetMapping("/schedule-problems")
    public ResponseEntity<String> scheduleProblems() {
        // now is captured here at the boundary; past problems are dropped as no longer actionable.
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(ScheduleProblemsRenderer.render(scheduleGapProjector.problems(Instant.now(clock))));
    }
}
