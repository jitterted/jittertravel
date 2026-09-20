package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.application.HotelHandler;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidCancelByDate;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookHotelControllerValidationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Chicago"); // Springfield, IL
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);
    private static final Instant NOW_INSTANT = NOW.atZone(ZONE).toInstant();
    private static final LocalDateTime CHECK_IN = NOW.plusWeeks(2).withHour(15).withMinute(0);
    private static final LocalDateTime CHECK_OUT = NOW.plusWeeks(2).plusDays(1).withHour(11).withMinute(0);

    @Test
    void checkInInPastProducesFieldErrorOnCheckIn() {
        HotelBooking service = mockService();
        BookHotelRequest request = requestCheckingIn(NOW.minusHours(1), CHECK_OUT, null);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("checkIn"))
                .as("Binding result must have a field error for checkIn")
                .isTrue();
    }

    @Test
    void checkOutSameDayAsCheckInProducesFieldErrorOnCheckOut() {
        HotelBooking service = mockService();
        BookHotelRequest request = requestCheckingIn(
                CHECK_IN, NOW.plusWeeks(2).withHour(23).withMinute(59), null);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("checkOut"))
                .as("Binding result must have a field error for checkOut")
                .isTrue();
    }

    @Test
    void validRequestProducesNoBindingErrors() {
        HotelBooking service = mockService();
        BookHotelRequest request = requestCheckingIn(CHECK_IN, CHECK_OUT, null);
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasErrors())
                .as("Valid request must produce no binding errors")
                .isFalse();
    }

    @Test
    void cancelByAfterCheckInProducesFieldErrorOnCancelBy() {
        HotelBooking service = mockService();
        BookHotelRequest request = requestCheckingIn(CHECK_IN, CHECK_OUT, CHECK_IN.plusHours(1));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasFieldErrors("cancelBy"))
                .as("Binding result must have a field error for cancelBy")
                .isTrue();
    }

    @Test
    void cancelByBeforeCheckInProducesNoBindingErrors() {
        HotelBooking service = mockService();
        BookHotelRequest request = requestCheckingIn(CHECK_IN, CHECK_OUT, CHECK_IN.minusDays(3));
        BindingResult bindingResult = new BeanPropertyBindingResult(request, "bookHotel");

        invokeService(service, request, bindingResult);

        assertThat(bindingResult.hasErrors())
                .as("A deadline that precedes check-in is the normal case")
                .isFalse();
    }

    private void invokeService(HotelBooking service, BookHotelRequest request, BindingResult bindingResult) {
        try {
            service.bookHotel(request, NOW_INSTANT);
        } catch (CheckInNotInFuture e) {
            bindingResult.rejectValue("checkIn", "future", e.getMessage());
        } catch (InvalidHotelDateRange e) {
            bindingResult.rejectValue("checkOut", "minOneDay", e.getMessage());
        } catch (InvalidCancelByDate e) {
            bindingResult.rejectValue("cancelBy", "notAfterCheckIn", e.getMessage());
        }
    }

    /**
     * A stay that is valid but for the dates the caller names. Springfield/US is ambiguous, so the
     * zone is pinned explicitly (the supported fallback) rather than derived.
     */
    private BookHotelRequest requestCheckingIn(LocalDateTime checkIn, LocalDateTime checkOut,
                                               LocalDateTime cancelBy) {
        return new BookHotelRequest(
                UUID.randomUUID().toString(), "Grand Hotel",
                "123 Main St", "Springfield", "IL", "US", "62701", null, null,
                "US_CENTRAL", checkIn, checkOut, cancelBy, BookingIntent.TENTATIVE);
    }

    private HotelBooking mockService() {
        return new HotelBooking(null, new LocationZoneResolver()) {
            @Override
            public void bookHotel(BookHotelRequest request, Instant now) {
                new HotelHandler(new LocationZoneResolver()).bookHotel(request)
                        .execute(new BookHotelContext(now));
            }
        };
    }
}
