package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.BookHotelRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookHotelCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);

    @Test
    void validTentativeRequestProducesHotelBookedEventWithAllFields() {
        BookHotelRequest request = validRequest();
        request.setBookingIntent(BookingIntent.TENTATIVE);

        List<HotelBooked> events = new BookHotelCommand().execute(request, NOW).toList();

        assertThat(events).hasSize(1);
        HotelBooked event = events.getFirst();
        assertThat(event.hotelBookingId()).isEqualTo(HotelBookingId.of(UUID.fromString(request.getHotelBookingId())));
        assertThat(event.hotelName()).isEqualTo("Grand Hotel");
        assertThat(event.address()).isEqualTo(new Address("123 Main St", "Springfield", "IL", "62701", "US"));
        assertThat(event.checkIn()).isEqualTo(NOW.plusWeeks(2).withHour(15).withMinute(0));
        assertThat(event.checkOut()).isEqualTo(NOW.plusWeeks(2).plusDays(1).withHour(11).withMinute(0));
        assertThat(event.bookingIntent()).isEqualTo(BookingIntent.TENTATIVE);
    }

    @Test
    void validFinalRequestProducesHotelBookedEventWithFinalIntent() {
        BookHotelRequest request = validRequest();
        request.setBookingIntent(BookingIntent.FINAL);

        List<HotelBooked> events = new BookHotelCommand().execute(request, NOW).toList();

        assertThat(events.getFirst().bookingIntent()).isEqualTo(BookingIntent.FINAL);
    }

    @Test
    void checkInInPastThrowsCheckInNotInFuture() {
        BookHotelRequest request = validRequest();
        request.setCheckIn(NOW.minusHours(1));

        assertThatThrownBy(() -> new BookHotelCommand().execute(request, NOW))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkInExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookHotelRequest request = validRequest();
        request.setCheckIn(NOW);

        assertThatThrownBy(() -> new BookHotelCommand().execute(request, NOW))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkOutOnSameDayAsCheckInThrowsInvalidHotelDateRange() {
        BookHotelRequest request = validRequest();
        request.setCheckIn(NOW.plusWeeks(2).withHour(15).withMinute(0));
        request.setCheckOut(NOW.plusWeeks(2).withHour(23).withMinute(59));

        assertThatThrownBy(() -> new BookHotelCommand().execute(request, NOW))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void checkOutExactlyOneDayAfterCheckInIsValid() {
        BookHotelRequest request = validRequest();
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 14, 15, 0);
        request.setCheckIn(checkIn);
        request.setCheckOut(LocalDateTime.of(2026, 6, 15, 11, 0)); // next day, fewer than 24 hours later

        List<HotelBooked> events = new BookHotelCommand().execute(request, NOW).toList();

        assertThat(events).hasSize(1);
    }

    private BookHotelRequest validRequest() {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        request.setHotelName("Grand Hotel");
        request.setStreet("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setCountry("US");
        request.setPostalCode("62701");
        request.setCheckIn(NOW.plusWeeks(2).withHour(15).withMinute(0));
        request.setCheckOut(NOW.plusWeeks(2).plusDays(1).withHour(11).withMinute(0));
        request.setBookingIntent(BookingIntent.TENTATIVE);
        return request;
    }
}
