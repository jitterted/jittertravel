package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.application.FlightDetailsView;
import dev.ted.jittertravel.application.FlightDetailsViewProjector;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import dev.ted.jittertravel.infrastructure.FlightLookupCandidates;
import dev.ted.jittertravel.infrastructure.FlightLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
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

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/booked-flights/{flightId}")
    public String changeFlightForm(@PathVariable("flightId") String flightIdString,
                                   Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        Optional<FlightDetailsView> maybe = lookup(flightIdString);
        if (maybe.isEmpty()) {
            // Stale edit link for a flight that's already gone: the view-only list can't render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/booked-flights";
        }

        FlightDetailsView view = maybe.get();
        // Seed the lookup box from the booking itself, so re-fetching this flight from
        // AeroDataBox doesn't mean retyping what the form already shows. The departure day is
        // taken in the airport's own zone, which is the date the API's dateLocalRole=Departure
        // expects.
        model.addAttribute("lookupFlightNumber", view.flightNumber());
        model.addAttribute("lookupDepartureDate", view.departureDateTime().localDateTime().toLocalDate());
        model.addAttribute("changeFlight", toRequest(view));
        return "change-flight";
    }

    @PostMapping("/booked-flights/{flightId}")
    public String changeFlightSubmit(@PathVariable("flightId") String flightIdString,
                                     @ModelAttribute("changeFlight") ChangeFlightRequest command,
                                     BindingResult bindingResult) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        // Path is the source of truth for flightId; it is not user-editable.
        command.setFlightId(flightIdString);

        try {
            // Nondeterministic inputs (commandId, now) are captured here at the boundary.
            applicationService.changeFlight(UUID.randomUUID(), command, Instant.now(clock));
        } catch (FlightNotFound e) {
            // The flight vanished between GET and POST (e.g. removed in another tab). Report it on
            // the form itself — never by redirecting to the view-only list, which drops the flash.
            bindingResult.reject("notFound", e.getMessage());
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        } catch (ZoneResolutionException e) {
            bindingResult.reject("zoneUnresolved",
                    "Could not determine the time zone for an airport — "
                            + "please choose the zone(s) below.");
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

        FlightLookupCandidates candidates = aeroDataBoxClient.lookup(flightNumber, departureDate);

        ChangeFlightRequest request = new ChangeFlightRequest();
        request.setFlightId(flightIdString);

        if (candidates.requiresChoice()) {
            // The flight number covers several segments that day; only the traveller knows which
            // one they are on, so offer them and leave the form otherwise untouched.
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("legChoices", candidates.segments());
            model.addAttribute("throughFlight", candidates.throughFlight().orElse(null));
        } else if (candidates.isEmpty()) {
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("lookupError",
                    "No flight found for " + flightNumber + " on " + departureDate
                            + " (or the API key is not configured). Edit the fields manually.");
        } else {
            applyLookup(request, candidates.single());
        }

        model.addAttribute("lookupFlightNumber", flightNumber);
        model.addAttribute("lookupDepartureDate", departureDate);
        model.addAttribute("changeFlight", request);
        return "change-flight";
    }

    /**
     * Fills the form from the leg the user picked out of a multi-segment lookup. The chosen leg's
     * fields arrive as hidden inputs, so no second API call is made.
     */
    @PostMapping("/booked-flights/{flightId}/lookup/select")
    public String selectLeg(@PathVariable("flightId") String flightIdString,
                            @ModelAttribute("changeFlight") ChangeFlightRequest request,
                            @RequestParam("lookupFlightNumber") String lookupFlightNumber,
                            @RequestParam("lookupDepartureDate")
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lookupDepartureDate,
                            Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        // Path is the source of truth for flightId; it is not user-editable.
        request.setFlightId(flightIdString);
        model.addAttribute("lookupFlightNumber", lookupFlightNumber);
        model.addAttribute("lookupDepartureDate", lookupDepartureDate);
        return "change-flight";
    }

    private void applyLookup(ChangeFlightRequest request, FlightLookupResult lookup) {
        request.setAirline(lookup.airline());
        request.setFlightNumber(lookup.flightNumber());
        request.setDepartureAirport(lookup.departureAirport());
        request.setDepartureDateTime(lookup.departureDateTime());
        request.setDepartureZone(lookup.departureZoneId());
        request.setArrivalAirport(lookup.arrivalAirport());
        request.setArrivalDateTime(lookup.arrivalDateTime());
        request.setArrivalZone(lookup.arrivalZoneId());
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
        request.setDepartureDateTime(view.departureDateTime().localDateTime());
        request.setDepartureZone(view.departureDateTime().zone().getId());
        request.setArrivalAirport(view.arrivalAirport().code());
        request.setArrivalDateTime(view.arrivalDateTime().localDateTime());
        request.setArrivalZone(view.arrivalDateTime().zone().getId());
        return request;
    }
}
