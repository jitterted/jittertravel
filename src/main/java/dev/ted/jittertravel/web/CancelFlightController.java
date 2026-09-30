package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Cancels a booked flight on its own page: GET renders the confirmation, POST performs it. Mirrors
 * {@link CancelTrainController} — red button, no typed word, and every miss navigates to
 * {@code /booked-flights} in silence (the list is j2html and cannot render a flash).
 * <p>
 * No problem-context banner and no {@code from} origin yet: {@code ProblemFix} does not link here,
 * so nothing arrives with one. Wiring the overlapping-flights fix link is when both are added.
 */
@Controller
public class CancelFlightController {

    private static final Logger log = LoggerFactory.getLogger(CancelFlightController.class);

    private final CancelFlight cancelFlight;
    private final FlightDetailsViewProjector detailsProjector;
    private final Clock clock;

    public CancelFlightController(CancelFlight cancelFlight,
                                  FlightDetailsViewProjector detailsProjector,
                                  Clock clock) {
        this.cancelFlight = cancelFlight;
        this.detailsProjector = detailsProjector;
        this.clock = clock;
    }

    @GetMapping("/booked-flights/{flightId}/cancel")
    public String cancelFlightForm(@PathVariable("flightId") String flightIdString, Model model) {
        Optional<FlightDetailsView> maybe = lookup(flightIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-flights";
        }
        model.addAttribute("flight", maybe.get());
        model.addAttribute("reason", "");
        return "cancel-flight";
    }

    @PostMapping("/booked-flights/{flightId}/cancel")
    public String cancelFlight(@PathVariable("flightId") String flightIdString,
                               @RequestParam(value = "reason", required = false) String reason) {
        Optional<FlightDetailsView> maybe = lookup(flightIdString);
        if (maybe.isEmpty()) {
            return "redirect:/booked-flights";
        }

        try {
            // The nondeterministic inputs, the commandId and the time, are captured here at the
            // boundary.
            cancelFlight.cancelFlight(UUID.randomUUID(),
                    new CancelFlightRequest(maybe.get().flightId().id(), reason),
                    Instant.now(clock));
        } catch (FlightNotFound e) {
            // Already cancelled in another tab: nothing left to cancel.
            return "redirect:/booked-flights";
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to cancel flight while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/booked-flights";
    }

    private Optional<FlightDetailsView> lookup(String flightIdString) {
        try {
            return detailsProjector.findById(FlightId.of(UUID.fromString(flightIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
