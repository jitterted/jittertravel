package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.ZoneResolutionException;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.infrastructure.AeroDataBoxClient;
import dev.ted.jittertravel.infrastructure.FlightLookupCandidates;
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

import dev.ted.jittertravel.application.AirportCityResolver;

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

    private final AirportCityResolver airportCities;

    public BookFlightController(FlightBooking applicationService,
                                AeroDataBoxClient aeroDataBoxClient,
                                AirportCityResolver airportCities,
                                Clock clock) {
        this.applicationService = applicationService;
        this.aeroDataBoxClient = aeroDataBoxClient;
        this.airportCities = airportCities;
        this.clock = clock;
    }

    @ModelAttribute("commonZones")
    public CommonZone[] commonZones() {
        return CommonZone.values();
    }

    /**
     * {@code ?date=} comes from the calendar day-menu; {@code ?fromCity=&toCity=} come from a
     * "Book flight" fix link on {@code /schedule-problems}. The link carries <strong>cities, never
     * codes</strong>: the airport table is many-to-one (London is LHR/LGW/STN/LCY), so a code is
     * seeded only where the city has exactly one — a wrong prefilled airport is worse than an empty
     * one, because Ted has to notice it to undo it. Every parameter is optional and every
     * absent-value default is unchanged.
     */
    @GetMapping("/book-flight")
    public String bookFlightForm(Model model,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(required = false) String fromCity,
                                 @RequestParam(required = false) String toCity) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }
        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());
        soleAirport(fromCity).ifPresent(request::setDepartureAirport);
        soleAirport(toCity).ifPresent(request::setArrivalAirport);
        // ?date= from the calendar day-menu seeds the departure day; without it, the default
        // stands (one week out) so the index nav card keeps working unchanged.
        LocalDate day = date != null ? date : LocalDate.now(clock).plusWeeks(1);
        LocalDateTime departure = day.atStartOfDay().plusHours(9);
        request.setDepartureDateTime(departure);
        request.setArrivalDateTime(departure.plusHours(3));

        // Seed the lookup box's date with the same day, so arriving from the calendar's
        // "Add flight" doesn't mean retyping the date to do the lookup.
        model.addAttribute("lookupDepartureDate", day);
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

        FlightLookupCandidates candidates = aeroDataBoxClient.lookup(flightNumber, departureDate);

        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());

        if (candidates.requiresChoice()) {
            // The flight number covers several segments that day; only the traveller knows which
            // one they are on, so offer them and leave the form otherwise untouched.
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("legChoices", candidates.segments());
            model.addAttribute("throughFlight", candidates.throughFlight().orElse(null));
        } else if (candidates.isEmpty()) {
            // Preserve what the user typed and surface an error banner.
            request.setFlightNumber(flightNumber);
            request.setDepartureDateTime(departureDate.atStartOfDay().plusHours(9));
            model.addAttribute("lookupError",
                    "No flight found for " + flightNumber + " on " + departureDate
                            + " (or the API key is not configured). Fill in the details manually.");
        } else {
            applyLookup(request, candidates.single());
        }

        // Echo the lookup inputs back so the form retains them.
        model.addAttribute("lookupFlightNumber", flightNumber);
        model.addAttribute("lookupDepartureDate", departureDate);
        model.addAttribute("bookFlight", request);
        return "book-flight";
    }

    /**
     * Fills the booking form from the leg the user picked out of a multi-segment lookup. The
     * chosen leg's fields arrive as hidden inputs, so no second API call is made.
     */
    @PostMapping("/book-flight/lookup/select")
    public String selectLeg(@ModelAttribute("bookFlight") BookFlightRequest request,
                            @RequestParam("lookupFlightNumber") String lookupFlightNumber,
                            @RequestParam("lookupDepartureDate")
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lookupDepartureDate,
                            Model model) {
        if (applicationService.isReadOnly()) {
            return "redirect:/read-only";
        }

        model.addAttribute("lookupFlightNumber", lookupFlightNumber);
        model.addAttribute("lookupDepartureDate", lookupDepartureDate);
        return "book-flight";
    }

    private void applyLookup(BookFlightRequest request, FlightLookupResult lookup) {
        request.setAirline(lookup.airline());
        request.setFlightNumber(lookup.flightNumber());
        request.setDepartureAirport(lookup.departureAirport());
        request.setDepartureDateTime(lookup.departureDateTime());
        request.setDepartureZone(lookup.departureZoneId());
        request.setArrivalAirport(lookup.arrivalAirport());
        request.setArrivalDateTime(lookup.arrivalDateTime());
        request.setArrivalZone(lookup.arrivalZoneId());
    }
    /** Empty unless the city has exactly one airport — see {@link AirportCityResolver#soleAirportFor}. */
    private Optional<String> soleAirport(String city) {
        return city == null || city.isBlank() ? Optional.empty() : airportCities.soleAirportFor(city);
    }

}
