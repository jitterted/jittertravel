package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookHotelHandler;
import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookHotelControllerValidationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);

    @Test
    void checkInInPastProducesFieldErrorOnCheckIn() {
        HotelBooking service = mockService();
        BookHotelRequest request = validRequest();
        request.setCheckIn(NOW.minusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("checkIn"))
                .as("Binding result must have a field error for checkIn")
                .isTrue();
    }

    @Test
    void checkOutSameDayAsCheckInProducesFieldErrorOnCheckOut() {
        HotelBooking service = mockService();
        BookHotelRequest request = validRequest();
        request.setCheckIn(NOW.plusWeeks(2).withHour(15).withMinute(0));
        request.setCheckOut(NOW.plusWeeks(2).withHour(23).withMinute(59));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("checkOut"))
                .as("Binding result must have a field error for checkOut")
                .isTrue();
    }

    @Test
    void validRequestProducesNoBindingErrors() {
        HotelBooking service = mockService();
        BookHotelRequest request = validRequest();
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasErrors())
                .as("Valid request must produce no binding errors")
                .isFalse();
    }

    private void invokeService(HotelBooking service, BookHotelRequest request, BindingResult bindingResult) {
        try {
            service.bookHotel(request, NOW);
        } catch (CheckInNotInFuture e) {
            bindingResult.rejectValue("checkIn", "future", e.getMessage());
        } catch (InvalidHotelDateRange e) {
            bindingResult.rejectValue("checkOut", "minOneDay", e.getMessage());
        }
    }

    private BookHotelRequest validRequest() {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        request.setHotelName("Grand Hotel");
        request.setStreet("123 Main St");
        request.setCity("Springfield");
        request.setRegion("IL");
        request.setCountry("US");
        request.setPostalCode("62701");
        request.setCheckIn(NOW.plusWeeks(2).withHour(15).withMinute(0));
        request.setCheckOut(NOW.plusWeeks(2).plusDays(1).withHour(11).withMinute(0));
        request.setBookingIntent(BookingIntent.TENTATIVE);
        return request;
    }

    private HotelBooking mockService() {
        return new HotelBooking(null) {
            @Override
            public void bookHotel(BookHotelRequest request, LocalDateTime now) {
                new BookHotelHandler().handle(request).execute(new BookHotelContext(now));
            }
        };
    }
}
