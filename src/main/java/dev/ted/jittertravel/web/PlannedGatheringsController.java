package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Controller
public class PlannedGatheringsController {

    private final PlannedGatheringsProjector projector;

    public PlannedGatheringsController(PlannedGatheringsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/planned-gatherings")
    public ResponseEntity<String> plannedGatherings(
            @RequestParam(required = false) String filter) {
        TimeView timeView = TimeView.fromParam(filter);
        String html = PlannedGatheringsRenderer.render(
                projector.views(timeView, LocalDateTime.now()), timeView);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
