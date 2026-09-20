package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

class BookHotelCommandTest {

    private static final ZoneId ZONE = ZoneId.of("America/Chicago"); // Springfield, IL
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 10, 0);
    private static final LocalDateTime CHECK_IN = NOW.toLocalDate().plusWeeks(2).atTime(15, 0);
    private static final LocalDateTime CHECK_OUT = CHECK_IN.toLocalDate().plusDays(1).atTime(11, 0);
    private static final Address ADDRESS = new Address("123 Main St", "Springfield", "IL", "62701", "US", null);

    @Test
    void validTentativeCommandProducesHotelBookedEventWithAllFields() {
        BookHotelCommand command = validCommand();

        List<HotelBooked> events = command.execute(new BookHotelContext(at(NOW))).toList();

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
                .isEqualTo(zt(CHECK_IN));
        assertThat(event.checkOut())
                .isEqualTo(zt(CHECK_OUT));
        assertThat(event.bookingIntent())
                .isEqualTo(BookingIntent.TENTATIVE);
    }

    @Test
    void validFinalCommandProducesHotelBookedEventWithFinalIntent() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null);

        assertThat(command.execute(new BookHotelContext(at(NOW))).toList().getFirst().bookingIntent())
                .isEqualTo(BookingIntent.FINAL);
    }

    @Test
    void checkInInPastThrowsCheckInNotInFuture() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(NOW.minusHours(1)), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(at(NOW))))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkInExactlyNowIsNotAcceptedMustBeStrictlyAfter() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(NOW), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(at(NOW))))
                .isInstanceOf(CheckInNotInFuture.class);
    }

    @Test
    void checkOutOnSameDayAsCheckInThrowsInvalidHotelDateRange() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_IN.withHour(23).withMinute(59)), BookingIntent.TENTATIVE, null, null);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(at(NOW))))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void checkOutExactlyOneDayAfterCheckInIsValid() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 14, 15, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 6, 15, 11, 0);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(checkIn), zt(checkOut), BookingIntent.TENTATIVE, null, null);

        assertThat(command.execute(new BookHotelContext(at(NOW))).toList())
                .hasSize(1);
    }

    @Test
    void cancelByIsCarriedOntoTheEventWhenItPrecedesCheckIn() {
        LocalDateTime deadline = CHECK_IN.minusDays(3);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, zt(deadline));

        HotelBooked event = command.execute(new BookHotelContext(at(NOW))).toList().getFirst();

        assertThat(event.cancelBy())
                .isEqualTo(zt(deadline));
    }

    @Test
    void absentCancelByStaysNullOnTheEvent() {
        HotelBooked event = validCommand().execute(new BookHotelContext(at(NOW))).toList().getFirst();

        assertThat(event.cancelBy())
                .as("no deadline recorded must stay absent, not become a stand-in value")
                .isNull();
    }

    @Test
    void cancelByExactlyAtCheckInIsAccepted() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, zt(CHECK_IN));

        assertThat(command.execute(new BookHotelContext(at(NOW))).toList())
                .as("cancelling right up to the moment you check in is a real hotel policy")
                .hasSize(1);
    }

    @Test
    void cancelByAfterCheckInThrowsInvalidCancelByDate() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, zt(CHECK_IN.plusMinutes(1)));

        assertThatThrownBy(() -> command.execute(new BookHotelContext(at(NOW))))
                .isInstanceOf(InvalidCancelByDate.class);
    }

    @Test
    void hotelWithNoNameIsRejectedAgainstTheNameField() {
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThat(locationProblems(command))
                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                .containsExactly(tuple(LocationRole.STAY, LocationField.VENUE_NAME));
    }

    @Test
    void hotelNamePastedIntoTheCityIsRejectedAgainstTheCityField() {
        Address pasted = new Address("123 Main St", "Grand Hotel", "IL", "62701", "US", null);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", pasted,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThat(locationProblems(command))
                .extracting(InvalidLocationEntry::field)
                .containsExactly(LocationField.CITY);
    }

    /**
     * A stay with neither a name nor a city is one submit with two mistakes, and the command has to
     * hand the boundary both — reporting the name alone means fixing it, submitting again, and
     * meeting a fresh error indistinguishable from the first fix having done nothing.
     */
    @Test
    void aStayWithNoNameAndNoCityReportsBothInOneAnswer() {
        Address cityless = new Address("123 Main St", "", "IL", "62701", "US", null);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "", cityless,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThat(locationProblems(command))
                .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                .containsExactly(
                        tuple(LocationField.VENUE_NAME, "Name is required"),
                        tuple(LocationField.CITY, "City is required"));
    }

    @Test
    void locationIsCheckedBeforeTheDates() {
        // Both are wrong; the location is the one reported, because a wrong place is the mistake
        // that looks right on the page.
        Address cityless = new Address("123 Main St", "", "IL", "62701", "US", null);
        BookHotelCommand command = new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", cityless,
                zt(NOW.minusHours(1)), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);

        assertThatThrownBy(() -> command.execute(new BookHotelContext(at(NOW))))
                .isInstanceOf(InvalidEnteredLocation.class);
    }

    /** Every location problem the command refused, in the order the boundary will report them. */
    private static List<InvalidLocationEntry> locationProblems(BookHotelCommand command) {
        Throwable thrown = catchThrowable(() -> command.execute(new BookHotelContext(at(NOW))));

        assertThat(thrown)
                .as("a location problem is reported through the carrier, never a bare entry")
                .isInstanceOf(InvalidEnteredLocation.class);
        return ((InvalidEnteredLocation) thrown).problems();
    }

    private static BookHotelCommand validCommand() {
        return new BookHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.TENTATIVE, null, null);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private static Instant at(LocalDateTime local) {
        return local.atZone(ZONE).toInstant();
    }
}
