package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainsProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class BookedTrainsController {

    private final BookedTrainsProjector projector;

    public BookedTrainsController(BookedTrainsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-trains")
    public ResponseEntity<String> bookedTrains() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedTrainsRenderer.render(projector.views()));
    }
}
