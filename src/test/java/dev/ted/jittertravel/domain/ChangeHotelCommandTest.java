package dev.ted.jittertravel.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

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

    /**
     * Refused as the command is built, not when it runs: the stay's own dates need no state to
     * judge, so a bad range is invalid data rather than a domain refusal, and must never reach the
     * command log ({@code docs/archived/ValidateBeforeTheWalPlan.md}). The cancel-by deadline and the
     * location checks below are the same.
     */
    @Test
    void checkOutOnSameDayAsCheckInIsRefusedAsTheCommandIsBuilt() {
        assertThatThrownBy(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_IN.withHour(23).withMinute(59)), BookingIntent.FINAL, null, null))
                .isInstanceOf(InvalidHotelDateRange.class);
    }

    @Test
    void existenceIsCheckedBeforeThePastCheckIn() {
        // A non-existent booking with a past check-in still fails as HotelBookingNotFound first:
        // both are recorded refusals, and "gone" is the one worth reporting.
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
    void cancelByAfterCheckInIsRefusedAsTheCommandIsBuilt() {
        assertThatThrownBy(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", ADDRESS,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, zt(CHECK_IN.plusMinutes(1))))
                .isInstanceOf(InvalidCancelByDate.class);
    }

    @Test
    void hotelNamePastedIntoTheCityIsRejectedAgainstTheCityField() {
        Address pasted = new Address("123 Main St", "Grand Hotel", "IL", "62701", "US", null);

        assertThat(locationProblems(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", pasted,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null)))
                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                .containsExactly(tuple(LocationRole.STAY, LocationField.CITY));
    }

    /**
     * A stay stored before countries were picked meets the rule only when it is edited — which is
     * the moment Ted can fix it, rather than a replay refusing the old event.
     */
    @Test
    void aCountryThatIsNotACodeIsRejectedAgainstTheCountryField() {
        Address legacy = new Address("123 Main St", "Springfield", "IL", "62701", "USA", null);

        assertThat(locationProblems(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", legacy,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null)))
                .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                .containsExactly(tuple(LocationField.COUNTRY, "Unknown country"));
    }

    /** The change path answers with every problem too, not only the booking path. */
    @Test
    void aStayWithNoNameAndNoCityReportsBothInOneAnswer() {
        Address cityless = new Address("123 Main St", "", "IL", "62701", "US", null);

        assertThat(locationProblems(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "", cityless,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null)))
                .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                .containsExactly(
                        tuple(LocationField.VENUE_NAME, "Name is required"),
                        tuple(LocationField.CITY, "City is required"));
    }

    /** Every location problem building the command refused, in the order the boundary will report them. */
    private static List<InvalidLocationEntry> locationProblems(ThrowingCallable building) {
        Throwable thrown = catchThrowable(building);

        assertThat(thrown)
                .as("a location problem is reported through the carrier, never a bare entry")
                .isInstanceOf(InvalidEnteredLocation.class);
        return ((InvalidEnteredLocation) thrown).problems();
    }

    /**
     * The reversal Ted accepted on 2026-10-22: a bad city is refused as the command is built, so
     * existence is never asked. Until then a booking that was gone reported that first. The edit
     * form is reached from a row that exists, so only a hand-edited URL can tell the difference.
     */
    @Test
    void aDataEntryMistakeIsRefusedBeforeExistenceCouldBeAsked() {
        Address pasted = new Address("123 Main St", "Grand Hotel", "IL", "62701", "US", null);

        assertThatThrownBy(() -> new ChangeHotelCommand(
                HotelBookingId.random(), "Grand Hotel", pasted,
                zt(CHECK_IN), zt(CHECK_OUT), BookingIntent.FINAL, null, null))
                .isInstanceOf(InvalidEnteredLocation.class);
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
