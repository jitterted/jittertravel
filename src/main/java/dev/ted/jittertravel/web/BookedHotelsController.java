package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelsProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class BookedHotelsController {

    private final BookedHotelsProjector projector;

    public BookedHotelsController(BookedHotelsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-hotels")
    public ResponseEntity<String> bookedHotels() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedHotelsRenderer.render(projector.views()));
    }
}
