package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.BookedFlightsProjector;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.FlightTrips;
import dev.ted.jittertravel.application.TimeView;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Controller
public class BookedFlightsController {

    private final BookedFlightsProjector projector;
    private final FlightTrips flightTrips;
    private final Clock clock;

    public BookedFlightsController(BookedFlightsProjector projector, FlightTrips flightTrips, Clock clock) {
        this.projector = projector;
        this.flightTrips = flightTrips;
        this.clock = clock;
    }

    @GetMapping("/booked-flights")
    public ResponseEntity<String> bookedFlights(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String cancelled) {
        TimeView timeView = TimeView.fromParam(filter);
        CancelledView cancelledView = CancelledView.fromParam(cancelled);
        Instant now = Instant.now(clock);
        List<BookedFlightView> listed = projector.views(timeView, cancelledView, now);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedFlightsRenderer.render(listed, timeView, cancelledView,
                        projector.cancelledCount(timeView, now), flightTrips.forList(listed, now)));
    }
}
