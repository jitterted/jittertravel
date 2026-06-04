package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookHotelCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);
    private static final LocalDateTime CHECK_IN = NOW.toLocalDate().plusWeeks(2).atTime(15, 0);
    private static final LocalDateTime CHECK_OUT = CHECK_IN.toLocalDate().plusDays(1).atTime(11, 0);
    private static final Address ADDRESS = new Address("123 Main St", "Springfield", "IL", "62701", "US", null);

    @Test
    void validTentativeCommandProducesHotelBookedEventWithAllFields() {
        BookHotelCommand command = validCommand();

        List<HotelBooked> events = command.execute(new BookHotelContext(NOW)).toList();

        assertThat(events)
                .hasSize(1);
        HotelBooked event = events.getFirst();
        assertThat(event.hotelBookingId())
                .isEqualTo(command.hotelBookingId());
        assertThat(event.hotelName())
                .isEqualTo("Grand Hotel");
        assertThat(event.address())
                .isEqualTo(ADDRESS);
        assertThat(event.checkIn())
                .isEqualTo(CHECK_IN);
        assertThat(event.checkOut())
                .isEqualTo(CHECK_OUT);
        assertThat(event.bookingIntent())
                .isEqualTo(BookingIntent.TENTATIVE);
    }

    @Test
    void validFinalCommandProducesHotelBookedEventWithFinalIntent() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                CHECK_IN, CHECK_OUT, BookingIntent.FINAL);

        assertThat(command.execute(new BookHotelContext(NOW)).toList().getFirst().bookingIntent())
                .isEqualTo(BookingIntent.FINAL);
    }

    @Test
    void checkInInPastThrowsCheckInNotInFuture() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                NOW.minusHours(1), CHECK_OUT, BookingIntent.TENTATIVE);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(NOW)))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkInExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                NOW, CHECK_OUT, BookingIntent.TENTATIVE);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(NOW)))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkOutOnSameDayAsCheckInThrowsInvalidHotelDateRange() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                CHECK_IN, CHECK_IN.withHour(23).withMinute(59), BookingIntent.TENTATIVE);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(NOW)))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void checkOutExactlyOneDayAfterCheckInIsValid() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 14, 15, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 6, 15, 11, 0);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                checkIn, checkOut, BookingIntent.TENTATIVE);

        assertThat(command.execute(new BookHotelContext(NOW)).toList())
                .hasSize(1);
    }

    private static BookHotelCommand validCommand() {
        return new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                CHECK_IN, CHECK_OUT, BookingIntent.TENTATIVE);
    }
}
