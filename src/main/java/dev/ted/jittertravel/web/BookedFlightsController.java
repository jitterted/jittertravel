package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class BookedFlightsController {

    private final BookedFlightsProjector projector;

    public BookedFlightsController(BookedFlightsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-flights")
    public ResponseEntity<String> bookedFlights() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedFlightsRenderer.render(projector.views()));
    }
}
