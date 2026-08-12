package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import dev.ted.jittertravel.infrastructure.FlightLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class BookFlightController {

    private static final Logger log = LoggerFactory.getLogger(BookFlightController.class);
    private final FlightBooking applicationService;
    private final AeroDataBoxClient aeroDataBoxClient;
    private final Clock clock;

    public BookFlightController(FlightBooking applicationService,
                                AeroDataBoxClient aeroDataBoxClient,
                                Clock clock) {
        this.applicationService = applicationService;
        this.aeroDataBoxClient = aeroDataBoxClient;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    @GetMapping("/book-flight")
    public String bookFlightForm(Model model,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }
        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());
        // ?date= from the calendar day-menu seeds the departure day; without it, the default
        // stands (one week out) so the index nav card keeps working unchanged.
        LocalDate day = date != null ? date : LocalDate.now(clock).plusWeeks(1);
        LocalDateTime departure = day.atStartOfDay().plusHours(9);
        request.setDepartureDateTime(departure);
        request.setArrivalDateTime(departure.plusHours(3));

        model.addAttribute("bookFlight", request);
        return "book-flight";
    }

    @PostMapping("/book-flight")
    public String bookFlightSubmit(@ModelAttribute("bookFlight") BookFlightRequest command,
                                   BindingResult bindingResult) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        try {
            applicationService.bookFlight(command, Instant.now(clock));
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
            log.warn("Attempted to book flight while in read-only mode", e);
            return "redirect:/read-only";
        }

        if (bindingResult.hasErrors()) {
            return "book-flight";
        }

        return "redirect:/booked-flights";
    }

    @PostMapping("/book-flight/lookup")
    public String lookupFlight(@RequestParam("lookupFlightNumber") String flightNumber,
                               @RequestParam("lookupDepartureDate")
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
                               Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        Optional<FlightLookupResult> result = aeroDataBoxClient.lookup(flightNumber, departureDate);

        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());

        if (result.isPresent()) {
            FlightLookupResult lookup = result.get();
            request.setAirline(lookup.airline());
            request.setFlightNumber(lookup.flightNumber());
            request.setDepartureAirport(lookup.departureAirport());
            request.setDepartureDateTime(lookup.departureDateTime());
            request.setDepartureZone(lookup.departureZoneId());
            request.setArrivalAirport(lookup.arrivalAirport());
            request.setArrivalDateTime(lookup.arrivalDateTime());
            request.setArrivalZone(lookup.arrivalZoneId());
        } else {
            // Preserve what the user typed and surface an error banner.
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("lookupError",
                    "No flight found for " + flightNumber + " on " + departureDate
                            + " (or the API key is not configured). Fill in the details manually.");
        }

        // Echo the lookup inputs back so the form retains them.
        model.addAttribute("lookupFlightNumber", flightNumber);
        model.addAttribute("lookupDepartureDate", departureDate);
        model.addAttribute("bookFlight", request);
        return "book-flight";
    }
}
