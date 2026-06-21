package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeHotelCommandTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);
    private static final LocalDateTime CHECK_IN = NOW.toLocalDate().plusWeeks(2).atTime(15, 0);
    private static final LocalDateTime CHECK_OUT = CHECK_IN.toLocalDate().plusDays(1).atTime(11, 0);
    private static final Address ADDRESS = new Address("123 Main St", "Springfield", "IL", "62701", "US", null);

    @Test
    void validChangeProducesHotelChangedEventWithAllFields() {
        ChangeHotelCommand command = validCommand();

        List<HotelChanged> events = command.execute(new ChangeHotelContext(true, NOW)).toList();

        assertThat(events)
                .hasSize(1);
        HotelChanged event = events.getFirst();
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
                .isEqualTo(BookingIntent.FINAL);
    }

    @Test
    void changeRejectedWhenBookingDoesNotExist() {
        ChangeHotelCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(false, NOW)))
                .isInstanceOf(HotelBookingNotFound.class);
    }

    @Test
    void checkInInPastThrowsCheckInNotInFuture() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                NOW.minusHours(1), CHECK_OUT, BookingIntent.FINAL, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, NOW)))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkInExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                NOW, CHECK_OUT, BookingIntent.FINAL, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, NOW)))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkOutOnSameDayAsCheckInThrowsInvalidHotelDateRange() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                CHECK_IN, CHECK_IN.withHour(23).withMinute(59), BookingIntent.FINAL, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, NOW)))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent booking with otherwise-invalid dates still fails as HotelBookingNotFound first.
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                NOW.minusHours(1), CHECK_OUT, BookingIntent.FINAL, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(false, NOW)))
                .isInstanceOf(HotelBookingNotFound.class);
    }

    private static ChangeHotelCommand validCommand() {
        return new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                CHECK_IN, CHECK_OUT, BookingIntent.FINAL, null);
    }
}