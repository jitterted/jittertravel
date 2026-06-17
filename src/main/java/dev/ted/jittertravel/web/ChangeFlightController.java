package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import dev.ted.jittertravel.infrastructure.FlightLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class ChangeFlightController {

    private static final Logger log = LoggerFactory.getLogger(ChangeFlightController.class);

    private final ChangeFlight applicationService;
    private final FlightDetailsViewProjector detailsProjector;
    private final AeroDataBoxClient aeroDataBoxClient;
    private final Clock clock;

    public ChangeFlightController(ChangeFlight applicationService,
                                  FlightDetailsViewProjector detailsProjector,
                                  AeroDataBoxClient aeroDataBoxClient,
                                  Clock clock) {
        this.applicationService = applicationService;
        this.detailsProjector = detailsProjector;
        this.aeroDataBoxClient = aeroDataBoxClient;
        this.clock = clock;
    }

    @GetMapping("/booked-flights/{flightId}")
    public String changeFlightForm(@PathVariable("flightId") String flightIdString,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        Optional<FlightDetailsView> maybe = lookup(flightIdString);
        if (maybe.isEmpty()) {
            redirectAttributes.addFlashAttribute("notFoundMessage",
                    "No booked flight found with id " + flightIdString);
            return "redirect:/booked-flights";
        }

        model.addAttribute("changeFlight", toRequest(maybe.get()));
        return "change-flight";
    }

    @PostMapping("/booked-flights/{flightId}")
    public String changeFlightSubmit(@PathVariable("flightId") String flightIdString,
                                     @ModelAttribute("changeFlight") ChangeFlightRequest command,
                                     BindingResult bindingResult,
                                     RedirectAttributes redirectAttributes) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        // Path is the source of truth for flightId; it is not user-editable.
        command.setFlightId(flightIdString);

        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.changeFlight(UUID.randomUUID(), command, LocalDateTime.now(clock));
        } catch (FlightNotFound e) {
            redirectAttributes.addFlashAttribute("notFoundMessage", e.getMessage());
            return "redirect:/booked-flights";
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to change flight while in read-only mode", e);
            return "redirect:/read-only";
        }

        if (bindingResult.hasErrors()) {
            return "change-flight";
        }

        return "redirect:/booked-flights";
    }

    @PostMapping("/booked-flights/{flightId}/lookup")
    public String lookupFlight(@PathVariable("flightId") String flightIdString,
                               @RequestParam("lookupFlightNumber") String flightNumber,
                               @RequestParam("lookupDepartureDate")
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
                               Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        Optional<FlightLookupResult> result = aeroDataBoxClient.lookup(flightNumber, departureDate);

        ChangeFlightRequest request = new ChangeFlightRequest();
        request.setFlightId(flightIdString);

        if (result.isPresent()) {
            FlightLookupResult lookup = result.get();
            request.setAirline(lookup.airline());
            request.setFlightNumber(lookup.flightNumber());
            request.setDepartureAirport(lookup.departureAirport());
            request.setDepartureDateTime(lookup.departureDateTime());
            request.setArrivalAirport(lookup.arrivalAirport());
            request.setArrivalDateTime(lookup.arrivalDateTime());
        } else {
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("lookupError",
                    "No flight found for " + flightNumber + " on " + departureDate
                            + " (or the API key is not configured). Edit the fields manually.");
        }

        model.addAttribute("lookupFlightNumber", flightNumber);
        model.addAttribute("lookupDepartureDate", departureDate);
        model.addAttribute("changeFlight", request);
        return "change-flight";
    }

    private Optional<FlightDetailsView> lookup(String flightIdString) {
        try {
            return detailsProjector.findById(FlightId.of(UUID.fromString(flightIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    private static ChangeFlightRequest toRequest(FlightDetailsView view) {
        ChangeFlightRequest request = new ChangeFlightRequest();
        request.setFlightId(view.flightId().id().toString());
        request.setAirline(view.airline());
        request.setFlightNumber(view.flightNumber());
        request.setDepartureAirport(view.departureAirport().code());
        request.setDepartureDateTime(view.departureDateTime());
        request.setArrivalAirport(view.arrivalAirport().code());
        request.setArrivalDateTime(view.arrivalDateTime());
        return request;
    }
}
