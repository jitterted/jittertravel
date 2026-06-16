package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Controller
public class BookedHotelsController {

    private final BookedHotelsProjector projector;

    public BookedHotelsController(BookedHotelsProjector projector) {
        this.projector = projector;
    }

    @GetMapping("/booked-hotels")
    public ResponseEntity<String> bookedHotels(
            @RequestParam(required = false) String filter) {
        TimeView timeView = TimeView.fromParam(filter);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedHotelsRenderer.render(
                        projector.views(timeView, LocalDateTime.now()), timeView));
    }
}
