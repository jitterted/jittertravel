package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.BookedFlightsProjector;
import dev.ted.jittertravel.application.BookedItinerariesProjector;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.FlightTrips;
import dev.ted.jittertravel.application.ItineraryChangeOutcomesProjector;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.FlightItineraryId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class BookedFlightsController {

    private final BookedFlightsProjector projector;
    private final FlightTrips flightTrips;
    private final BookedItinerariesProjector itinerariesProjector;
    private final ItineraryChangeOutcomesProjector changeOutcomes;
    private final Clock clock;

    public BookedFlightsController(BookedFlightsProjector projector, FlightTrips flightTrips,
                                   BookedItinerariesProjector itinerariesProjector,
                                   ItineraryChangeOutcomesProjector changeOutcomes, Clock clock) {
        this.projector = projector;
        this.flightTrips = flightTrips;
        this.itinerariesProjector = itinerariesProjector;
        this.changeOutcomes = changeOutcomes;
        this.clock = clock;
    }

    /**
     * @param changed the itinerary whose change was just recorded, and {@code how} its outcome key
     *                ({@link ChangeFlightItineraryController#howParam}); together they earn the
     *                banner, and anything else is ignored rather than refused
     */
    @GetMapping("/booked-flights")
    public ResponseEntity<String> bookedFlights(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String cancelled,
            @RequestParam(required = false) String changed,
            @RequestParam(required = false) String how) {
        TimeView timeView = TimeView.fromParam(filter);
        CancelledView cancelledView = CancelledView.fromParam(cancelled);
        Instant now = Instant.now(clock);
        List<BookedFlightView> listed = projector.views(timeView, cancelledView, now);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(BookedFlightsRenderer.render(listed, timeView, cancelledView,
                        projector.cancelledCount(timeView, now), flightTrips.forList(listed, now),
                        changeRecorded(changed, how)));
    }

    /**
     * The banner only for a live itinerary whose last change is the kind the key names, so a stale
     * or hand-edited address shows the plain list rather than a sentence about something else.
     */
    private Optional<ChangeRecorded> changeRecorded(String changed, String how) {
        if (changed == null || how == null) {
            return Optional.empty();
        }
        FlightItineraryId itineraryId;
        try {
            itineraryId = FlightItineraryId.of(UUID.fromString(changed));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return itinerariesProjector.findLive(itineraryId).flatMap(itinerary ->
                changeOutcomes.lastChange(itineraryId)
                        .filter(outcome -> ChangeFlightItineraryController.howParam(outcome.kind()).equals(how))
                        .map(outcome -> new ChangeRecorded(itinerary.confirmationCode(), outcome)));
    }
}
