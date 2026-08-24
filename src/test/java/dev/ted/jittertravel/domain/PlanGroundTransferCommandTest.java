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
