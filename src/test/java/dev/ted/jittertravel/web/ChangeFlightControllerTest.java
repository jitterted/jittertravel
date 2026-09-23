package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeFlight;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightNotFound;
import dev.ted.jittertravel.domain.InvalidAirportCode;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ChangeFlightControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-31T10:00:00Z"), ZoneId.of("UTC"));

    /**
     * Each refusal the service can raise lands where the form shows it — on the input that fixes
     * it, or, for those that name no single input, as a global error at the top — and the form is
     * re-rendered. The service is a stub programmed to throw, so the only code deciding where is
     * the controller's own; whether a real input produces each refusal is
     * {@code ChangeFlightCommandTest}'s, {@code ChangeFlightHandlerTest}'s and
     * {@code AirportCodeTest}'s to say.
     */
    @ParameterizedTest(name = "{0} → fields {1}, global {2}")
    @MethodSource("refusals")
    void eachRefusalLandsWhereTheFormShowsIt(RuntimeException refusal,
                                             List<String> fields,
                                             List<String> globalCodes) {
        ChangeFlightController controller =
                new ChangeFlightController(refusing(refusal), null, null, FIXED_CLOCK);
        ChangeFlightRequest request = new ChangeFlightRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "changeFlight");

        String view = controller.changeFlightSubmit(
                UUID.randomUUID().toString(), request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .as("a refusal re-renders the form, where the error can be seen")
                .isEqualTo("change-flight");
        assertThat(bindingResult.getFieldErrors())
                .extracting(FieldError::getField)
                .as("the inputs the refusal is reported on")
                .containsExactlyInAnyOrderElementsOf(fields);
        assertThat(bindingResult.getGlobalErrors())
                .extracting(ObjectError::getCode)
                .as("the form-wide errors the refusal is reported as")
                .containsExactlyInAnyOrderElementsOf(globalCodes);
    }

    static Stream<Arguments> refusals() {
        ZoneId london = ZoneId.of("Europe/London");
        return Stream.of(
                arguments(new FlightNotFound("Flight not found"),
                          List.of(), List.of("notFound")),
                arguments(new DepartureNotInFuture("Departure must be in the future"),
                          List.of("departureDateTime"), List.of()),
                arguments(new InvalidDateRange("Arrival must be after departure"),
                          List.of("arrivalDateTime"), List.of()),
                arguments(new OverlappingLegRefused(new ScheduledLeg(
                                  new ScheduledLegId.Flight(FlightId.random()),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), london),
                                  ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 11, 0), london))),
                          List.of("departureDateTime"), List.of()),
                arguments(new InvalidAirportCode("Airport code must be exactly 3 characters: BADCODE"),
                          List.of(), List.of("airportCode")),
                arguments(new ZoneResolutionException("ZZZ"),
                          List.of(), List.of("zoneUnresolved")));
    }

    @Test
    void readOnlyModeRedirectsRatherThanReportingAnError() {
        ChangeFlightController controller = new ChangeFlightController(
                refusing(new ReadOnlyModeException("read-only")), null, null, FIXED_CLOCK);
        ChangeFlightRequest request = new ChangeFlightRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "changeFlight");

        String view = controller.changeFlightSubmit(
                UUID.randomUUID().toString(), request, bindingResult, new ConcurrentModel());

        assertThat(view)
                .isEqualTo("redirect:/read-only");
        assertThat(bindingResult.hasErrors())
                .as("nothing on the form was wrong")
                .isFalse();
    }

    /** A writable service whose every change is refused with {@code refusal}. */
    private static ChangeFlight refusing(RuntimeException refusal) {
        return new ChangeFlight(null, null, null, null) {
            @Override public boolean isReadOnly() { return false; }

            @Override public void changeFlight(UUID commandId, ChangeFlightRequest request, Instant now) {
                throw refusal;
            }
        };
    }
}
