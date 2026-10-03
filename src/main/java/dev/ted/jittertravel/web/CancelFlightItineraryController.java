package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedItinerariesProjector;
import dev.ted.jittertravel.application.BookedItineraryView;
import dev.ted.jittertravel.application.CancelFlightItinerary;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.FlightTrips;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.FlightItineraryHasDeparted;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a whole flight itinerary on its own page: GET renders the confirmation, POST performs it.
 * Red button, no typed word, like {@link CancelFlightController}.
 * <p>
 * The page composes read models, as R12 asks: the itinerary (which flights, which code) from
 * {@link BookedItinerariesProjector} and each leg's details from {@link FlightDetailsViewProjector},
 * which no longer holds a leg once it is cancelled — so the page lists only what the cancel would
 * remove. {@link FlightTrips} adds what it would leave: flights falling between these that belong to
 * another itinerary or to none.
 * <p>
 * A leg that has already departed is refused by the command, and the refusal is answered on this
 * page rather than by navigating away: the list is j2html and cannot show it, and the page can say
 * what to do instead. Every other miss — an unknown, malformed or already-cancelled itinerary —
 * navigates to {@code /booked-flights} in silence.
 */
@Controller
public class CancelFlightItineraryController {

    private static final Logger log = LoggerFactory.getLogger(CancelFlightItineraryController.class);

    private final CancelFlightItinerary cancelFlightItinerary;
    private final BookedItinerariesProjector itinerariesProjector;
    private final FlightDetailsViewProjector detailsProjector;
    private final FlightTrips flightTrips;
    private final Clock clock;

    public CancelFlightItineraryController(CancelFlightItinerary cancelFlightItinerary,
                                           BookedItinerariesProjector itinerariesProjector,
                                           FlightDetailsViewProjector detailsProjector,
                                           FlightTrips flightTrips,
                                           Clock clock) {
        this.cancelFlightItinerary = cancelFlightItinerary;
        this.itinerariesProjector = itinerariesProjector;
        this.detailsProjector = detailsProjector;
        this.flightTrips = flightTrips;
        this.clock = clock;
    }

    @GetMapping("/booked-itineraries/{itineraryId}/cancel")
    public String cancelItineraryForm(@PathVariable("itineraryId") String itineraryIdString, Model model) {
        Optional<BookedItineraryView> maybe = lookup(itineraryIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-flights";
        }
        return render(model, maybe.get(), "", false);
    }

    @PostMapping("/booked-itineraries/{itineraryId}/cancel")
    public String cancelItinerary(@PathVariable("itineraryId") String itineraryIdString,
                                  @RequestParam(value = "reason", required = false) String reason,
                                  Model model) {
        Optional<BookedItineraryView> maybe = lookup(itineraryIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-flights";
        }

        try {
            // The nondeterministic inputs, the commandId and the time, are captured here at the
            // boundary.
            cancelFlightItinerary.cancelItinerary(UUID.randomUUID(),
                    new CancelFlightItineraryRequest(maybe.get().itineraryId().id(), reason),
                    Instant.now(clock));
        } catch (FlightItineraryNotFound e) {
            // Already cancelled in another tab: nothing left to cancel.
            return "redirect:/booked-flights";
        } catch (FlightItineraryHasDeparted e) {
            return render(model, maybe.get(), reason, true);
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to cancel flight itinerary while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/booked-flights";
    }

    private String render(Model model, BookedItineraryView itinerary, String reason, boolean departed) {
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("legs", liveLegs(itinerary));
        model.addAttribute("stays", flightTrips.staysBooked(itinerary, Instant.now(clock)));
        model.addAttribute("reason", reason == null ? "" : reason);
        model.addAttribute("departed", departed);
        return "cancel-flight-itinerary";
    }

    private List<FlightDetailsView> liveLegs(BookedItineraryView itinerary) {
        return itinerary.flightIds().stream()
                .map(detailsProjector::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(leg -> leg.departureDateTime().utc()))
                .toList();
    }

    private Optional<BookedItineraryView> lookup(String itineraryIdString) {
        try {
            return itinerariesProjector.findLive(FlightItineraryId.of(UUID.fromString(itineraryIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
