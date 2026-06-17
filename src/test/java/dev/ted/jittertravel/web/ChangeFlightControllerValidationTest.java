package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFlightControllerValidationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 16, 10, 0);

    @Test
    void departureInThePastIsInvalid() {
        ChangeFlight service = mockService(true);
        ChangeFlightRequest command = baseRequest();
        command.setDepartureDateTime(NOW.minusHours(1));
        command.setArrivalDateTime(NOW.plusDays(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "changeFlight");

        catchAndBind(service, command, bindingResult);

        assertThat(bindingResult.hasFieldErrors("departureDateTime")).isTrue();
    }

    @Test
    void arrivalBeforeDepartureIsInvalid() {
        ChangeFlight service = mockService(true);
        ChangeFlightRequest command = baseRequest();
        command.setDepartureDateTime(NOW.plusDays(1));
        command.setArrivalDateTime(NOW.plusDays(1).minusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "changeFlight");

        catchAndBind(service, command, bindingResult);

        assertThat(bindingResult.hasFieldErrors("arrivalDateTime")).isTrue();
    }

    @Test
    void invalidAirportCodeIsReportedAsGlobalError() {
        ChangeFlight service = mockService(true);
        ChangeFlightRequest command = baseRequest();
        command.setDepartureAirport("BADCODE");
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "changeFlight");

        catchAndBind(service, command, bindingResult);

        assertThat(bindingResult.hasGlobalErrors()).isTrue();
    }

    @Test
    void flightNotFoundIsThrownWhenFlightDoesNotExist() {
        ChangeFlight service = mockService(false);
        ChangeFlightRequest command = baseRequest();

        FlightNotFound thrown = null;
        try {
            service.changeFlight(UUID.randomUUID(), command, NOW);
        } catch (FlightNotFound e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
    }

    @Test
    void validRequestProducesNoErrors() {
        ChangeFlight service = mockService(true);
        ChangeFlightRequest command = baseRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "changeFlight");

        try {
            service.changeFlight(UUID.randomUUID(), command, NOW);
        } catch (Exception e) {
            bindingResult.reject("error", e.getMessage());
        }

        assertThat(bindingResult.hasErrors()).isFalse();
    }

    private static void catchAndBind(ChangeFlight service, ChangeFlightRequest command, BindingResult bindingResult) {
        try {
            service.changeFlight(UUID.randomUUID(), command, NOW);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        } catch (InvalidAirportCode e) {
            bindingResult.reject("airportCode", e.getMessage());
        } catch (FlightNotFound e) {
            bindingResult.reject("flightNotFound", e.getMessage());
        }
    }

    private ChangeFlightRequest baseRequest() {
        ChangeFlightRequest command = new ChangeFlightRequest();
        command.setFlightId(UUID.randomUUID().toString());
        command.setAirline("United");
        command.setFlightNumber("UA100");
        command.setDepartureAirport("SFO");
        command.setDepartureDateTime(NOW.plusDays(1));
        command.setArrivalAirport("JFK");
        command.setArrivalDateTime(NOW.plusDays(1).plusHours(5));
        return command;
    }

    private ChangeFlight mockService(boolean flightExists) {
        return new ChangeFlight(null, null) {
            @Override public boolean isReadOnly() { return false; }
            @Override public void changeFlight(UUID commandId, ChangeFlightRequest request, LocalDateTime now) {
                new ChangeFlightCommand().execute(request, flightExists, now);
            }
        };
    }
}
