package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringsProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class PlannedGatheringsController {

    private final PlannedGatheringsProjector projector;

    public PlannedGatheringsController(PlannedGatheringsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/planned-gatherings")
    public ResponseEntity<String> plannedGatherings() {
        String html = PlannedGatheringsRenderer.render(projector.views());
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
