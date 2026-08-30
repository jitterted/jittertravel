package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeHotelCommandTest {

    private static final ZoneId ZONE = ZoneId.of("America/Chicago"); // Springfield, IL
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);
    private static final LocalDateTime CHECK_IN = NOW.toLocalDate().plusWeeks(2).atTime(15, 0);
    private static final LocalDateTime CHECK_OUT = CHECK_IN.toLocalDate().plusDays(1).atTime(11, 0);
    private static final Address ADDRESS = new Address("123 Main St", "Springfield", "IL", "62701", "US", null);

    @Test
    void validChangeProducesHotelChangedEventWithAllFields() {
        ChangeHotelCommand command = validCommand();

        List<HotelChanged> events = command.execute(new ChangeHotelContext(true, at(NOW))).toList();

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
                .isEqualTo(zt(CHECK_IN));
        assertThat(event.checkOut())
                .isEqualTo(zt(CHECK_OUT));
        assertThat(event.bookingIntent())
                .isEqualTo(BookingIntent.FINAL);
    }

    @Test
    void changeRejectedWhenBookingDoesNotExist() {
        ChangeHotelCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(false, at(NOW))))
                .isInstanceOf(HotelBookingNotFound.class);
    }

    @Test
    void checkInInPastThrowsCheckInNotInFuture() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(NOW.minusHours(1)), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, at(NOW))))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkInExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(NOW), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, at(NOW))))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkOutOnSameDayAsCheckInThrowsInvalidHotelDateRange() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_IN.withHour(23).withMinute(59)), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, at(NOW))))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent booking with otherwise-invalid dates still fails as HotelBookingNotFound first.
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(NOW.minusHours(1)), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(false, at(NOW))))
                .isInstanceOf(HotelBookingNotFound.class);
    }

    @Test
    void cancelByIsCarriedOntoTheChangedSnapshot() {
        LocalDateTime deadline = CHECK_IN.minusDays(2);
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, zt(deadline));

        HotelChanged event = command.execute(new ChangeHotelContext(true, at(NOW))).toList().getFirst();

        assertThat(event.cancelBy())
                .isEqualTo(zt(deadline));
    }

    @Test
    void changeWithoutCancelByClearsTheDeadlineBecauseTheEventIsAFullSnapshot() {
        HotelChanged event = validCommand()
                .execute(new ChangeHotelContext(true, at(NOW)))
                .toList().getFirst();

        assertThat(event.cancelBy())
                .as("an omitted deadline is stored as absent — callers must resubmit it to keep it")
                .isNull();
    }

    @Test
    void cancelByAfterCheckInThrowsInvalidCancelByDate() {
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, zt(CHECK_IN.plusMinutes(1)));

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, at(NOW))))
                .isInstanceOf(InvalidCancelByDate.class);
    }

    @Test
    void hotelNamePastedIntoTheCityThrowsInvalidLocationEntry() {
        Address pasted = new Address("123 Main St", "Grand Hotel", "IL", "62701", "US", null);
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", pasted,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(true, at(NOW))))
                .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid -> {
                    assertThat(invalid.role())
                            .isEqualTo(LocationRole.STAY);
                    assertThat(invalid.field())
                            .isEqualTo(LocationField.CITY);
                });
    }

    @Test
    void existenceIsStillCheckedBeforeTheLocation() {
        // A booking that is gone reports that, not the bad city it was submitted with.
        Address pasted = new Address("123 Main St", "Grand Hotel", "IL", "62701", "US", null);
        ChangeHotelCommand command = new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", pasted,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThatThrownBy(() -> command.execute(new ChangeHotelContext(false, at(NOW))))
                .isInstanceOf(HotelBookingNotFound.class);
    }

    private static ChangeHotelCommand validCommand() {
        return new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static Instant at(LocalDateTime local) {
        return local.atZone(ZONE).toInstant();
    }
}
