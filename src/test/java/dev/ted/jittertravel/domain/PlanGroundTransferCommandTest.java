package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanGroundTransferCommandTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalTime DEPARTS = LocalTime.of(11, 0);
    private static final LocalTime ARRIVES = LocalTime.of(11, 45);
    private static final Address AIRPORT = new Address("", "Denver", "CO", "", "US", "Denver");
    private static final Address HOTEL = new Address("10345 Park Meadows Dr", "Lone Tree", "CO",
                                                     "80124", "US", "Lone Tree");

    @Test
    void validCommandProducesGroundTransferPlannedEventWithBothEndpoints() {
        PlanGroundTransferCommand command = new PlanGroundTransferCommand(
                GroundTransferId.random(),
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                denverTime(TODAY, DEPARTS), denverTime(TODAY, ARRIVES),
                "A16 hotel shuttle");

        List<GroundTransferPlanned> events = command.execute(new PlanGroundTransferContext()).toList();

        assertThat(events)
                .hasSize(1);
        GroundTransferPlanned event = events.getFirst();
        assertThat(event.groundTransferId())
                .isEqualTo(command.groundTransferId());
        assertThat(event.originAirportCode())
                .isEqualTo("DEN");
        assertThat(event.originName())
                .isEmpty();
        assertThat(event.origin())
                .isEqualTo(AIRPORT);
        assertThat(event.destinationAirportCode())
                .isEmpty();
        assertThat(event.destinationName())
                .isEqualTo("Marriott Lone Tree");
        assertThat(event.destination())
                .isEqualTo(HOTEL);
        assertThat(event.departsAt())
                .isEqualTo(denverTime(TODAY, DEPARTS));
        assertThat(event.arrivesAt())
                .isEqualTo(denverTime(TODAY, ARRIVES));
        assertThat(event.mode())
                .isEqualTo("A16 hotel shuttle");
    }

    /**
     * A transfer recorded before the mode field existed replays with no mode — not a null, which
     * would put a null String in the domain for every reader downstream to check for.
     */
    @Test
    void aTransferPlannedWithoutAModeCarriesTheAbsentSentinelNotNull() {
        PlanGroundTransferCommand command = new PlanGroundTransferCommand(
                GroundTransferId.random(),
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                denverTime(TODAY, DEPARTS), denverTime(TODAY, ARRIVES), null);

        assertThat(command.execute(new PlanGroundTransferContext()).toList().getFirst().mode())
                .isEmpty();
    }

    /**
     * D6, and the case that regresses if someone copies {@code PlanPrivateEventCommand} wholesale:
     * a transfer is normally entered mid-trip, for a day already under way or already gone, to close
     * a gap the trip has already raised. Any date is accepted; the range rule is the only rule.
     */
    @Test
    void aTransferDatedYesterdayIsAccepted() {
        LocalDate yesterday = TODAY.minusDays(1);
        PlanGroundTransferCommand command = commandFor(
                denverTime(yesterday, DEPARTS), denverTime(yesterday, ARRIVES));

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .singleElement()
                .extracting(GroundTransferPlanned::departsAt)
                .isEqualTo(denverTime(yesterday, DEPARTS));
    }

    @Test
    void aTransferLaterTodayIsAccepted() {
        PlanGroundTransferCommand command = commandFor(
                denverTime(TODAY, DEPARTS), denverTime(TODAY, ARRIVES));

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    @Test
    void arrivalBeforeDepartureThrowsInvalidGroundTransferTimeRange() {
        PlanGroundTransferCommand command = commandFor(
                denverTime(TODAY, ARRIVES), denverTime(TODAY, DEPARTS));

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferTimeRange.class)
                .hasMessage("Arrival time must be after departure time");
    }

    @Test
    void arrivalEqualToDepartureThrowsInvalidGroundTransferTimeRange() {
        PlanGroundTransferCommand command = commandFor(
                denverTime(TODAY, DEPARTS), denverTime(TODAY, DEPARTS));

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferTimeRange.class);
    }

    @Test
    void endpointsAWeekApartAreRefusedWhateverDateIsTyped() {
        LocalDate checkOut = LocalDate.of(2026, 9, 13);
        LocalDate flight = LocalDate.of(2026, 9, 20);
        PlanGroundTransferCommand command = commandBetween(
                denverTime(flight, DEPARTS), denverTime(flight, ARRIVES),
                denverTime(checkOut, LocalTime.of(11, 0)), denverTime(flight, LocalTime.of(14, 0)));

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class)
                .hasMessage("Date must be within a day of both places");
    }

    @Test
    void aDateThatIsTheDayOfNeitherEndIsRefused() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        LocalDate wrongDay = LocalDate.of(2026, 9, 24);
        PlanGroundTransferCommand command = commandBetween(
                denverTime(wrongDay, DEPARTS), denverTime(wrongDay, ARRIVES),
                denverTime(day, LocalTime.of(9, 0)), denverTime(day, LocalTime.of(15, 0)));

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class);
    }

    @Test
    void anOvernightHopWithinTwentyFourHoursIsAcceptedOnEitherEndsDay() {
        LocalDate arrivalDay = LocalDate.of(2026, 9, 14);
        PlanGroundTransferCommand command = commandBetween(
                denverTime(arrivalDay, DEPARTS), denverTime(arrivalDay, ARRIVES),
                denverTime(arrivalDay, LocalTime.of(23, 30)),
                denverTime(arrivalDay.plusDays(1), LocalTime.of(15, 0)));

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    @Test
    void exactlyTwentyFourHoursApartIsAccepted() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        PlanGroundTransferCommand command = commandBetween(
                denverTime(day, DEPARTS), denverTime(day, ARRIVES),
                denverTime(day, LocalTime.of(12, 0)), denverTime(day.plusDays(1), LocalTime.of(12, 0)));

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    @Test
    void aMinuteOverTwentyFourHoursApartIsRefused() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        PlanGroundTransferCommand command = commandBetween(
                denverTime(day, DEPARTS), denverTime(day, ARRIVES),
                denverTime(day, LocalTime.of(12, 0)), denverTime(day.plusDays(1), LocalTime.of(12, 1)));

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class);
    }

    /** With only one moment known there is no pair to compare, but the date still has to be its day. */
    @Test
    void aSingleKnownMomentOnlyHoldsTheDateToItsOwnDay() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        PlanGroundTransferCommand sameDay = commandBetween(
                denverTime(day, DEPARTS), denverTime(day, ARRIVES),
                denverTime(day, LocalTime.of(9, 0)), null);
        PlanGroundTransferCommand otherDay = commandBetween(
                denverTime(day.plusDays(3), DEPARTS), denverTime(day.plusDays(3), ARRIVES),
                denverTime(day, LocalTime.of(9, 0)), null);

        assertThat(sameDay.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
        assertThatThrownBy(() -> otherDay.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class);
    }

    @Test
    void whenNoMomentIsKnownAnyDateIsAccepted() {
        PlanGroundTransferCommand command = commandBetween(
                denverTime(TODAY, DEPARTS), denverTime(TODAY, ARRIVES), null, null);

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    /**
     * The motivating case (Ted, 2026-10-06): a stay of Sep 13-18 and a gathering on Sep 15. The ride
     * happens on neither check-in nor check-out day, and is refused by a rule that sees only moments.
     */
    @Test
    void aMidStayRideFromAHotelToAGatheringIsAccepted() {
        LocalDate checkIn = LocalDate.of(2026, 9, 13);
        LocalDate gatheringDay = LocalDate.of(2026, 9, 15);
        var stay = new TransferEndpointWindow(
                denverTime(checkIn, LocalTime.of(15, 0)),
                denverTime(checkIn.plusDays(5), LocalTime.of(11, 0)));
        var gathering = new TransferEndpointWindow(
                denverTime(gatheringDay, LocalTime.of(19, 0)),
                denverTime(gatheringDay, LocalTime.of(22, 0)));
        PlanGroundTransferCommand command = commandBetweenWindows(
                denverTime(gatheringDay, LocalTime.of(18, 0)),
                denverTime(gatheringDay, LocalTime.of(18, 30)), stay, gathering);

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    /** The date is covered by the stay alone — neither its check-in day nor its check-out day. */
    @Test
    void aDateInsideOnlyTheStaysMiddleDaysIsAccepted() {
        LocalDate checkIn = LocalDate.of(2026, 9, 13);
        var stay = new TransferEndpointWindow(
                denverTime(checkIn, LocalTime.of(15, 0)),
                denverTime(checkIn.plusDays(5), LocalTime.of(11, 0)));
        var gathering = new TransferEndpointWindow(
                denverTime(checkIn.plusDays(3), LocalTime.of(19, 0)),
                denverTime(checkIn.plusDays(3), LocalTime.of(22, 0)));
        LocalDate midStay = checkIn.plusDays(2);
        PlanGroundTransferCommand command = commandBetweenWindows(
                denverTime(midStay, DEPARTS), denverTime(midStay, ARRIVES), stay, gathering);

        assertThat(command.execute(new PlanGroundTransferContext()).toList())
                .hasSize(1);
    }

    @Test
    void aDateOutsideEveryWindowIsRefusedEvenWhenTheWindowsOverlap() {
        LocalDate checkIn = LocalDate.of(2026, 9, 13);
        LocalDate gatheringDay = LocalDate.of(2026, 9, 15);
        var stay = new TransferEndpointWindow(
                denverTime(checkIn, LocalTime.of(15, 0)),
                denverTime(checkIn.plusDays(5), LocalTime.of(11, 0)));
        var gathering = new TransferEndpointWindow(
                denverTime(gatheringDay, LocalTime.of(19, 0)),
                denverTime(gatheringDay, LocalTime.of(22, 0)));
        LocalDate afterTheStay = LocalDate.of(2026, 9, 19);
        PlanGroundTransferCommand command = commandBetweenWindows(
                denverTime(afterTheStay, DEPARTS), denverTime(afterTheStay, ARRIVES),
                stay, gathering);

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class);
    }

    @Test
    void windowsMoreThanADayApartAreRefused() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        var stay = new TransferEndpointWindow(
                denverTime(day, LocalTime.of(15, 0)), denverTime(day.plusDays(1), LocalTime.of(11, 0)));
        var gathering = new TransferEndpointWindow(
                denverTime(day.plusDays(3), LocalTime.of(19, 0)),
                denverTime(day.plusDays(3), LocalTime.of(22, 0)));
        PlanGroundTransferCommand command = commandBetweenWindows(
                denverTime(day, DEPARTS), denverTime(day, ARRIVES), stay, gathering);

        assertThatThrownBy(() -> command.execute(new PlanGroundTransferContext()))
                .isInstanceOf(InvalidGroundTransferDate.class);
    }

    private static PlanGroundTransferCommand commandBetweenWindows(
            ZonedTimestamp departsAt, ZonedTimestamp arrivesAt,
            TransferEndpointWindow origin, TransferEndpointWindow destination) {
        return new PlanGroundTransferCommand(
                GroundTransferId.random(),
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                departsAt, arrivesAt, "", origin, destination);
    }

    private static PlanGroundTransferCommand commandBetween(
            ZonedTimestamp departsAt, ZonedTimestamp arrivesAt,
            ZonedTimestamp originMoment, ZonedTimestamp destinationMoment) {
        return commandBetweenWindows(departsAt, arrivesAt,
                originMoment == null ? null : TransferEndpointWindow.at(originMoment),
                destinationMoment == null ? null : TransferEndpointWindow.at(destinationMoment));
    }

    private static ZonedTimestamp denverTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), DENVER);
    }

    private static PlanGroundTransferCommand commandFor(ZonedTimestamp departsAt, ZonedTimestamp arrivesAt) {
        return new PlanGroundTransferCommand(
                GroundTransferId.random(),
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                departsAt, arrivesAt, "");
    }
}
