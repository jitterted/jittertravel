package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Controller
public class BookedFlightsController {

    private final BookedFlightsProjector projector;

    public BookedFlightsController(BookedFlightsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-flights")
    public ResponseEntity<String> bookedFlights(
            @RequestParam(required = false) String filter) {
        TimeView timeView = TimeView.fromParam(filter);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedFlightsRenderer.render(
                        projector.views(timeView, LocalDateTime.now()), timeView));
    }
}
