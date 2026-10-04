package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightItineraryBooking;
import dev.ted.jittertravel.application.ItineraryEvaluation;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paste a confirmation email, preview every leg, book them all at once.
 * <p>
 * One page and one POST, told apart by which button was pressed: {@code action=preview} evaluates
 * and writes nothing; {@code action=book} runs the same evaluation and writes only if it is clean.
 * Booking always re-reads the text box, so what is booked is what is validated, even if the text
 * changed after the preview.
 * <p>
 * I/O only: ids and {@code now} are minted here, and everything else is
 * {@link FlightItineraryBooking}'s. Under {@code /book-flight/**}, so OWNER-only in
 * {@code SecurityConfig} already.
 */
@Controller
public class BookFlightItineraryController {

    private static final Logger log = LoggerFactory.getLogger(BookFlightItineraryController.class);
    private static final String VIEW = "book-flight-itinerary";

    private final FlightItineraryBooking itineraryBooking;
    private final Clock clock;

    public BookFlightItineraryController(FlightItineraryBooking itineraryBooking, Clock clock) {
        this.itineraryBooking = itineraryBooking;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/book-flight/itinerary")
    public String form(Model model) {
        if (itineraryBooking.isReadOnly()) {
            return "redirect:/read-only";
        }
        BookFlightItineraryRequest request = new BookFlightItineraryRequest();
        // Minted once, when the form is first shown, and carried through preview and book: it is
        // also the command id, so the same form cannot book the same trip twice.
        request.setItineraryId(UUID.randomUUID().toString());
        model.addAttribute("itinerary", request);
        return VIEW;
    }

    @PostMapping("/book-flight/itinerary")
    public String submit(@ModelAttribute("itinerary") BookFlightItineraryRequest request,
                         @RequestParam(value = "action", defaultValue = "preview") String action,
                         Model model) {
        if (itineraryBooking.isReadOnly()) {
            return "redirect:/read-only";
        }
        FlightItineraryId itineraryId = itineraryId(request);
        Supplier<FlightId> newFlightId = () -> FlightId.of(UUID.randomUUID());
        Instant now = Instant.now(clock);

        ItineraryEvaluation evaluation;
        if (action.equals("book")) {
            try {
                evaluation = itineraryBooking.book(request, request.getPasted(), request.getAirportZones(),
                        itineraryId, newFlightId, now);
            } catch (ReadOnlyModeException e) {
                log.warn("Attempted to book a flight itinerary while in read-only mode", e);
                return "redirect:/read-only";
            }
            if (evaluation.bookable() || evaluation.changeable() || evaluation.alreadyBooked()) {
                return "redirect:/booked-flights";
            }
        } else {
            evaluation = itineraryBooking.evaluate(request.getPasted(), request.getAirportZones(),
                    itineraryId, newFlightId, now);
        }
        model.addAttribute("preview", ItineraryPreview.from(evaluation));
        return VIEW;
    }

    /** The id the form carried, or a fresh one if it was hand-edited into something that is not one. */
    private static FlightItineraryId itineraryId(BookFlightItineraryRequest request) {
        try {
            return FlightItineraryId.of(UUID.fromString(request.getItineraryId()));
        } catch (IllegalArgumentException | NullPointerException malformed) {
            UUID fresh = UUID.randomUUID();
            request.setItineraryId(fresh.toString());
            return FlightItineraryId.of(fresh);
        }
    }
}
