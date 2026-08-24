package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookFlightHandler;
import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightContext;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookFlightControllerValidationTest {

    private static final LocalDateTime NOW_LDT = LocalDateTime.of(2026, 5, 16, 10, 0);
    private static final Instant NOW = NOW_LDT.toInstant(ZoneOffset.UTC);

    @Test
    void departureInThePastIsInvalid() {
        FlightBooking service = mockService();
        BookFlightRequest command = baseRequest();
        command.setDepartureDateTime(NOW_LDT.minusHours(1));
        command.setArrivalDateTime(NOW_LDT.plusDays(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command, NOW);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        }

        assertThat(bindingResult.hasFieldErrors("departureDateTime")).isTrue();
    }

    @Test
    void arrivalBeforeDepartureIsInvalid() {
        FlightBooking service = mockService();
        BookFlightRequest command = baseRequest();
        command.setDepartureDateTime(NOW_LDT.plusDays(1));
        command.setArrivalDateTime(NOW_LDT.plusDays(1).minusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command, NOW);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        }

        assertThat(bindingResult.hasFieldErrors("arrivalDateTime")).isTrue();
    }

    @Test
    void invalidAirportCodeIsReportedAsGlobalError() {
        FlightBooking service = mockService();
        BookFlightRequest command = baseRequest();
        command.setDepartureAirport("BADCODE");
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command, NOW);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        }

        assertThat(bindingResult.hasGlobalErrors()).isTrue();
    }

    @Test
    void validRequestProducesNoErrors() {
        FlightBooking service = mockService();
        BookFlightRequest command = baseRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command, NOW);
        } catch (Exception e) {
            bindingResult.reject("error", e.getMessage());
        }

        assertThat(bindingResult.hasErrors()).isFalse();
    }

    private BookFlightRequest baseRequest() {
        BookFlightRequest command = new BookFlightRequest();
        command.setFlightId(UUID.randomUUID().toString());
        command.setAirline("United");
        command.setFlightNumber("UA100");
        command.setDepartureAirport("SFO");
        command.setDepartureDateTime(NOW_LDT.plusDays(1));
        command.setArrivalAirport("JFK");
        command.setArrivalDateTime(NOW_LDT.plusDays(1).plusHours(5));
        return command;
    }

    private FlightBooking mockService() {
        return new FlightBooking(null, null) {
            @Override public boolean isReadOnly() { return false; }
            @Override public void bookFlight(BookFlightRequest request, Instant now) {
                request.setDepartureZone("UTC");
                request.setArrivalZone("UTC");
                new BookFlightHandler(new AirportZoneResolver())
                        .handle(request)
                        .execute(new BookFlightContext(now))
                        .toList();
            }
        };
    }
}
