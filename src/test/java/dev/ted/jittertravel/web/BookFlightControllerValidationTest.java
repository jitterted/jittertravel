package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.FlightBooking;
import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookFlightControllerValidationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 16, 10, 0);

    @Test
    void departureInThePastIsInvalid() {
        FlightBooking service = mockService();
        BookFlightRequest command = baseRequest();
        command.setDepartureDateTime(NOW.minusHours(1));
        command.setArrivalDateTime(NOW.plusDays(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command);
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
        command.setDepartureDateTime(NOW.plusDays(1));
        command.setArrivalDateTime(NOW.plusDays(1).minusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "bookFlight");

        try {
            service.bookFlight(command);
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
            service.bookFlight(command);
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
            service.bookFlight(command);
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
        command.setDepartureDateTime(NOW.plusDays(1));
        command.setArrivalAirport("JFK");
        command.setArrivalDateTime(NOW.plusDays(1).plusHours(5));
        return command;
    }

    private FlightBooking mockService() {
        return new FlightBooking(null, null) {
            @Override public boolean isReadOnly() { return false; }
            @Override public void bookFlight(BookFlightRequest request) {
                new BookFlightCommand().execute(request, NOW);
            }
        };
    }
}
