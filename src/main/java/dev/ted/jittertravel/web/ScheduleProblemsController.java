package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class ScheduleProblemsController {

    private final ScheduleGapProjector scheduleGapProjector;

    public ScheduleProblemsController(ScheduleGapProjector scheduleGapProjector) {
        this.scheduleGapProjector = scheduleGapProjector;
    }

    @GetMapping("/schedule-problems")
    public ResponseEntity<String> scheduleProblems() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(ScheduleProblemsRenderer.render(scheduleGapProjector.problems()));
    }
}
