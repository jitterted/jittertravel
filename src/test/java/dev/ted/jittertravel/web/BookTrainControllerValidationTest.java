package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookTrainHandler;
import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.BookTrainContext;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookTrainControllerValidationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 10, 0);
    private static final Instant NOW_INSTANT = NOW.atZone(ZONE).toInstant();

    @Test
    void departureInPastProducesFieldErrorOnDepartureDateTime() {
        TrainBooking service = mockService();
        BookTrainRequest request = validRequest();
        request.setDepartureDateTime(NOW.minusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookTrain");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("departureDateTime"))
                .as("Binding result must have a field error for departureDateTime")
                .isTrue();
    }

    @Test
    void arrivalBeforeDepartureProducesFieldErrorOnArrivalDateTime() {
        TrainBooking service = mockService();
        BookTrainRequest request = validRequest();
        request.setArrivalDateTime(request.getDepartureDateTime().minusMinutes(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookTrain");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("arrivalDateTime"))
                .as("Binding result must have a field error for arrivalDateTime")
                .isTrue();
    }

    @Test
    void validRequestProducesNoBindingErrors() {
        TrainBooking service = mockService();
        BookTrainRequest request = validRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookTrain");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasErrors())
                .as("Valid request must produce no binding errors")
                .isFalse();
    }

    private void invokeService(TrainBooking service, BookTrainRequest request, BindingResult bindingResult) {
        try {
            service.bookTrain(request, NOW_INSTANT);
        } catch (DepartureNotInFuture e) {
            bindingResult.rejectValue("departureDateTime", "future", e.getMessage());
        } catch (InvalidDateRange e) {
            bindingResult.rejectValue("arrivalDateTime", "afterDeparture", e.getMessage());
        }
    }

    private BookTrainRequest validRequest() {
        BookTrainRequest request = new BookTrainRequest();
        request.setTrainTripId(UUID.randomUUID().toString());
        request.setDepartureStationName("London Euston");
        request.setDepartureCityName("London");
        request.setDepartureCountry("UK");
        request.setDepartureDateTime(NOW.plusWeeks(1).withHour(9).withMinute(0));
        request.setArrivalStationName("Manchester Piccadilly");
        request.setArrivalCityName("Manchester");
        request.setArrivalCountry("UK");
        request.setArrivalDateTime(NOW.plusWeeks(1).withHour(13).withMinute(0));
        return request;
    }

    private TrainBooking mockService() {
        return new TrainBooking(null, new LocationZoneResolver()) {
            @Override
            public void bookTrain(BookTrainRequest request, Instant now) {
                new BookTrainHandler(new LocationZoneResolver()).handle(request)
                        .execute(new BookTrainContext(now));
            }
        };
    }
}
